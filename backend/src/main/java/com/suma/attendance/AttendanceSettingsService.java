package com.suma.attendance;

import com.suma.audit.AuditService;
import com.suma.common.ApiException;
import com.suma.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
public class AttendanceSettingsService {
    private final AttendanceSettingsRepository repo;
    private final BreakPolicyRepository policies;
    private final BreakCategoryRepository categories;
    private final AuditService audit;

    public AttendanceSettingsService(AttendanceSettingsRepository repo, BreakPolicyRepository policies,
                                     BreakCategoryRepository categories, AuditService audit) {
        this.repo = repo;
        this.policies = policies;
        this.categories = categories;
        this.audit = audit;
    }

    public AttendanceSettings global() {
        return repo.findGlobal().orElseGet(AttendanceSettings::new);
    }

    /** Project override if configured, otherwise the organisation default. */
    public AttendanceSettings effective(UUID projectId) {
        if (projectId != null) {
            var p = repo.findByProjectId(projectId);
            if (p.isPresent()) return p.get();
        }
        return global();
    }

    public ZoneId zone(UUID projectId) {
        return ZoneId.of(effective(projectId).getReportingTimezone());
    }

    /** Most specific active policy for the category: project+category, project, global+category, global. */
    public BreakPolicy policyFor(UUID projectId, String category) {
        List<BreakPolicy> list = policies.applicable(projectId);
        BreakPolicy best = null;
        int bestScore = -1;
        for (BreakPolicy p : list) {
            if (p.getBreakCategory() != null && !p.getBreakCategory().equals(category)) continue;
            int score = (p.getProjectId() != null ? 2 : 0) + (p.getBreakCategory() != null ? 1 : 0);
            if (score > bestScore) { best = p; bestScore = score; }
        }
        return best;
    }

    /** Effective max duration (minutes) for a break or null when no limit is configured. */
    public Integer maxBreakMinutes(UUID projectId, String category) {
        BreakPolicy p = policyFor(projectId, category);
        if (p != null && p.getMaxDurationMinutes() != null) return p.getMaxDurationMinutes();
        return effective(projectId).getMaxBreakMinutes();
    }

    public Integer reminderMinutes(UUID projectId, String category) {
        BreakPolicy p = policyFor(projectId, category);
        if (p != null && p.getReminderAfterMinutes() != null) return p.getReminderAfterMinutes();
        return effective(projectId).getBreakReminderIntervalMinutes();
    }

    public List<BreakCategory> categories() {
        return categories.findAllByOrderBySortOrderAsc();
    }

    @Transactional(readOnly = true)
    public List<AttendanceSettings> all() {
        return repo.findAll();
    }

    @Transactional
    public AttendanceSettings save(UUID projectId, AttendanceDtos.SettingsRequest r) {
        try {
            ZoneId.of(r.reportingTimezone());
        } catch (Exception e) {
            throw ApiException.badRequest("Unknown reporting timezone");
        }
        AttendanceSettings s = projectId == null ? repo.findGlobal().orElseGet(AttendanceSettings::new)
                : repo.findByProjectId(projectId).orElseGet(() -> {
            AttendanceSettings n = new AttendanceSettings();
            n.setProjectId(projectId);
            return n;
        });
        String before = describe(s);
        s.setDefaultWorkHours(r.defaultWorkHours());
        s.setIdleThresholdMinutes(r.idleThresholdMinutes());
        s.setHeartbeatTimeoutMinutes(r.heartbeatTimeoutMinutes());
        s.setMissingLogoutAfterHours(r.missingLogoutAfterHours());
        s.setMaxBreakMinutes(r.maxBreakMinutes());
        s.setMaxDailyBreakMinutes(r.maxDailyBreakMinutes());
        s.setMaxBreaksPerDay(r.maxBreaksPerDay());
        s.setBreakReminderIntervalMinutes(r.breakReminderIntervalMinutes());
        s.setReportingTimezone(r.reportingTimezone());
        s.setNotifyTeamLeaderOnIdle(r.notifyTeamLeaderOnIdle());
        s.setMonthlyReportEnabled(r.monthlyReportEnabled());
        s.setMonthlyReportHour(r.monthlyReportHour());
        s.setEmailMaxAttempts(r.emailMaxAttempts());
        s.setEmailRetryBackoffMinutes(r.emailRetryBackoffMinutes());
        s.setRetentionDays(r.retentionDays());
        s.setUpdatedBy(CurrentUser.id());
        s.setUpdatedAt(Instant.now());
        repo.save(s);
        audit.log("ATTENDANCE_SETTINGS_UPDATED", "AttendanceSettings", projectId == null ? "GLOBAL" : projectId, before + " -> " + describe(s));
        return s;
    }

    @Transactional
    public void deleteProjectOverride(UUID projectId) {
        repo.findByProjectId(projectId).ifPresent(s -> {
            repo.delete(s);
            audit.log("ATTENDANCE_SETTINGS_OVERRIDE_REMOVED", "AttendanceSettings", projectId, null);
        });
    }

    @Transactional(readOnly = true)
    public List<BreakPolicy> listPolicies() {
        return policies.findAll();
    }

    @Transactional
    public BreakPolicy savePolicy(UUID id, AttendanceDtos.BreakPolicyRequest r) {
        if (r.breakCategory() != null && !categories.existsById(r.breakCategory())) throw ApiException.badRequest("Unknown break category");
        BreakPolicy p = id == null ? new BreakPolicy() : policies.findById(id).orElseThrow(() -> ApiException.notFound("Break policy"));
        p.setName(r.name());
        p.setProjectId(r.projectId());
        p.setBreakCategory(r.breakCategory());
        p.setMaxDurationMinutes(r.maxDurationMinutes());
        p.setMaxPerDay(r.maxPerDay());
        p.setReminderAfterMinutes(r.reminderAfterMinutes());
        if (r.active() != null) p.setActive(r.active());
        policies.save(p);
        audit.log(id == null ? "BREAK_POLICY_CREATED" : "BREAK_POLICY_UPDATED", "BreakPolicy", p.getId(),
                p.getName() + " max=" + p.getMaxDurationMinutes() + " perDay=" + p.getMaxPerDay() + " reminder=" + p.getReminderAfterMinutes());
        return p;
    }

    @Transactional
    public void deletePolicy(UUID id) {
        BreakPolicy p = policies.findById(id).orElseThrow(() -> ApiException.notFound("Break policy"));
        policies.delete(p);
        audit.log("BREAK_POLICY_DELETED", "BreakPolicy", id, p.getName());
    }

    private static String describe(AttendanceSettings s) {
        return "idle=" + s.getIdleThresholdMinutes() + ",hb=" + s.getHeartbeatTimeoutMinutes() + ",maxBreak=" + s.getMaxBreakMinutes()
                + ",maxDaily=" + s.getMaxDailyBreakMinutes() + ",maxCount=" + s.getMaxBreaksPerDay() + ",reminder="
                + s.getBreakReminderIntervalMinutes() + ",tz=" + s.getReportingTimezone() + ",monthly=" + s.isMonthlyReportEnabled();
    }
}

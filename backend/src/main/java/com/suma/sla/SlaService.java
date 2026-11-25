package com.suma.sla;

import com.suma.audit.AuditService;
import com.suma.common.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Service
public class SlaService {
    private final SlaPolicyRepository policies;
    private final BusinessCalendarRepository calendars;
    private final BusinessHoursRepository hours;
    private final HolidayRepository holidays;
    private final EscalationRuleRepository escalations;
    private final AuditService audit;

    public SlaService(SlaPolicyRepository policies, BusinessCalendarRepository calendars, BusinessHoursRepository hours,
                      HolidayRepository holidays, EscalationRuleRepository escalations, AuditService audit) {
        this.policies = policies;
        this.calendars = calendars;
        this.hours = hours;
        this.holidays = holidays;
        this.escalations = escalations;
        this.audit = audit;
    }

    public Optional<SlaPolicy> policyFor(UUID projectId, String priority) {
        return policies.applicable(projectId, priority).stream().findFirst();
    }

    public BusinessTimeCalculator calculator(SlaPolicy p, UUID projectCalendarId) {
        if (!p.isBusinessHoursOnly()) return BusinessTimeCalculator.twentyFourSeven();
        UUID calId = p.getCalendarId() != null ? p.getCalendarId() : projectCalendarId;
        BusinessCalendar cal = calId != null ? calendars.findById(calId).orElse(null) : calendars.findFirstByDefaultCalendarTrue().orElse(null);
        if (cal == null) return BusinessTimeCalculator.twentyFourSeven();
        return calculator(cal);
    }

    public BusinessTimeCalculator calculator(BusinessCalendar cal) {
        Map<DayOfWeek, LocalTime[]> map = new EnumMap<>(DayOfWeek.class);
        for (BusinessHours h : hours.findByCalendarIdOrderByDayOfWeekAsc(cal.getId())) {
            map.put(DayOfWeek.of(h.getDayOfWeek()), new LocalTime[]{h.getStartTime(), h.getEndTime()});
        }
        Set<LocalDate> hol = new HashSet<>();
        holidays.findByCalendarIdOrderByHolidayDateAsc(cal.getId()).forEach(x -> hol.add(x.getHolidayDate()));
        return new BusinessTimeCalculator(ZoneId.of(cal.getTimezone()), map, hol);
    }

    public List<EscalationRule> escalationRules(UUID projectId, String priority) {
        return escalations.applicable(projectId, priority);
    }

    // ------------------------------------------------------------------ administration

    public record PolicyRequest(String name, UUID projectId, String priority, int responseMinutes, int resolutionMinutes,
                                UUID calendarId, Boolean businessHoursOnly, Boolean pauseOnPending, Boolean active) {}

    public record CalendarRequest(String name, String timezone, Boolean defaultCalendar, List<HoursDto> hours) {}

    public record HoursDto(int dayOfWeek, LocalTime startTime, LocalTime endTime) {}

    public record CalendarDto(UUID id, String name, String timezone, boolean defaultCalendar, List<HoursDto> hours, List<Holiday> holidays) {}

    @Transactional(readOnly = true)
    public List<SlaPolicy> listPolicies() {
        return policies.findAll(org.springframework.data.domain.Sort.by("priority"));
    }

    @Transactional
    public SlaPolicy savePolicy(UUID id, PolicyRequest r) {
        if (r.responseMinutes() <= 0 || r.resolutionMinutes() <= 0) throw ApiException.badRequest("SLA minutes must be positive");
        if (r.resolutionMinutes() < r.responseMinutes()) throw ApiException.badRequest("Resolution target must be >= response target");
        if (!Set.of("CRITICAL", "HIGH", "MEDIUM", "LOW").contains(r.priority())) throw ApiException.badRequest("Invalid priority");
        SlaPolicy p = id == null ? new SlaPolicy() : policies.findById(id).orElseThrow(() -> ApiException.notFound("SLA policy"));
        p.setName(r.name() == null || r.name().isBlank() ? r.priority() + " SLA" : r.name());
        p.setProjectId(r.projectId());
        p.setPriority(r.priority());
        p.setResponseMinutes(r.responseMinutes());
        p.setResolutionMinutes(r.resolutionMinutes());
        p.setCalendarId(r.calendarId());
        if (r.businessHoursOnly() != null) p.setBusinessHoursOnly(r.businessHoursOnly());
        if (r.pauseOnPending() != null) p.setPauseOnPending(r.pauseOnPending());
        if (r.active() != null) p.setActive(r.active());
        policies.saveAndFlush(p);
        audit.log(id == null ? "SLA_POLICY_CREATED" : "SLA_POLICY_UPDATED", "SlaPolicy", p.getId(),
                p.getPriority() + " response=" + p.getResponseMinutes() + "m resolution=" + p.getResolutionMinutes() + "m project=" + p.getProjectId());
        return p;
    }

    @Transactional
    public void deletePolicy(UUID id) {
        SlaPolicy p = policies.findById(id).orElseThrow(() -> ApiException.notFound("SLA policy"));
        p.setActive(false);
        audit.log("SLA_POLICY_DEACTIVATED", "SlaPolicy", id, p.getName());
    }

    @Transactional(readOnly = true)
    public List<CalendarDto> listCalendars() {
        return calendars.findAll().stream().map(this::toDto).toList();
    }

    @Transactional
    public CalendarDto saveCalendar(UUID id, CalendarRequest r) {
        try { ZoneId.of(r.timezone()); } catch (Exception e) { throw ApiException.badRequest("Unknown timezone"); }
        BusinessCalendar c = id == null ? new BusinessCalendar() : calendars.findById(id).orElseThrow(() -> ApiException.notFound("Calendar"));
        c.setName(r.name());
        c.setTimezone(r.timezone());
        if (Boolean.TRUE.equals(r.defaultCalendar())) {
            calendars.findAll().forEach(x -> x.setDefaultCalendar(false));
            c.setDefaultCalendar(true);
        }
        calendars.saveAndFlush(c);
        if (r.hours() != null) {
            hours.deleteByCalendarId(c.getId());
            hours.flush();
            Set<Integer> seen = new HashSet<>();
            for (HoursDto h : r.hours()) {
                if (h.dayOfWeek() < 1 || h.dayOfWeek() > 7 || !seen.add(h.dayOfWeek())) throw ApiException.badRequest("Invalid or duplicate day of week");
                if (!h.endTime().isAfter(h.startTime())) throw ApiException.badRequest("Business hours end must be after start");
                BusinessHours bh = new BusinessHours();
                bh.setCalendarId(c.getId());
                bh.setDayOfWeek((short) h.dayOfWeek());
                bh.setStartTime(h.startTime());
                bh.setEndTime(h.endTime());
                hours.save(bh);
            }
        }
        audit.log(id == null ? "CALENDAR_CREATED" : "CALENDAR_UPDATED", "BusinessCalendar", c.getId(), c.getName());
        return toDto(c);
    }

    @Transactional
    public Holiday addHoliday(UUID calendarId, LocalDate date, String name) {
        calendars.findById(calendarId).orElseThrow(() -> ApiException.notFound("Calendar"));
        Holiday h = new Holiday();
        h.setCalendarId(calendarId);
        h.setHolidayDate(date);
        h.setName(name);
        holidays.saveAndFlush(h);
        audit.log("HOLIDAY_ADDED", "BusinessCalendar", calendarId, date + " " + name);
        return h;
    }

    @Transactional
    public void deleteHoliday(UUID holidayId) {
        Holiday h = holidays.findById(holidayId).orElseThrow(() -> ApiException.notFound("Holiday"));
        holidays.delete(h);
        audit.log("HOLIDAY_REMOVED", "BusinessCalendar", h.getCalendarId(), h.getHolidayDate().toString());
    }

    @Transactional(readOnly = true)
    public List<EscalationRule> listEscalations() {
        return escalations.findAll();
    }

    @Transactional
    public EscalationRule saveEscalation(UUID id, EscalationRule r) {
        EscalationRule e = id == null ? new EscalationRule() : escalations.findById(id).orElseThrow(() -> ApiException.notFound("Escalation rule"));
        if (!Set.of("RESPONSE_BREACH", "RESOLUTION_BREACH", "RESOLUTION_PERCENT").contains(r.getTriggerType())) throw ApiException.badRequest("Invalid trigger type");
        if ("RESOLUTION_PERCENT".equals(r.getTriggerType()) && (r.getThresholdPercent() == null || r.getThresholdPercent() < 1 || r.getThresholdPercent() > 100))
            throw ApiException.badRequest("thresholdPercent 1-100 required");
        e.setProjectId(r.getProjectId());
        e.setPriority(r.getPriority());
        e.setTriggerType(r.getTriggerType());
        e.setThresholdPercent(r.getThresholdPercent());
        e.setEscalationLevel(r.getEscalationLevel());
        e.setNotifyRole(r.getNotifyRole());
        e.setNotifyUserId(r.getNotifyUserId());
        e.setActive(r.isActive());
        escalations.save(e);
        audit.log("ESCALATION_RULE_SAVED", "EscalationRule", e.getId(), e.getTriggerType() + " L" + e.getEscalationLevel());
        return e;
    }

    @Transactional
    public void deleteEscalation(UUID id) {
        escalations.deleteById(id);
        audit.log("ESCALATION_RULE_DELETED", "EscalationRule", id, null);
    }

    private CalendarDto toDto(BusinessCalendar c) {
        return new CalendarDto(c.getId(), c.getName(), c.getTimezone(), c.isDefaultCalendar(),
                hours.findByCalendarIdOrderByDayOfWeekAsc(c.getId()).stream().map(h -> new HoursDto(h.getDayOfWeek(), h.getStartTime(), h.getEndTime())).toList(),
                holidays.findByCalendarIdOrderByHolidayDateAsc(c.getId()));
    }
}

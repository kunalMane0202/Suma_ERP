package com.suma.attendance;

import com.suma.auth.AuthService;
import com.suma.common.ApiException;
import com.suma.common.PageResponse;
import com.suma.security.AccessScopeService;
import com.suma.security.AuthUser;
import com.suma.security.CurrentUser;
import com.suma.user.User;
import com.suma.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/attendance")
@Tag(name = "Attendance & Break Monitoring")
public class AttendanceController {
    private final AttendanceService attendance;
    private final AttendanceReportService reports;
    private final AttendanceExportService exporter;
    private final AttendanceSettingsService settings;
    private final MonthlyReportService monthly;
    private final AttendanceEventRepository events;
    private final BreakReminderRepository reminders;
    private final AccessScopeService scope;
    private final UserRepository users;
    private final AuthService auth;
    private final Clock clock;

    public AttendanceController(AttendanceService attendance, AttendanceReportService reports, AttendanceExportService exporter,
                                AttendanceSettingsService settings, MonthlyReportService monthly, AttendanceEventRepository events,
                                BreakReminderRepository reminders, AccessScopeService scope, UserRepository users, AuthService auth, Clock clock) {
        this.attendance = attendance;
        this.reports = reports;
        this.exporter = exporter;
        this.settings = settings;
        this.monthly = monthly;
        this.events = events;
        this.reminders = reminders;
        this.scope = scope;
        this.users = users;
        this.auth = auth;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ self service

    @PostMapping("/login")
    @PreAuthorize("hasAuthority('ATTENDANCE_SELF')")
    @Operation(summary = "Attendance check-in for the current login (idempotent). Attendance is recorded automatically at sign-in; this returns the active session.")
    public AttendanceDtos.TodayView login() {
        AuthUser me = CurrentUser.get();
        attendance.activeSession(me);
        return attendance.today(me);
    }

    @PostMapping("/logout")
    @PreAuthorize("hasAuthority('ATTENDANCE_SELF')")
    @Operation(summary = "End the current attendance session and sign out")
    public ResponseEntity<Void> logout() {
        AuthUser me = CurrentUser.get();
        auth.logout(null, me.sessionId(), "USER_LOGOUT");
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @PreAuthorize("hasAuthority('ATTENDANCE_SELF')")
    @Operation(summary = "Today's attendance for the signed-in employee: login time, status, breaks, idle events, exceptions")
    public AttendanceDtos.TodayView me() {
        return attendance.today(CurrentUser.get());
    }

    @PostMapping("/heartbeat")
    @PreAuthorize("hasAuthority('ATTENDANCE_SELF')")
    @Operation(summary = "Browser heartbeat with last in-app activity time (used for disconnect and idle detection)")
    public AttendanceDtos.IdleStatus heartbeat(@RequestBody(required = false) AttendanceDtos.HeartbeatRequest r) {
        return attendance.heartbeat(CurrentUser.get(), r);
    }

    @GetMapping("/sessions")
    @PreAuthorize("hasAuthority('ATTENDANCE_SELF')")
    public List<AttendanceDtos.SessionDto> sessions(@RequestParam(required = false) UUID userId,
                                                    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        checkRange(from, to, 93);
        return attendance.sessionsOf(CurrentUser.get(), userId, from, to);
    }

    @GetMapping("/categories")
    public List<AttendanceDtos.CategoryDto> categories() {
        return settings.categories().stream().map(c -> new AttendanceDtos.CategoryDto(c.getCode(), c.getLabel(), c.isActive())).toList();
    }

    // ------------------------------------------------------------------ breaks

    @PostMapping("/breaks/start")
    @PreAuthorize("hasAuthority('ATTENDANCE_SELF')")
    public AttendanceDtos.BreakDto startBreak(@Valid @RequestBody(required = false) AttendanceDtos.StartBreakRequest r) {
        return attendance.startBreak(CurrentUser.get(), r == null ? null : r.category(), r == null ? null : r.notes(), "EMPLOYEE");
    }

    @PostMapping("/breaks/end")
    @PreAuthorize("hasAuthority('ATTENDANCE_SELF')")
    public AttendanceDtos.BreakDto endBreak() {
        return attendance.endBreak(CurrentUser.get(), "EMPLOYEE");
    }

    @GetMapping("/breaks/current")
    @PreAuthorize("hasAuthority('ATTENDANCE_SELF')")
    public ResponseEntity<AttendanceDtos.BreakDto> currentBreak() {
        AttendanceDtos.BreakDto b = attendance.currentBreak(CurrentUser.get());
        return b == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(b);
    }

    @GetMapping("/breaks/history")
    @PreAuthorize("hasAuthority('ATTENDANCE_SELF')")
    public List<AttendanceDtos.BreakDto> breakHistory(@RequestParam(required = false) UUID userId,
                                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return attendance.breakHistory(CurrentUser.get(), userId, from, to);
    }

    // ------------------------------------------------------------------ idle

    @PostMapping("/idle/events")
    @PreAuthorize("hasAuthority('ATTENDANCE_SELF')")
    @Operation(summary = "Report that the configured in-app inactivity threshold was reached (idempotent per session)")
    public AttendanceDtos.IdleDto reportIdle(@Valid @RequestBody AttendanceDtos.IdleReportRequest r) {
        return attendance.reportIdle(CurrentUser.get(), r.idleStartedAt(), "BROWSER");
    }

    @GetMapping("/idle/events")
    @PreAuthorize("hasAuthority('ATTENDANCE_SELF')")
    public List<AttendanceDtos.IdleDto> idleEvents(@RequestParam(required = false) UUID userId,
                                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return attendance.idleHistory(CurrentUser.get(), userId, from, to);
    }

    @PostMapping("/idle/confirm")
    @PreAuthorize("hasAuthority('ATTENDANCE_SELF')")
    @Operation(summary = "Employee confirms status after an inactivity prompt: WORKING, ON_BREAK or AWAY")
    public AttendanceDtos.IdleDto confirmIdle(@Valid @RequestBody AttendanceDtos.IdleConfirmRequest r) {
        return attendance.confirmIdle(CurrentUser.get(), r.idleEventId(), r.response(), r.breakCategory());
    }

    @GetMapping("/idle/status")
    @PreAuthorize("hasAuthority('ATTENDANCE_SELF')")
    public AttendanceDtos.IdleStatus idleStatus() {
        return attendance.idleStatus(CurrentUser.get());
    }

    // ------------------------------------------------------------------ team / org views

    @GetMapping("/summary")
    @Operation(summary = "Attendance dashboard (scoped): logged in, active, on break, idle, missing logouts, compliance, live status")
    public AttendanceDtos.DashboardView summary(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                                @RequestParam(required = false) UUID projectId, @RequestParam(required = false) UUID teamId,
                                                @RequestParam(required = false) UUID userId) {
        return reports.dashboard(CurrentUser.get(), date, new AttendanceReportService.Filter(projectId, teamId, userId));
    }

    @GetMapping("/daily")
    public AttendanceReportService.DailyReport daily(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                                     @RequestParam(required = false) UUID projectId, @RequestParam(required = false) UUID teamId,
                                                     @RequestParam(required = false) UUID userId) {
        AuthUser me = CurrentUser.get();
        LocalDate d = date == null ? LocalDate.now(clock.withZone(settings.zone(projectId))) : date;
        return reports.daily(me, d, new AttendanceReportService.Filter(projectId, teamId, userId));
    }

    @GetMapping("/monthly")
    public AttendanceReportService.MonthlyReport monthlyReport(@RequestParam int year, @RequestParam int month,
                                                               @RequestParam(required = false) UUID projectId, @RequestParam(required = false) UUID teamId,
                                                               @RequestParam(required = false) UUID userId) {
        return reports.monthly(CurrentUser.get(), year, month, new AttendanceReportService.Filter(projectId, teamId, userId), null);
    }

    @GetMapping("/events")
    @Transactional(readOnly = true)
    @Operation(summary = "Attendance history: login, logout, break start/end, idle start/end, confirmations, reminders, corrections")
    public PageResponse<AttendanceDtos.EventDto> events(@RequestParam(required = false) UUID userId, @RequestParam(required = false) String type,
                                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                        @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        AuthUser me = CurrentUser.get();
        Set<UUID> visible = scope.attendanceVisibleUserIds(me);
        if (userId != null && visible != null && !visible.contains(userId)) throw ApiException.forbidden("You cannot view this employee's attendance");
        LocalDate t = to == null ? LocalDate.now(clock) : to;
        LocalDate f = from == null ? t.minusDays(7) : from;
        var p = events.search(visible != null, visible == null ? Set.of(new UUID(0, 0)) : visible, userId, blank(type),
                f.atStartOfDay(ZoneOffset.UTC).minusDays(1).toInstant(), t.plusDays(2).atStartOfDay(ZoneOffset.UTC).toInstant(),
                PageRequest.of(page, Math.min(size, 200)));
        Map<UUID, String> names = new HashMap<>();
        users.findByIdIn(p.getContent().stream().map(AttendanceEvent::getUserId).distinct().toList()).forEach(u -> names.put(u.getId(), u.getFullName()));
        return PageResponse.of(p, e -> new AttendanceDtos.EventDto(e.getId(), e.getUserId(), names.get(e.getUserId()), e.getSessionId(),
                e.getEventType(), e.getOccurredAt(), e.getSource(), e.getDetails(), e.getCreatedBy()));
    }

    @GetMapping("/reminders")
    @PreAuthorize("hasAuthority('ATTENDANCE_SELF')")
    @Transactional(readOnly = true)
    public List<BreakReminder> reminders(@RequestParam UUID breakId) {
        var b = attendance.breakHistory(CurrentUser.get(), null, LocalDate.now(clock).minusDays(60), LocalDate.now(clock).plusDays(1));
        AuthUser me = CurrentUser.get();
        if (b.stream().noneMatch(x -> x.id().equals(breakId)) && !me.isAdmin() && !me.has("ATTENDANCE_TEAM_VIEW"))
            throw ApiException.forbidden("Not your break");
        return reminders.findByBreakIdOrderBySentAtAsc(breakId);
    }

    // ------------------------------------------------------------------ reports & exports

    @GetMapping("/reports/daily")
    public AttendanceReportService.DailyReport reportDaily(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                                           @RequestParam(required = false) UUID projectId, @RequestParam(required = false) UUID teamId,
                                                           @RequestParam(required = false) UUID userId) {
        return reports.daily(CurrentUser.get(), date, new AttendanceReportService.Filter(projectId, teamId, userId));
    }

    @GetMapping("/reports/monthly")
    public AttendanceReportService.MonthlyReport reportMonthly(@RequestParam int year, @RequestParam int month,
                                                               @RequestParam(required = false) UUID projectId, @RequestParam(required = false) UUID teamId,
                                                               @RequestParam(required = false) UUID userId) {
        return reports.monthly(CurrentUser.get(), year, month, new AttendanceReportService.Filter(projectId, teamId, userId), null);
    }

    @GetMapping("/reports/export")
    @Operation(summary = "Export attendance report. type=daily|monthly, format=csv|xlsx|pdf")
    public ResponseEntity<byte[]> export(@RequestParam String type, @RequestParam String format,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                         @RequestParam(required = false) Integer year, @RequestParam(required = false) Integer month,
                                         @RequestParam(required = false) UUID projectId, @RequestParam(required = false) UUID teamId,
                                         @RequestParam(required = false) UUID userId) {
        AuthUser me = CurrentUser.get();
        if (!me.has("ATTENDANCE_REPORT_VIEW") && !me.isAdmin() && scope.projectsLedBy(me.id()).isEmpty()
                && (userId == null || !userId.equals(me.id())))
            throw ApiException.forbidden("You are not allowed to export attendance reports");
        var filter = new AttendanceReportService.Filter(projectId, teamId, userId);
        AttendanceExportService.Export ex = switch (type) {
            case "daily" -> {
                if (date == null) throw ApiException.badRequest("date is required for daily export");
                yield exporter.daily(reports.daily(me, date, filter), format);
            }
            case "monthly" -> {
                if (year == null || month == null) throw ApiException.badRequest("year and month are required for monthly export");
                yield exporter.monthly(reports.monthly(me, year, month, filter, null), format, null);
            }
            default -> throw ApiException.badRequest("type must be daily or monthly");
        };
        return file(ex);
    }

    // ------------------------------------------------------------------ monthly delivery

    @GetMapping("/monthly-reports")
    public List<AttendanceReportLog> monthlyReports() {
        return monthly.list(CurrentUser.get());
    }

    public record GenerateRequest(@jakarta.validation.constraints.NotNull UUID projectId, int year, int month) {}

    @PostMapping("/monthly-reports")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ATTENDANCE_MANAGE')")
    @Operation(summary = "Generate and send a monthly report for a completed month (fails with 409 if that period was already generated)")
    public AttendanceReportLog generate(@Valid @RequestBody GenerateRequest r) {
        return monthly.generateManual(CurrentUser.get(), r.projectId(), r.year(), r.month());
    }

    @GetMapping("/monthly-reports/{id}")
    public AttendanceReportLog monthlyReport(@PathVariable UUID id) {
        return monthly.get(CurrentUser.get(), id);
    }

    @PostMapping("/monthly-reports/{id}/send")
    @PreAuthorize("hasAuthority('ATTENDANCE_MANAGE')")
    @Operation(summary = "Re-send a monthly report. Already-delivered reports require force=true (audited).")
    public AttendanceReportLog send(@PathVariable UUID id, @RequestParam(defaultValue = "false") boolean force) {
        return monthly.resend(CurrentUser.get(), id, force);
    }

    @GetMapping("/monthly-reports/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable UUID id, @RequestParam(defaultValue = "xlsx") String format) {
        return file(monthly.download(CurrentUser.get(), id, format));
    }

    // ------------------------------------------------------------------ settings & policies

    @GetMapping("/settings")
    public List<AttendanceDtos.SettingsResponse> getSettings() {
        return settings.all().stream().map(AttendanceDtos.SettingsResponse::of).toList();
    }

    @GetMapping("/settings/effective")
    public AttendanceDtos.SettingsResponse effective(@RequestParam(required = false) UUID projectId) {
        return AttendanceDtos.SettingsResponse.of(settings.effective(projectId));
    }

    @PutMapping("/settings")
    @PreAuthorize("hasAuthority('ATTENDANCE_MANAGE')")
    public AttendanceDtos.SettingsResponse saveGlobal(@Valid @RequestBody AttendanceDtos.SettingsRequest r) {
        return AttendanceDtos.SettingsResponse.of(settings.save(null, r));
    }

    @PutMapping("/settings/projects/{projectId}")
    @PreAuthorize("hasAuthority('ATTENDANCE_MANAGE')")
    public AttendanceDtos.SettingsResponse saveProject(@PathVariable UUID projectId, @Valid @RequestBody AttendanceDtos.SettingsRequest r) {
        return AttendanceDtos.SettingsResponse.of(settings.save(projectId, r));
    }

    @DeleteMapping("/settings/projects/{projectId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('ATTENDANCE_MANAGE')")
    public void deleteProject(@PathVariable UUID projectId) {
        settings.deleteProjectOverride(projectId);
    }

    @GetMapping("/policies")
    public List<BreakPolicy> policies() {
        return settings.listPolicies();
    }

    @PostMapping("/policies")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ATTENDANCE_MANAGE')")
    public BreakPolicy createPolicy(@Valid @RequestBody AttendanceDtos.BreakPolicyRequest r) {
        return settings.savePolicy(null, r);
    }

    @PutMapping("/policies/{id}")
    @PreAuthorize("hasAuthority('ATTENDANCE_MANAGE')")
    public BreakPolicy updatePolicy(@PathVariable UUID id, @Valid @RequestBody AttendanceDtos.BreakPolicyRequest r) {
        return settings.savePolicy(id, r);
    }

    @DeleteMapping("/policies/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('ATTENDANCE_MANAGE')")
    public void deletePolicy(@PathVariable UUID id) {
        settings.deletePolicy(id);
    }

    // ------------------------------------------------------------------ corrections

    @GetMapping("/corrections")
    @PreAuthorize("hasAuthority('ATTENDANCE_MANAGE')")
    public List<AttendanceDtos.CorrectionDto> corrections() {
        return attendance.corrections();
    }

    @PostMapping("/corrections")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ATTENDANCE_MANAGE')")
    @Operation(summary = "Administrative correction. The original value is preserved in the correction record and audit log.")
    public AttendanceDtos.CorrectionDto correct(@Valid @RequestBody AttendanceDtos.CorrectionRequest r) {
        return attendance.correct(CurrentUser.get(), r);
    }

    // ------------------------------------------------------------------ helpers

    private static ResponseEntity<byte[]> file(AttendanceExportService.Export ex) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(ex.fileName()).build().toString())
                .contentType(MediaType.parseMediaType(ex.contentType()))
                .body(ex.content());
    }

    private static void checkRange(LocalDate from, LocalDate to, int maxDays) {
        if (from.isAfter(to)) throw ApiException.badRequest("from must be before to");
        if (Duration.between(from.atStartOfDay(), to.atStartOfDay()).toDays() > maxDays) throw ApiException.badRequest("Range too large");
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

}

package com.suma.attendance;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AttendanceCalculatorTest {
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final AttendanceCalculator.Limits NO_LIMITS = new AttendanceCalculator.Limits(null, null, null);

    private static AttendanceSession session(Instant in, Instant out, String status) {
        AttendanceSession s = new AttendanceSession();
        s.setId(UUID.randomUUID());
        s.setLoginAt(in);
        s.setLogoutAt(out);
        s.setStatus(status);
        return s;
    }

    private static BreakRecord brk(Instant start, long minutes, String category) {
        BreakRecord b = new BreakRecord();
        b.setId(UUID.randomUUID());
        b.setStartedAt(start);
        b.setEndedAt(start.plus(Duration.ofMinutes(minutes)));
        b.setDurationSeconds(minutes * 60);
        b.setStatus("COMPLETED");
        b.setBreakCategory(category);
        return b;
    }

    private static Instant at(String local) {
        return LocalDateTime.parse(local).atZone(IST).toInstant();
    }

    @Test
    void concurrentSessionsAreMergedNotDoubleCounted() {
        var s1 = session(at("2026-03-02T09:00"), at("2026-03-02T13:00"), "LOGGED_OUT");
        var s2 = session(at("2026-03-02T12:00"), at("2026-03-02T18:00"), "LOGGED_OUT"); // second browser overlapping
        var s3 = session(at("2026-03-02T19:00"), at("2026-03-02T19:30"), "LOGGED_OUT");
        var day = AttendanceCalculator.computeDay(LocalDate.parse("2026-03-02"), List.of(s1, s2, s3), List.of(), List.of(),
                NO_LIMITS, (p, c) -> null, Set.of(), IST, at("2026-03-03T10:00"));
        assertThat(day.sessionSeconds()).isEqualTo(Duration.ofHours(9).plusMinutes(30).toSeconds());
        assertThat(day.firstLoginAt()).isEqualTo(at("2026-03-02T09:00"));
        assertThat(day.lastLogoutAt()).isEqualTo(at("2026-03-02T19:30"));
        assertThat(day.exceptions()).isEmpty();
    }

    @Test
    void breakDurationsCountsAverageAndLimits() {
        var s = session(at("2026-03-02T09:00"), at("2026-03-02T18:00"), "LOGGED_OUT");
        var b1 = brk(at("2026-03-02T11:00"), 10, "GENERAL");
        var b2 = brk(at("2026-03-02T13:00"), 45, "MEAL");
        var b3 = brk(at("2026-03-02T16:00"), 15, "GENERAL");
        var limits = new AttendanceCalculator.Limits(60, 2, null);
        var day = AttendanceCalculator.computeDay(LocalDate.parse("2026-03-02"), List.of(s), List.of(b1, b2, b3), List.of(),
                limits, (p, c) -> "MEAL".equals(c) ? 40 : 30, Set.of(), IST, at("2026-03-03T10:00"));
        assertThat(day.breakCount()).isEqualTo(3);
        assertThat(day.breakSeconds()).isEqualTo(70 * 60);
        assertThat(day.averageBreakSeconds()).isEqualTo(70 * 60 / 3);
        assertThat(day.exceededBreakCount()).isEqualTo(1); // the 45 minute meal break over its 40 minute limit
        assertThat(day.exceptions()).contains("BREAK_LIMIT_EXCEEDED", "DAILY_BREAK_LIMIT_EXCEEDED", "MAX_BREAKS_EXCEEDED");
    }

    @Test
    void ongoingBreakAndSessionMeasuredUntilNow() {
        Instant now = at("2026-03-02T12:30");
        var s = session(at("2026-03-02T09:00"), null, "ACTIVE");
        BreakRecord b = new BreakRecord();
        b.setStartedAt(at("2026-03-02T12:00"));
        b.setStatus("ACTIVE");
        b.setBreakCategory("GENERAL");
        var day = AttendanceCalculator.computeDay(LocalDate.parse("2026-03-02"), List.of(s), List.of(b), List.of(),
                NO_LIMITS, (p, c) -> null, Set.of(), IST, now);
        assertThat(day.sessionSeconds()).isEqualTo(Duration.ofMinutes(210).toSeconds());
        assertThat(day.breakSeconds()).isEqualTo(30 * 60);
        assertThat(day.breaks().get(0).ongoing()).isTrue();
    }

    @Test
    void missingLogoutAndUnconfirmedIdleAreExceptionsButIdleIsNotABreak() {
        var s = session(at("2026-03-02T09:00"), at("2026-03-02T15:00"), "MISSING_LOGOUT");
        IdleEvent i = new IdleEvent();
        i.setIdleStartedAt(at("2026-03-02T11:00"));
        i.setEndedAt(at("2026-03-02T11:25"));
        i.setDurationSeconds(25 * 60L);
        i.setStatus("UNCONFIRMED");
        var day = AttendanceCalculator.computeDay(LocalDate.parse("2026-03-02"), List.of(s), List.of(), List.of(i),
                NO_LIMITS, (p, c) -> null, Set.of(), IST, at("2026-03-03T10:00"));
        assertThat(day.missingLogout()).isTrue();
        assertThat(day.idleSeconds()).isEqualTo(25 * 60);
        assertThat(day.breakSeconds()).isZero();
        assertThat(day.exceptions()).contains("MISSING_LOGOUT", "UNCONFIRMED_IDLE");
    }

    @Test
    void absenceFlaggedOnWorkdaysOnlyAndNotInTheFuture() {
        Instant now = at("2026-03-04T10:00");
        var monday = AttendanceCalculator.computeDay(LocalDate.parse("2026-03-02"), List.of(), List.of(), List.of(), NO_LIMITS, (p, c) -> null, Set.of(), IST, now);
        assertThat(monday.exceptions()).containsExactly("NO_LOGIN_RECORD");
        var sundayOff = AttendanceCalculator.computeDay(LocalDate.parse("2026-03-01"), List.of(), List.of(), List.of(), NO_LIMITS, (p, c) -> null, Set.of((short) 7), IST, now);
        assertThat(sundayOff.exceptions()).isEmpty();
        assertThat(sundayOff.weeklyOff()).isTrue();
        var future = AttendanceCalculator.computeDay(LocalDate.parse("2026-03-10"), List.of(), List.of(), List.of(), NO_LIMITS, (p, c) -> null, Set.of(), IST, now);
        assertThat(future.exceptions()).isEmpty();
    }

    @Test
    void shortWorkingDayFlaggedWhenWorkHoursConfigured() {
        var s = session(at("2026-03-02T09:00"), at("2026-03-02T13:00"), "LOGGED_OUT");
        var day = AttendanceCalculator.computeDay(LocalDate.parse("2026-03-02"), List.of(s), List.of(), List.of(),
                new AttendanceCalculator.Limits(null, null, new BigDecimal("8")), (p, c) -> null, Set.of(), IST, at("2026-03-03T10:00"));
        assertThat(day.exceptions()).contains("SHORT_WORKING_SESSION");
    }

    @Test
    void workDateUsesReportingTimezoneAcrossMidnightAndMonthEnd() {
        // 2026-01-31 20:00 UTC is already 1 February in India (UTC+05:30)
        Instant i = Instant.parse("2026-01-31T20:00:00Z");
        assertThat(AttendanceCalculator.workDate(i, IST)).isEqualTo(LocalDate.parse("2026-02-01"));
        assertThat(AttendanceCalculator.workDate(i, ZoneOffset.UTC)).isEqualTo(LocalDate.parse("2026-01-31"));
    }

    @Test
    void daylightSavingDayMeasuresRealElapsedTime() {
        ZoneId ny = ZoneId.of("America/New_York"); // 2026-03-08: clocks jump 02:00 -> 03:00
        Instant in = LocalDateTime.parse("2026-03-08T00:30").atZone(ny).toInstant();
        Instant out = LocalDateTime.parse("2026-03-08T04:30").atZone(ny).toInstant();
        var day = AttendanceCalculator.computeDay(LocalDate.parse("2026-03-08"), List.of(session(in, out, "LOGGED_OUT")), List.of(), List.of(),
                NO_LIMITS, (p, c) -> null, Set.of(), ny, Instant.parse("2026-03-09T12:00:00Z"));
        assertThat(day.sessionSeconds()).isEqualTo(Duration.ofHours(3).toSeconds()); // 4 wall-clock hours, 3 real hours
    }

    @Test
    void unionOfIntervalsHandlesContainmentAndGaps() {
        assertThat(AttendanceCalculator.unionSeconds(List.of(new long[]{0, 100}, new long[]{10, 20}, new long[]{150, 200}))).isEqualTo(150);
        assertThat(AttendanceCalculator.unionSeconds(List.of())).isZero();
    }
}

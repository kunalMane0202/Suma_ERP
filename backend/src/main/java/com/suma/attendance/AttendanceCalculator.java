package com.suma.attendance;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.function.BiFunction;

/**
 * Pure attendance arithmetic. All inputs are real records; nothing is estimated except the duration of records still
 * open at {@code now}. Concurrent sessions are merged (union of intervals) so they are never double counted.
 */
public final class AttendanceCalculator {
    private AttendanceCalculator() {}

    public record Limits(Integer maxDailyBreakMinutes, Integer maxBreaksPerDay, java.math.BigDecimal defaultWorkHours) {}

    public record SessionLine(UUID id, Instant loginAt, Instant logoutAt, long durationSeconds, String status,
                              String logoutSource, String loginIp, boolean ongoing) {}

    public record BreakLine(UUID id, String category, Instant startedAt, Instant endedAt, long durationSeconds,
                            boolean exceeded, String status, boolean ongoing) {}

    public record IdleLine(UUID id, Instant startedAt, Instant endedAt, long durationSeconds, String status, String response) {}

    public record DayResult(LocalDate date, boolean present, Instant firstLoginAt, Instant lastLogoutAt,
                            List<SessionLine> sessions, long sessionSeconds, List<BreakLine> breaks, int breakCount,
                            long breakSeconds, long averageBreakSeconds, int exceededBreakCount, List<IdleLine> idles,
                            int idleCount, long idleSeconds, int unconfirmedIdleCount, boolean missingLogout,
                            boolean weeklyOff, List<String> exceptions) {}

    /**
     * @param maxBreakFor returns the max minutes for (projectId, category), or null when not configured
     */
    public static DayResult computeDay(LocalDate date, List<AttendanceSession> sessions, List<BreakRecord> breaks,
                                       List<IdleEvent> idles, Limits limits, BiFunction<UUID, String, Integer> maxBreakFor,
                                       Set<Short> weeklyOffs, ZoneId zone, Instant now) {
        List<SessionLine> sLines = new ArrayList<>();
        List<long[]> intervals = new ArrayList<>();
        Instant first = null, last = null;
        boolean missingLogout = false, anyOngoing = false;
        for (AttendanceSession s : sessions) {
            boolean ongoing = s.isActive();
            Instant end = s.getLogoutAt() != null ? s.getLogoutAt() : (ongoing ? now : s.getLoginAt());
            if (end.isBefore(s.getLoginAt())) end = s.getLoginAt();
            long dur = Duration.between(s.getLoginAt(), end).toSeconds();
            sLines.add(new SessionLine(s.getId(), s.getLoginAt(), s.getLogoutAt(), dur, s.getStatus(), s.getLogoutSource(), s.getLoginIp(), ongoing));
            intervals.add(new long[]{s.getLoginAt().getEpochSecond(), end.getEpochSecond()});
            if (first == null || s.getLoginAt().isBefore(first)) first = s.getLoginAt();
            if (s.getLogoutAt() != null && (last == null || s.getLogoutAt().isAfter(last))) last = s.getLogoutAt();
            if ("MISSING_LOGOUT".equals(s.getStatus())) missingLogout = true;
            if (ongoing) anyOngoing = true;
        }
        long sessionSeconds = unionSeconds(intervals);

        List<BreakLine> bLines = new ArrayList<>();
        long breakSeconds = 0;
        int exceeded = 0;
        for (BreakRecord b : breaks) {
            boolean ongoing = b.isActive();
            long dur = b.getDurationSeconds() != null ? b.getDurationSeconds()
                    : Duration.between(b.getStartedAt(), ongoing ? now : b.getStartedAt()).toSeconds();
            Integer max = maxBreakFor.apply(b.getProjectId(), b.getBreakCategory());
            boolean ex = b.isExceeded() || (max != null && dur > max * 60L);
            if (ex) exceeded++;
            breakSeconds += dur;
            bLines.add(new BreakLine(b.getId(), b.getBreakCategory(), b.getStartedAt(), b.getEndedAt(), dur, ex, b.getStatus(), ongoing));
        }

        List<IdleLine> iLines = new ArrayList<>();
        long idleSeconds = 0;
        int unconfirmed = 0;
        for (IdleEvent i : idles) {
            long dur = i.getDurationSeconds() != null ? i.getDurationSeconds()
                    : Duration.between(i.getIdleStartedAt(), i.getEndedAt() != null ? i.getEndedAt() : now).toSeconds();
            idleSeconds += Math.max(0, dur);
            if ("UNCONFIRMED".equals(i.getStatus()) || ("AUTO_RESOLVED".equals(i.getStatus()) && i.getResponse() == null)) unconfirmed++;
            iLines.add(new IdleLine(i.getId(), i.getIdleStartedAt(), i.getEndedAt(), Math.max(0, dur), i.getStatus(), i.getResponse()));
        }

        boolean weeklyOff = weeklyOffs != null && weeklyOffs.contains((short) date.getDayOfWeek().getValue());
        boolean present = !sessions.isEmpty();
        List<String> exceptions = new ArrayList<>();
        LocalDate today = LocalDate.ofInstant(now, zone);
        if (!present && !weeklyOff && !date.isAfter(today)) exceptions.add("NO_LOGIN_RECORD");
        if (missingLogout) exceptions.add("MISSING_LOGOUT");
        if (exceeded > 0) exceptions.add("BREAK_LIMIT_EXCEEDED");
        if (limits.maxDailyBreakMinutes() != null && breakSeconds > limits.maxDailyBreakMinutes() * 60L) exceptions.add("DAILY_BREAK_LIMIT_EXCEEDED");
        if (limits.maxBreaksPerDay() != null && breaks.size() > limits.maxBreaksPerDay()) exceptions.add("MAX_BREAKS_EXCEEDED");
        if (unconfirmed > 0) exceptions.add("UNCONFIRMED_IDLE");
        if (limits.defaultWorkHours() != null && present && !anyOngoing && date.isBefore(today)
                && sessionSeconds < limits.defaultWorkHours().multiply(java.math.BigDecimal.valueOf(3600)).longValue()) {
            exceptions.add("SHORT_WORKING_SESSION");
        }
        if (present && weeklyOff) exceptions.add("WORKED_ON_WEEKLY_OFF");

        return new DayResult(date, present, first, last, sLines, sessionSeconds, bLines, breaks.size(), breakSeconds,
                breaks.isEmpty() ? 0 : breakSeconds / breaks.size(), exceeded, iLines, idles.size(), idleSeconds, unconfirmed,
                missingLogout, weeklyOff, exceptions);
    }

    /** Total length of the union of [start, end) intervals in epoch seconds. */
    static long unionSeconds(List<long[]> intervals) {
        if (intervals.isEmpty()) return 0;
        List<long[]> sorted = new ArrayList<>(intervals);
        sorted.sort(Comparator.comparingLong(a -> a[0]));
        long total = 0, curStart = sorted.get(0)[0], curEnd = sorted.get(0)[1];
        for (int i = 1; i < sorted.size(); i++) {
            long[] iv = sorted.get(i);
            if (iv[0] <= curEnd) {
                curEnd = Math.max(curEnd, iv[1]);
            } else {
                total += curEnd - curStart;
                curStart = iv[0];
                curEnd = iv[1];
            }
        }
        return total + (curEnd - curStart);
    }

    /** Calendar date of an instant in the reporting timezone (attendance day boundary). */
    public static LocalDate workDate(Instant at, ZoneId zone) {
        return LocalDate.ofInstant(at, zone);
    }
}

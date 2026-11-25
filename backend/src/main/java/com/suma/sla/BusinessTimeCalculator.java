package com.suma.sla;

import java.time.*;
import java.util.*;

/**
 * Business-time arithmetic over a calendar (timezone, weekly business hours, holidays). With no business hours defined
 * the calendar is treated as 24x7. All computation is done on the backend in the calendar's timezone, so daylight
 * saving transitions are handled by java.time.
 */
public final class BusinessTimeCalculator {
    private final ZoneId zone;
    private final Map<DayOfWeek, LocalTime[]> hours;
    private final Set<LocalDate> holidays;
    private final boolean always;

    public BusinessTimeCalculator(ZoneId zone, Map<DayOfWeek, LocalTime[]> hours, Set<LocalDate> holidays) {
        this.zone = zone;
        this.hours = hours == null ? Map.of() : hours;
        this.holidays = holidays == null ? Set.of() : holidays;
        this.always = this.hours.isEmpty();
    }

    public static BusinessTimeCalculator twentyFourSeven() {
        return new BusinessTimeCalculator(ZoneOffset.UTC, Map.of(), Set.of());
    }

    /** Adds business seconds to a start instant. */
    public Instant addBusinessSeconds(Instant start, long seconds) {
        if (seconds <= 0) return start;
        if (always) return start.plusSeconds(seconds);
        long remaining = seconds;
        ZonedDateTime cursor = start.atZone(zone);
        for (int guard = 0; guard < 3660; guard++) { // at most ~10 years of days
            LocalDate d = cursor.toLocalDate();
            LocalTime[] window = window(d);
            if (window != null) {
                ZonedDateTime open = d.atTime(window[0]).atZone(zone);
                ZonedDateTime close = d.atTime(window[1]).atZone(zone);
                ZonedDateTime from = cursor.isAfter(open) ? cursor : open;
                if (from.isBefore(close)) {
                    long available = Duration.between(from, close).toSeconds();
                    if (available >= remaining) return from.plusSeconds(remaining).toInstant();
                    remaining -= available;
                }
            }
            cursor = d.plusDays(1).atStartOfDay(zone);
        }
        throw new IllegalStateException("Business calendar has no working time");
    }

    /** Business seconds elapsed between two instants (0 if end <= start). */
    public long businessSecondsBetween(Instant start, Instant end) {
        if (!end.isAfter(start)) return 0;
        if (always) return Duration.between(start, end).toSeconds();
        long total = 0;
        ZonedDateTime s = start.atZone(zone), e = end.atZone(zone);
        for (LocalDate d = s.toLocalDate(); !d.isAfter(e.toLocalDate()); d = d.plusDays(1)) {
            LocalTime[] window = window(d);
            if (window == null) continue;
            ZonedDateTime open = d.atTime(window[0]).atZone(zone);
            ZonedDateTime close = d.atTime(window[1]).atZone(zone);
            ZonedDateTime from = s.isAfter(open) ? s : open;
            ZonedDateTime to = e.isBefore(close) ? e : close;
            if (to.isAfter(from)) total += Duration.between(from, to).toSeconds();
        }
        return total;
    }

    private LocalTime[] window(LocalDate d) {
        if (holidays.contains(d)) return null;
        return hours.get(d.getDayOfWeek());
    }
}

package com.suma.sla;

import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessTimeCalculatorTest {
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private static BusinessTimeCalculator weekdays9to18(ZoneId z, Set<LocalDate> holidays) {
        Map<DayOfWeek, LocalTime[]> h = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek d : new DayOfWeek[]{DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY})
            h.put(d, new LocalTime[]{LocalTime.of(9, 0), LocalTime.of(18, 0)});
        return new BusinessTimeCalculator(z, h, holidays);
    }

    private static Instant at(String s, ZoneId z) { return LocalDateTime.parse(s).atZone(z).toInstant(); }

    @Test
    void twentyFourSevenIsPlainAddition() {
        Instant s = Instant.parse("2026-01-01T00:00:00Z");
        assertThat(BusinessTimeCalculator.twentyFourSeven().addBusinessSeconds(s, 3600)).isEqualTo(s.plusSeconds(3600));
    }

    @Test
    void rollsOverEveningsAndWeekends() {
        var c = weekdays9to18(IST, Set.of());
        // Friday 17:00 + 4 business hours = Monday 12:00
        assertThat(c.addBusinessSeconds(at("2026-03-06T17:00", IST), 4 * 3600)).isEqualTo(at("2026-03-09T12:00", IST));
        // created Saturday: clock starts Monday 09:00
        assertThat(c.addBusinessSeconds(at("2026-03-07T10:00", IST), 30 * 60)).isEqualTo(at("2026-03-09T09:30", IST));
    }

    @Test
    void skipsHolidays() {
        var c = weekdays9to18(IST, Set.of(LocalDate.parse("2026-03-09")));
        assertThat(c.addBusinessSeconds(at("2026-03-06T17:00", IST), 4 * 3600)).isEqualTo(at("2026-03-10T12:00", IST));
    }

    @Test
    void businessSecondsBetweenExcludesClosedHours() {
        var c = weekdays9to18(IST, Set.of());
        assertThat(c.businessSecondsBetween(at("2026-03-06T17:00", IST), at("2026-03-09T10:00", IST))).isEqualTo(2 * 3600);
        assertThat(c.businessSecondsBetween(at("2026-03-09T10:00", IST), at("2026-03-09T09:00", IST))).isZero();
    }

    @Test
    void handlesDaylightSavingTransitions() {
        ZoneId ny = ZoneId.of("America/New_York");
        var c = weekdays9to18(ny, Set.of());
        // Friday 2026-03-06 17:00 EST + 2h -> Monday 2026-03-09 10:00 EDT (weekend DST change does not shift business time)
        assertThat(c.addBusinessSeconds(at("2026-03-06T17:00", ny), 2 * 3600)).isEqualTo(at("2026-03-09T10:00", ny));
    }
}

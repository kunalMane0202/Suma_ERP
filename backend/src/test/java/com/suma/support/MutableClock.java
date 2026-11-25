package com.suma.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Test clock that can be moved forward to exercise timeouts, SLA breaches and scheduled jobs deterministically. */
public class MutableClock extends Clock {
    private volatile Instant now;

    public MutableClock(Instant start) { this.now = start.truncatedTo(java.time.temporal.ChronoUnit.MILLIS); }

    public void set(Instant i) { now = i.truncatedTo(java.time.temporal.ChronoUnit.MILLIS); }
    public void advance(Duration d) { now = now.plus(d); }
    public void reset() { now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS); }

    @Override public ZoneId getZone() { return ZoneOffset.UTC; }
    @Override public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
    @Override public Instant instant() { return now; }
}

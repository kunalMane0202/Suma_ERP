package com.suma.auth;

import com.suma.config.SumaProperties;
import com.suma.support.MutableClock;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LoginRateLimiterTest {
    @Test
    void blocksAfterLimitAndRecoversAfterWindow() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        var props = new SumaProperties(null, false, List.of(), List.of(), "k", ".", "x", false, null, false, new SumaProperties.LoginRateLimit(3, 60));
        LoginRateLimiter l = new LoginRateLimiter(props, clock);
        assertThat(l.tryAcquire("ip")).isTrue();
        assertThat(l.tryAcquire("ip")).isTrue();
        assertThat(l.tryAcquire("ip")).isTrue();
        assertThat(l.tryAcquire("ip")).isFalse();
        assertThat(l.tryAcquire("other")).isTrue();
        clock.advance(Duration.ofSeconds(61));
        assertThat(l.tryAcquire("ip")).isTrue();
    }
}

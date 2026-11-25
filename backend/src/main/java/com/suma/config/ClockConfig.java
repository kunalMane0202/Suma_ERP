package com.suma.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ClockConfig {
    /** Injectable clock so time-dependent logic (attendance, SLA, schedulers) is deterministic in tests. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}

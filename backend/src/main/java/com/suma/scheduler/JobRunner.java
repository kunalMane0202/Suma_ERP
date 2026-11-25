package com.suma.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;

/** Records each scheduled job execution in job_execution_log (auto-committed, independent of the job's own transactions). */
@Component
public class JobRunner {
    private static final Logger log = LoggerFactory.getLogger(JobRunner.class);
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public JobRunner(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public void run(String name, Supplier<Integer> job) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into job_execution_log (id, job_name, started_at, status) values (?, ?, ?, 'RUNNING')",
                id, name, Timestamp.from(Instant.now(clock)));
        try {
            int n = job.get();
            jdbc.update("update job_execution_log set finished_at = ?, status = 'SUCCESS', items_processed = ? where id = ?",
                    Timestamp.from(Instant.now(clock)), n, id);
        } catch (RuntimeException e) {
            log.error("Scheduled job {} failed", name, e);
            String msg = e.getClass().getSimpleName() + ": " + e.getMessage();
            jdbc.update("update job_execution_log set finished_at = ?, status = 'FAILED', message = ? where id = ?",
                    Timestamp.from(Instant.now(clock)), msg.length() > 2000 ? msg.substring(0, 2000) : msg, id);
        }
    }
}

package com.suma.attendance;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.suma.notification.EmailDeliveryService;
import com.suma.support.IntegrationTest;
import com.suma.user.User;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MonthlyReportIT extends IntegrationTest {
    static GreenMail smtp;
    @Autowired MonthlyReportService monthly;
    @Autowired EmailDeliveryService delivery;

    @BeforeAll
    static void startSmtp() {
        smtp = new GreenMail(ServerSetupTest.SMTP);
        smtp.start();
    }

    @AfterAll
    static void stopSmtp() {
        smtp.stop();
    }

    @BeforeEach
    void clean() throws Exception {
        smtp.purgeEmailFromAllMailboxes();
        delivery.setBaseBackoff(Duration.ofMinutes(1));
        jdbc.update("update email_outbox set status = 'SENT' where status <> 'SENT'"); // isolate from other test classes
    }

    @Test
    void sendsPreviousMonthToVerifiedProjectLeadExactlyOnce() throws Exception {
        clock.set(Instant.parse("2027-01-01T07:00:00Z")); // reporting timezone UTC, scheduled hour 06:00
        User emp = createUser("L1");
        UUID project = projectWithMember(emp, "lead-" + UUID.randomUUID().toString().substring(0, 6) + "@test.suma.local", true);
        String to = jdbc.queryForObject("select project_lead_email from projects where id = ?", String.class, project);

        monthly.runScheduledCycle();
        monthly.runScheduledCycle(); // duplicate scheduler run (e.g. restart): must not create a second report
        delivery.processDue(50);
        delivery.processDue(50);

        assertThat(jdbc.queryForObject("select count(*) from attendance_report_logs where project_id = ?", Integer.class, project)).isEqualTo(1);
        var log = jdbc.queryForMap("select status, period_year, period_month, recipient_email, employee_count from attendance_report_logs where project_id = ?", project);
        assertThat(log.get("status")).isEqualTo("SENT");
        assertThat(log.get("period_year")).isEqualTo(2026);
        assertThat(log.get("period_month")).isEqualTo(12);
        assertThat(log.get("recipient_email")).isEqualTo(to);
        assertThat((Integer) log.get("employee_count")).isEqualTo(1);
        MimeMessage[] received = smtp.getReceivedMessagesForDomain("test.suma.local");
        assertThat(received).filteredOn(m -> { try { return m.getAllRecipients()[0].toString().equals(to); } catch (Exception e) { return false; } }).hasSize(1);
        MimeMessage m = received[received.length - 1];
        assertThat(m.getSubject()).contains("2026-12");
        assertThat(((MimeMultipart) m.getContent()).getCount()).isEqualTo(2); // body + Excel attachment
    }

    @Test
    void doesNotRunBeforeConfiguredHourOnTheFirst() {
        clock.set(Instant.parse("2027-02-01T03:00:00Z"));
        assertThat(monthly.duePeriod(clock.instant())).isEmpty();
        clock.set(Instant.parse("2027-02-01T06:30:00Z"));
        assertThat(monthly.duePeriod(clock.instant())).hasValueSatisfying(ym -> assertThat(ym.toString()).isEqualTo("2027-01"));
        clock.set(Instant.parse("2027-02-03T01:00:00Z")); // catch-up after downtime
        assertThat(monthly.duePeriod(clock.instant())).isPresent();
    }

    @Test
    void unverifiedOrMissingRecipientIsSkippedNotSent() {
        clock.set(Instant.parse("2027-03-01T07:00:00Z"));
        UUID unverified = projectWithMember(createUser("L2"), "someone@outside.example", false);
        UUID none = projectWithMember(createUser("L2"), null, false);
        monthly.runScheduledCycle();
        delivery.processDue(50);
        assertThat(jdbc.queryForObject("select status from attendance_report_logs where project_id = ?", String.class, unverified)).isEqualTo("SKIPPED_UNVERIFIED");
        assertThat(jdbc.queryForObject("select status from attendance_report_logs where project_id = ?", String.class, none)).isEqualTo("SKIPPED_NO_RECIPIENT");
        assertThat(smtp.getReceivedMessagesForDomain("outside.example")).isEmpty();
    }

    @Test
    void failedSmtpDeliveryIsRetriedThenMarkedFailed() throws Exception {
        clock.set(Instant.parse("2027-04-01T07:00:00Z"));
        jdbc.update("update attendance_settings set email_max_attempts = 2 where project_id is null");
        try {
            UUID project = projectWithMember(createUser("L1"), "retry-lead@test.suma.local", true);
            smtp.stop();
            monthly.runScheduledCycle();
            delivery.processDue(50);
            assertThat(jdbc.queryForObject("select status from attendance_report_logs where project_id = ?", String.class, project)).isEqualTo("RETRYING");
            assertThat(jdbc.queryForObject("select attempts from attendance_report_logs where project_id = ?", Integer.class, project)).isEqualTo(1);
            clock.advance(Duration.ofMinutes(2));
            delivery.processDue(50);
            assertThat(jdbc.queryForObject("select status from attendance_report_logs where project_id = ?", String.class, project)).isEqualTo("FAILED");
            assertThat(jdbc.queryForObject("select count(*) from notifications where type = 'MONTHLY_REPORT_FAILED'", Integer.class)).isGreaterThanOrEqualTo(0);

            // administrator resends explicitly once SMTP is back
            smtp = new GreenMail(ServerSetupTest.SMTP);
            smtp.start();
            User admin = createUser("ADMIN");
            Session a = login(admin.getUsername());
            String logId = jdbc.queryForObject("select id::text from attendance_report_logs where project_id = ?", String.class, project);
            mvc.perform(auth(post("/api/v1/attendance/monthly-reports/" + logId + "/send"), a).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk());
            delivery.processDue(50);
            assertThat(jdbc.queryForObject("select status from attendance_report_logs where project_id = ?", String.class, project)).isEqualTo("SENT");
            // already sent: a plain resend is refused, force is required
            mvc.perform(auth(post("/api/v1/attendance/monthly-reports/" + logId + "/send"), a)).andExpect(status().isConflict());
        } finally {
            jdbc.update("update attendance_settings set email_max_attempts = 5 where project_id is null");
            if (!smtp.isRunning()) smtp.start();
        }
    }
}

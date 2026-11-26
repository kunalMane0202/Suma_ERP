package com.suma.attendance;

import com.suma.support.IntegrationTest;
import com.suma.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Daily and monthly summary jobs: stored totals match the day, re-running is idempotent, exceptions notify once. */
class AttendanceSummaryIT extends IntegrationTest {
    @Autowired AttendanceMaintenanceService maintenance;

    @Test
    void dailySummaryStoresTotalsOnceAndNotifiesExceptionsOnce() throws Exception {
        User emp = createUser("L1");
        Session s = login(emp.getUsername());
        mvc.perform(auth(post("/api/v1/attendance/heartbeat"), s).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
        mvc.perform(auth(post("/api/v1/attendance/breaks/start"), s).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
        clock.advance(Duration.ofMinutes(10));
        mvc.perform(auth(post("/api/v1/attendance/breaks/end"), s)).andExpect(status().isOk());
        mvc.perform(auth(post("/api/v1/attendance/heartbeat"), s).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
        // browser closed without signing out: reconciled as a missing logout, which is an attendance exception
        clock.advance(Duration.ofMinutes(45));
        maintenance.reconcileSessions();
        LocalDate day = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);

        maintenance.summariseDay(day);
        maintenance.summariseDay(day); // idempotent: updates the same row, notifies only the first time
        Map<String, Object> row = jdbc.queryForMap("select session_count, break_count, break_seconds, missing_logout, exceptions from attendance_daily_summary where user_id = ? and work_date = ?",
                emp.getId(), day);
        assertThat(jdbc.queryForObject("select count(*) from attendance_daily_summary where user_id = ? and work_date = ?", Integer.class, emp.getId(), day)).isEqualTo(1);
        assertThat(((Number) row.get("session_count")).intValue()).isEqualTo(1);
        assertThat(((Number) row.get("break_count")).intValue()).isEqualTo(1);
        assertThat(((Number) row.get("break_seconds")).longValue()).isEqualTo(600);
        assertThat(row.get("missing_logout")).isEqualTo(true);
        assertThat((String) row.get("exceptions")).contains("MISSING_LOGOUT");
        assertThat(jdbc.queryForObject("select count(*) from notifications where user_id = ? and type = 'ATTENDANCE_EXCEPTION'", Integer.class, emp.getId())).isEqualTo(1);

        YearMonth ym = YearMonth.from(day);
        maintenance.summariseMonth(ym);
        maintenance.summariseMonth(ym);
        Map<String, Object> month = jdbc.queryForMap("select days_present, break_count, missing_logout_count from attendance_monthly_summary where user_id = ? and period_year = ? and period_month = ?",
                emp.getId(), ym.getYear(), ym.getMonthValue());
        assertThat(((Number) month.get("days_present")).intValue()).isEqualTo(1);
        assertThat(((Number) month.get("break_count")).intValue()).isEqualTo(1);
        assertThat(((Number) month.get("missing_logout_count")).intValue()).isEqualTo(1);
        sessionValidator.evictAll();
    }
}

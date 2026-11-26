package com.suma.auth;

import com.suma.support.IntegrationTest;
import com.suma.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccountAccessIT extends IntegrationTest {
    private static final Pattern TOKEN_LINK = Pattern.compile("#token=([A-Za-z0-9_-]+)");

    @Test
    void registrationRequestIsIdempotentAndOnlyAnAdminCanReviewIt() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "request-" + suffix + "@example.com";
        String body = "{\"fullName\":\"New Employee\",\"email\":\"" + email + "\"}";

        mvc.perform(post("/api/v1/auth/registration-requests").contentType(MediaType.APPLICATION_JSON).content(body)
                        .with(r -> { r.setRemoteAddr("198.51.100.61"); return r; }))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value("If the request is eligible, an administrator will review it."));
        mvc.perform(post("/api/v1/auth/registration-requests").contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace(email, email.toUpperCase()))
                        .with(r -> { r.setRemoteAddr("198.51.100.62"); return r; }))
                .andExpect(status().isAccepted());
        assertThat(jdbc.queryForObject("select count(*) from account_registration_requests where lower(email) = lower(?) and status = 'PENDING'",
                Integer.class, email)).isEqualTo(1);

        Session employee = login(createUser("L1").getUsername());
        mvc.perform(auth(get("/api/v1/account-requests"), employee)).andExpect(status().isForbidden());
    }

    @Test
    void adminApprovalSetupAndEmailPasswordResetUseSingleUseTokens() throws Exception {
        User admin = createUser("ADMIN");
        Session session = login(admin.getUsername());
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "approved-" + suffix + "@example.com";

        mvc.perform(post("/api/v1/auth/registration-requests").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Approved Employee\",\"email\":\"" + email + "\"}")
                        .with(r -> { r.setRemoteAddr("198.51.100.63"); return r; }))
                .andExpect(status().isAccepted());
        UUID requestId = jdbc.queryForObject("select id from account_registration_requests where email = ?", UUID.class, email);

        mvc.perform(auth(post("/api/v1/account-requests/" + requestId + "/approve"), session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"employeeCode\":\"A-" + suffix + "\",\"username\":\"approved-" + suffix + "\",\"role\":\"L1\"}"))
                .andExpect(status().isNoContent());
        UUID userId = jdbc.queryForObject("select id from users where email = ?", UUID.class, email);
        assertThat(jdbc.queryForObject("select status from users where id = ?", String.class, userId)).isEqualTo("INACTIVE");

        String setupBody = jdbc.queryForObject("select body from email_outbox where recipient = ? order by created_at desc limit 1", String.class, email);
        String setupToken = extractToken(setupBody);
        String storedHash = jdbc.queryForObject("select token_hash from password_action_tokens where user_id = ? and purpose = 'SETUP'", String.class, userId);
        assertThat(storedHash).isNotEqualTo(setupToken);

        String firstPassword = "Initial-pass12";
        completePassword(setupToken, firstPassword, "198.51.100.64").andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select status from users where id = ?", String.class, userId)).isEqualTo("ACTIVE");
        assertThat(encoder.matches(firstPassword, jdbc.queryForObject("select password_hash from users where id = ?", String.class, userId))).isTrue();
        completePassword(setupToken, firstPassword, "198.51.100.65").andExpect(status().isBadRequest());

        MvcResult known = mvc.perform(post("/api/v1/auth/password-reset-requests").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}").with(r -> { r.setRemoteAddr("198.51.100.66"); return r; }))
                .andExpect(status().isAccepted()).andReturn();
        MvcResult unknown = mvc.perform(post("/api/v1/auth/password-reset-requests").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"absent-" + suffix + "@example.com\"}").with(r -> { r.setRemoteAddr("198.51.100.67"); return r; }))
                .andExpect(status().isAccepted()).andReturn();
        assertThat(known.getResponse().getContentAsString()).isEqualTo(unknown.getResponse().getContentAsString());

        String resetBody = jdbc.queryForObject("""
                select e.body from email_outbox e
                join password_action_tokens t on e.related_id = t.id::text
                where e.recipient = ? and e.related_type = 'PasswordAction' and t.purpose = 'RESET'
                order by e.created_at desc limit 1
                """, String.class, email);
        String resetToken = extractToken(resetBody);
        String resetPassword = "Reset-pass12";
        completePassword(resetToken, resetPassword, "198.51.100.68").andExpect(status().isNoContent());
        assertThat(encoder.matches(resetPassword, jdbc.queryForObject("select password_hash from users where id = ?", String.class, userId))).isTrue();
        completePassword(resetToken, resetPassword, "198.51.100.69").andExpect(status().isBadRequest());
    }

    private ResultActions completePassword(String token, String password, String ip) throws Exception {
        return mvc.perform(post("/api/v1/auth/password/complete").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"newPassword\":\"" + password + "\"}")
                .with(r -> { r.setRemoteAddr(ip); return r; }));
    }

    private String extractToken(String body) {
        Matcher matcher = TOKEN_LINK.matcher(body);
        if (!matcher.find()) throw new AssertionError("Password action link missing from email");
        return matcher.group(1);
    }
}

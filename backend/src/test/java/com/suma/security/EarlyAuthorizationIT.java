package com.suma.security;

import com.suma.support.IntegrationTest;
import com.suma.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EarlyAuthorizationIT extends IntegrationTest {

    @Test
    void unauthorisedCallerWithInvalidBodyGets403NotValidationDetails() throws Exception {
        User l1 = createUser("L1");
        Session s = login(l1.getUsername());
        // L1 has no CLIENT_WRITE; an empty body would fail validation, but authorisation must be decided first
        mvc.perform(auth(post("/api/v1/clients"), s).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(auth(post("/api/v1/security/ip-rules"), s).contentType(MediaType.APPLICATION_JSON).content("{\"cidr\":\"not-an-ip\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void authorisedCallerWithInvalidBodyStillGetsValidationError() throws Exception {
        User admin = createUser("ADMIN");
        Session s = login(admin.getUsername());
        mvc.perform(auth(post("/api/v1/clients"), s).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }
}

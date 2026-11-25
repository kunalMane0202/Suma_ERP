package com.suma.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suma.auth.LoginRateLimiter;
import com.suma.security.SessionValidator;
import com.suma.user.RoleRepository;
import com.suma.user.User;
import com.suma.user.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestConfig.class)
public abstract class IntegrationTest {
    public static final String PASSWORD = "Password@123";
    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper mapper;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected UserRepository users;
    @Autowired protected RoleRepository roles;
    @Autowired protected PasswordEncoder encoder;
    @Autowired protected MutableClock clock;
    @Autowired protected SessionValidator sessionValidator;
    @Autowired protected LoginRateLimiter rateLimiter;

    @AfterEach
    void resetClock() {
        clock.reset();
        sessionValidator.evictAll();
    }

    protected User createUser(String role) {
        String u = role.toLowerCase() + "-" + UUID.randomUUID().toString().substring(0, 8);
        User x = new User();
        x.setEmployeeCode("T-" + u.toUpperCase());
        x.setUsername(u);
        x.setEmail(u + "@test.suma.local");
        x.setFullName("Test " + u);
        x.setRole(roles.findByCode(role).orElseThrow());
        x.setPasswordHash(encoder.encode(PASSWORD));
        return users.saveAndFlush(x);
    }

    public record Session(String token, Cookie refresh, JsonNode me) {}

    protected Session login(String username) throws Exception {
        return login(username, PASSWORD, "127.0.0.1");
    }

    protected Session login(String username, String password, String remoteAddr) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}")
                .with(req -> { req.setRemoteAddr(remoteAddr); return req; })).andReturn();
        if (r.getResponse().getStatus() != 200) throw new AssertionError("Login failed: " + r.getResponse().getContentAsString());
        JsonNode body = mapper.readTree(r.getResponse().getContentAsString());
        return new Session(body.get("accessToken").asText(), r.getResponse().getCookie("suma_rt"), body.get("user"));
    }

    protected MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder b, Session s) {
        return b.header("Authorization", "Bearer " + s.token());
    }

    protected JsonNode json(MvcResult r) throws Exception {
        return mapper.readTree(r.getResponse().getContentAsString());
    }

    protected UUID projectWithMember(User member, String leadEmail, boolean verified) {
        UUID client = UUID.randomUUID();
        UUID project = UUID.randomUUID();
        String code = "P" + project.toString().substring(0, 6).toUpperCase();
        jdbc.update("insert into clients (id, code, name) values (?, ?, ?)", client, "C" + code, "Client " + code);
        jdbc.update("insert into projects (id, client_id, code, name, project_lead_email, project_lead_email_verified) values (?,?,?,?,?,?)",
                project, client, code, "Project " + code, leadEmail, verified);
        jdbc.update("insert into project_members (project_id, user_id, designation) values (?, ?, 'MEMBER')", project, member.getId());
        return project;
    }
}

package com.suma.ticket;

import com.suma.support.IntegrationTest;
import com.suma.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CustomFieldIT extends IntegrationTest {
    @Autowired TicketService tickets;

    @Test
    void definitionsValidateTicketValuesAndOnlyAdminsManageThem() throws Exception {
        User admin = createUser("ADMIN");
        User eng = createUser("L1");
        UUID project = projectWithMember(eng, null, false);
        Session a = login(admin.getUsername());
        Session e = login(eng.getUsername());

        // engineers cannot define fields
        mvc.perform(auth(post("/api/v1/custom-fields"), e).contentType(MediaType.APPLICATION_JSON)
                .content("{\"fieldKey\":\"asset_tag\",\"label\":\"Asset tag\",\"fieldType\":\"TEXT\",\"required\":true}")).andExpect(status().isForbidden());
        // admins define a required text field for this project and an optional select field for all projects
        mvc.perform(auth(post("/api/v1/custom-fields"), a).contentType(MediaType.APPLICATION_JSON)
                .content("{\"fieldKey\":\"asset_tag\",\"label\":\"Asset tag\",\"fieldType\":\"TEXT\",\"required\":true,\"projectId\":\"" + project + "\"}")).andExpect(status().isCreated());
        String sel = json(mvc.perform(auth(post("/api/v1/custom-fields"), a).contentType(MediaType.APPLICATION_JSON)
                .content("{\"fieldKey\":\"site_" + project.toString().substring(0, 6) + "\",\"label\":\"Site\",\"fieldType\":\"SELECT\",\"options\":\"Pune, Mumbai\",\"required\":false,\"projectId\":\"" + project + "\"}"))
                .andExpect(status().isCreated()).andReturn()).get("id").asText();
        String siteKey = "site_" + project.toString().substring(0, 6);
        mvc.perform(auth(post("/api/v1/custom-fields"), a).contentType(MediaType.APPLICATION_JSON)
                .content("{\"fieldKey\":\"Bad Key\",\"label\":\"x\",\"fieldType\":\"TEXT\"}")).andExpect(status().isBadRequest());

        // the ticket form can read the fields that apply to the project
        mvc.perform(auth(get("/api/v1/custom-fields").param("projectId", project.toString()), e)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        String base = "{\"projectId\":\"" + project + "\",\"title\":\"Laptop broken\",\"priority\":\"LOW\"";
        mvc.perform(auth(post("/api/v1/tickets"), e).contentType(MediaType.APPLICATION_JSON).content(base + "}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Asset tag is required"));
        mvc.perform(auth(post("/api/v1/tickets"), e).contentType(MediaType.APPLICATION_JSON)
                .content(base + ",\"customFields\":{\"asset_tag\":\"LT-1\",\"" + siteKey + "\":\"Delhi\"}}")).andExpect(status().isBadRequest());
        mvc.perform(auth(post("/api/v1/tickets"), e).contentType(MediaType.APPLICATION_JSON)
                .content(base + ",\"customFields\":{\"asset_tag\":\"LT-1\",\"unknown\":\"x\"}}")).andExpect(status().isBadRequest());
        var created = json(mvc.perform(auth(post("/api/v1/tickets"), e).contentType(MediaType.APPLICATION_JSON)
                .content(base + ",\"customFields\":{\"asset_tag\":\" LT-1 \",\"" + siteKey + "\":\"Pune\"}}")).andExpect(status().isCreated()).andReturn());
        assertThat(created.get("customFields").get("asset_tag").asText()).isEqualTo("LT-1");
        assertThat(created.get("customFields").get(siteKey).asText()).isEqualTo("Pune");

        // integrations are not blocked by required fields
        var external = tickets.createInternal(new TicketDtos.CreateRequest(project, "From Jira", null, "LOW", null, null, null, null, null, null, Map.of()),
                "JIRA", "JIRA", "EXT-" + UUID.randomUUID(), null);
        assertThat(external.getId()).isNotNull();

        // deactivated fields stop applying
        mvc.perform(auth(delete("/api/v1/custom-fields/" + sel), a)).andExpect(status().isNoContent());
        mvc.perform(auth(get("/api/v1/custom-fields").param("projectId", project.toString()), e)).andExpect(jsonPath("$.length()").value(1));
    }
}

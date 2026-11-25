package com.suma.settings;

import com.suma.audit.AuditService;
import com.suma.common.ApiException;
import com.suma.notification.EmailTemplate;
import com.suma.notification.EmailTemplateRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/settings")
@Tag(name = "System configuration")
public class SettingsController {
    private final SettingsService settings;
    private final EmailTemplateRepository templates;
    private final AuditService audit;
    private final JdbcTemplate jdbc;

    public SettingsController(SettingsService settings, EmailTemplateRepository templates, AuditService audit, JdbcTemplate jdbc) {
        this.settings = settings;
        this.templates = templates;
        this.audit = audit;
        this.jdbc = jdbc;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    public List<SystemSetting> all() {
        return settings.all();
    }

    public record SettingUpdate(@NotNull @Size(max = 2000) String value) {}

    @PutMapping("/{key}")
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    public SystemSetting update(@PathVariable String key, @Valid @RequestBody SettingUpdate u) {
        return settings.update(key, u.value());
    }

    /** Reference data used by forms; available to any authenticated user. */
    @GetMapping("/reference")
    public Map<String, Object> reference() {
        return Map.of(
                "statuses", jdbc.queryForList("select code, label, color, sort_order as \"sortOrder\", is_terminal as \"terminal\" from ticket_status_config order by sort_order"),
                "priorities", jdbc.queryForList("select code, label, color, sort_order as \"sortOrder\" from ticket_priority_config order by sort_order"),
                "breakCategories", jdbc.queryForList("select code, label, active from break_categories order by sort_order"),
                "dashboardWidgets", settings.get("dashboard.widgets", "kpis,trend,priority,status,team,workforce"),
                "appName", settings.get("app.name", "SUMA Tracking Tool"));
    }

    public record StatusConfig(@NotBlank @Size(max = 60) String label, @NotBlank @Size(max = 20) String color) {}

    @PutMapping("/statuses/{code}")
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    @Transactional
    public void status(@PathVariable String code, @Valid @RequestBody StatusConfig c) {
        if (jdbc.update("update ticket_status_config set label = ?, color = ? where code = ?", c.label(), c.color(), code) == 0)
            throw ApiException.notFound("Status");
        audit.log("STATUS_CONFIG_UPDATED", "TicketStatus", code, c.label() + " " + c.color());
    }

    @PutMapping("/priorities/{code}")
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    @Transactional
    public void priority(@PathVariable String code, @Valid @RequestBody StatusConfig c) {
        if (jdbc.update("update ticket_priority_config set label = ?, color = ? where code = ?", c.label(), c.color(), code) == 0)
            throw ApiException.notFound("Priority");
        audit.log("PRIORITY_CONFIG_UPDATED", "TicketPriority", code, c.label() + " " + c.color());
    }

    @GetMapping("/email-templates")
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    public List<EmailTemplate> templates() {
        return templates.findAll();
    }

    public record TemplateUpdate(@NotBlank @Size(max = 300) String subject, @NotBlank @Size(max = 10000) String body) {}

    @PutMapping("/email-templates/{code}")
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    @Transactional
    public EmailTemplate template(@PathVariable String code, @Valid @RequestBody TemplateUpdate u) {
        EmailTemplate t = templates.findById(code).orElseThrow(() -> ApiException.notFound("Template"));
        t.setSubject(u.subject());
        t.setBody(u.body());
        t.setUpdatedAt(Instant.now());
        audit.log("EMAIL_TEMPLATE_UPDATED", "EmailTemplate", code, null);
        return t;
    }

    @GetMapping("/jobs")
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    public List<Map<String, Object>> jobs() {
        return jdbc.queryForList("""
                select job_name as "jobName", started_at as "startedAt", finished_at as "finishedAt", status,
                  items_processed as "itemsProcessed", message from job_execution_log order by started_at desc limit 100
                """);
    }
}

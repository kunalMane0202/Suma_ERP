package com.suma.ticket.field;

import com.suma.audit.AuditService;
import com.suma.common.ApiException;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/custom-fields")
@Tag(name = "Custom ticket fields")
public class CustomFieldController {
    public record FieldRequest(@NotBlank @Size(max = 60) @Pattern(regexp = "[a-z][a-z0-9_]*", message = "Key: lower-case letters, digits and _ (start with a letter)") String fieldKey,
                               @NotBlank @Size(max = 120) String label,
                               @NotBlank @Pattern(regexp = "TEXT|NUMBER|DATE|SELECT|BOOLEAN") String fieldType,
                               @Size(max = 2000) String options, boolean required, UUID projectId, Boolean active) {}

    private final CustomFieldRepository repo;
    private final AuditService audit;

    public CustomFieldController(CustomFieldRepository repo, AuditService audit) {
        this.repo = repo;
        this.audit = audit;
    }

    /** Active fields that apply to a project's tickets (for the ticket form). */
    @GetMapping
    @PreAuthorize("hasAuthority('TICKET_READ')")
    public List<CustomField> applicable(@RequestParam UUID projectId) {
        return repo.applicable(projectId);
    }

    @GetMapping("/all")
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    public List<CustomField> all() {
        return repo.allForTickets();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    @Transactional
    public CustomField create(@Valid @RequestBody FieldRequest r) {
        if (repo.existsByEntityTypeAndFieldKeyAndProjectId("TICKET", r.fieldKey(), r.projectId())) throw ApiException.conflict("A field with key " + r.fieldKey() + " already exists for this scope");
        CustomField f = new CustomField();
        f.setFieldKey(r.fieldKey());
        apply(f, r);
        CustomField saved = repo.save(f);
        audit.log("CUSTOM_FIELD_CREATED", "CustomField", saved.getId(), r.fieldKey() + " (" + r.fieldType() + ")");
        return saved;
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    @Transactional
    public CustomField update(@PathVariable UUID id, @Valid @RequestBody FieldRequest r) {
        CustomField f = repo.findById(id).orElseThrow(() -> ApiException.notFound("Custom field"));
        apply(f, r); // the key is fixed once created, so stored ticket values keep matching
        audit.log("CUSTOM_FIELD_UPDATED", "CustomField", id, f.getFieldKey());
        return f;
    }

    /** Deactivates instead of deleting, so values already stored on tickets stay readable in history. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    @Transactional
    public void deactivate(@PathVariable UUID id) {
        CustomField f = repo.findById(id).orElseThrow(() -> ApiException.notFound("Custom field"));
        f.setActive(false);
        audit.log("CUSTOM_FIELD_DEACTIVATED", "CustomField", id, f.getFieldKey());
    }

    private static void apply(CustomField f, FieldRequest r) {
        if ("SELECT".equals(r.fieldType()) && (r.options() == null || r.options().isBlank())) throw ApiException.badRequest("Choices are required for a select field");
        f.setLabel(r.label().trim());
        f.setFieldType(r.fieldType());
        f.setOptions("SELECT".equals(r.fieldType()) ? r.options().trim() : null);
        f.setRequired(r.required());
        f.setProjectId(r.projectId());
        if (r.active() != null) f.setActive(r.active());
    }
}

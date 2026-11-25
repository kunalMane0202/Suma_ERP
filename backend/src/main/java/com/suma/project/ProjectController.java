package com.suma.project;

import com.suma.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects")
@Tag(name = "Projects")
public class ProjectController {
    private final ProjectService service;

    public ProjectController(ProjectService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PROJECT_READ')")
    public PageResponse<ProjectDtos.ProjectResponse> list(@RequestParam(required = false) String q,
                                                          @RequestParam(required = false) UUID clientId,
                                                          @RequestParam(required = false) String status,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        return service.list(q, clientId, status, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PROJECT_READ')")
    public ProjectDtos.ProjectResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PROJECT_WRITE')")
    public ProjectDtos.ProjectResponse create(@Valid @RequestBody ProjectDtos.ProjectRequest r) {
        return service.create(r);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PROJECT_WRITE')")
    public ProjectDtos.ProjectResponse update(@PathVariable UUID id, @Valid @RequestBody ProjectDtos.ProjectRequest r) {
        return service.update(id, r);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('PROJECT_WRITE')")
    public void status(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        String s = body.get("status");
        if (!"ACTIVE".equals(s) && !"INACTIVE".equals(s)) throw new IllegalArgumentException("status must be ACTIVE or INACTIVE");
        service.setStatus(id, s);
    }

    @PostMapping("/{id}/lead-email/verify")
    @PreAuthorize("hasAuthority('PROJECT_WRITE')")
    @Operation(summary = "Mark the Project Lead report email as verified (or unverified). Monthly reports are only sent to verified recipients.")
    public ProjectDtos.ProjectResponse verifyLeadEmail(@PathVariable UUID id, @RequestParam(defaultValue = "true") boolean verified) {
        return service.verifyLeadEmail(id, verified);
    }

    @PutMapping("/{id}/members")
    @PreAuthorize("hasAuthority('PROJECT_WRITE')")
    public ProjectDtos.ProjectResponse upsertMember(@PathVariable UUID id, @Valid @RequestBody ProjectDtos.MemberRequest r) {
        return service.upsertMember(id, r);
    }

    @DeleteMapping("/{id}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('PROJECT_WRITE')")
    public void removeMember(@PathVariable UUID id, @PathVariable UUID userId) {
        service.removeMember(id, userId);
    }

    @GetMapping("/{id}/categories")
    @PreAuthorize("hasAuthority('PROJECT_READ')")
    public List<ProjectDtos.CategoryResponse> categories(@PathVariable UUID id) {
        return service.categories(id);
    }

    @PostMapping("/{id}/categories")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PROJECT_WRITE')")
    public ProjectDtos.CategoryResponse addCategory(@PathVariable UUID id, @Valid @RequestBody ProjectDtos.CategoryRequest r) {
        return service.addCategory(id, r);
    }

    @DeleteMapping("/{id}/categories/{categoryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('PROJECT_WRITE')")
    public void deleteCategory(@PathVariable UUID id, @PathVariable UUID categoryId) {
        service.deleteCategory(id, categoryId);
    }
}

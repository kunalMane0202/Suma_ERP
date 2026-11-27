package com.suma.project;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/budget")
@Tag(name = "Project budgets")
public class ProjectBudgetController {
    private final ProjectBudgetService service;

    public ProjectBudgetController(ProjectBudgetService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PROJECT_READ')")
    @Operation(summary = "Get the latest project budget dashboard and upload history")
    public ProjectBudgetDtos.Dashboard dashboard(@PathVariable UUID projectId) {
        return service.dashboard(projectId);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('PROJECT_WRITE')")
    @Operation(summary = "Import a CSV or XLSX project budget")
    public ProjectBudgetDtos.Dashboard upload(@PathVariable UUID projectId,
                                               @RequestPart("file") MultipartFile file,
                                               @RequestParam(defaultValue = "INR") String currency) {
        return service.upload(projectId, file, currency);
    }
}

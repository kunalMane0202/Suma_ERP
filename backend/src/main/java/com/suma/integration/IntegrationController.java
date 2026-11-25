package com.suma.integration;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/integrations")
@Tag(name = "Integrations")
@PreAuthorize("hasAuthority('INTEGRATION_MANAGE')")
public class IntegrationController {
    private final IntegrationService service;

    public IntegrationController(IntegrationService service) {
        this.service = service;
    }

    @GetMapping
    public List<IntegrationService.View> list() { return service.list(); }

    @GetMapping("/connectors")
    public Map<String, Map<String, Object>> connectors() { return service.defaultMappings(); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create integration. Credentials are encrypted at rest and never returned.")
    public IntegrationService.View create(@RequestBody IntegrationService.Request r) { return service.save(null, r); }

    @PutMapping("/{id}")
    public IntegrationService.View update(@PathVariable UUID id, @RequestBody IntegrationService.Request r) { return service.save(id, r); }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) { service.delete(id); }

    @PostMapping("/{id}/test")
    public Map<String, String> test(@PathVariable UUID id) { return Map.of("message", service.test(id)); }

    @PostMapping("/{id}/sync")
    public IntegrationService.SyncResult sync(@PathVariable UUID id) { return service.sync(id); }

    @GetMapping("/{id}/logs")
    public List<IntegrationSyncLog> logs(@PathVariable UUID id) { return service.logs(id); }
}

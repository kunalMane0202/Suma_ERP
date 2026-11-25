package com.suma.sla;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sla")
@Tag(name = "SLA")
public class SlaController {
    private final SlaService service;

    public SlaController(SlaService service) {
        this.service = service;
    }

    @GetMapping("/policies")
    @PreAuthorize("hasAuthority('SLA_READ')")
    public List<SlaPolicy> policies() { return service.listPolicies(); }

    @PostMapping("/policies")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SLA_WRITE')")
    public SlaPolicy create(@RequestBody SlaService.PolicyRequest r) { return service.savePolicy(null, r); }

    @PutMapping("/policies/{id}")
    @PreAuthorize("hasAuthority('SLA_WRITE')")
    public SlaPolicy update(@PathVariable UUID id, @RequestBody SlaService.PolicyRequest r) { return service.savePolicy(id, r); }

    @DeleteMapping("/policies/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('SLA_WRITE')")
    public void delete(@PathVariable UUID id) { service.deletePolicy(id); }

    @GetMapping("/calendars")
    @PreAuthorize("hasAuthority('SLA_READ')")
    public List<SlaService.CalendarDto> calendars() { return service.listCalendars(); }

    @PostMapping("/calendars")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SLA_WRITE')")
    public SlaService.CalendarDto createCalendar(@RequestBody SlaService.CalendarRequest r) { return service.saveCalendar(null, r); }

    @PutMapping("/calendars/{id}")
    @PreAuthorize("hasAuthority('SLA_WRITE')")
    public SlaService.CalendarDto updateCalendar(@PathVariable UUID id, @RequestBody SlaService.CalendarRequest r) { return service.saveCalendar(id, r); }

    @PostMapping("/calendars/{id}/holidays")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SLA_WRITE')")
    public Holiday addHoliday(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        return service.addHoliday(id, LocalDate.parse(body.get("date")), body.getOrDefault("name", "Holiday"));
    }

    @DeleteMapping("/holidays/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('SLA_WRITE')")
    public void deleteHoliday(@PathVariable UUID id) { service.deleteHoliday(id); }

    @GetMapping("/escalation-rules")
    @PreAuthorize("hasAuthority('SLA_READ')")
    public List<EscalationRule> escalations() { return service.listEscalations(); }

    @PostMapping("/escalation-rules")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SLA_WRITE')")
    public EscalationRule createEscalation(@RequestBody EscalationRule r) { return service.saveEscalation(null, r); }

    @DeleteMapping("/escalation-rules/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('SLA_WRITE')")
    public void deleteEscalation(@PathVariable UUID id) { service.deleteEscalation(id); }

}

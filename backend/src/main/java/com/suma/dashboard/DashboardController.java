package com.suma.dashboard;

import com.suma.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dashboard")
@Tag(name = "Dashboard")
public class DashboardController {
    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('DASHBOARD_VIEW')")
    public DashboardService.Summary summary(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                            @RequestParam(required = false) UUID clientId, @RequestParam(required = false) UUID projectId,
                                            @RequestParam(required = false) UUID teamId, @RequestParam(required = false) UUID assigneeId,
                                            @RequestParam(required = false) String status) {
        if (from != null && to != null && from.isAfter(to)) throw new IllegalArgumentException("from must be before to");
        if (from != null && to != null && from.plusDays(366).isBefore(to)) throw new IllegalArgumentException("Range limited to one year");
        return service.summary(CurrentUser.get(), new DashboardService.Filter(from, to, clientId, projectId, teamId, assigneeId,
                status == null || status.isBlank() ? null : status));
    }
}

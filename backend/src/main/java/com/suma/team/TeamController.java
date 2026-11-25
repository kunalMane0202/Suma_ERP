package com.suma.team;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/teams")
@Tag(name = "Teams")
public class TeamController {
    private final TeamService service;

    public TeamController(TeamService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('TEAM_READ')")
    public List<TeamDtos.TeamResponse> list(@RequestParam(required = false) UUID projectId) {
        return service.list(projectId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('TEAM_READ')")
    public TeamDtos.TeamResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('TEAM_WRITE')")
    public TeamDtos.TeamResponse create(@Valid @RequestBody TeamDtos.TeamRequest r) {
        return service.create(r);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('TEAM_WRITE')")
    public TeamDtos.TeamResponse update(@PathVariable UUID id, @Valid @RequestBody TeamDtos.TeamRequest r) {
        return service.update(id, r);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('TEAM_WRITE')")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}

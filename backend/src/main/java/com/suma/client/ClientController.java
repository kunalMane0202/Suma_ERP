package com.suma.client;

import com.suma.common.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clients")
@Tag(name = "Clients")
public class ClientController {
    private final ClientService service;

    public ClientController(ClientService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('CLIENT_READ')")
    public PageResponse<ClientDtos.ClientResponse> list(@RequestParam(required = false) String q,
                                                        @RequestParam(required = false) String status,
                                                        @RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "20") int size) {
        return service.list(q, status, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('CLIENT_READ')")
    public ClientDtos.ClientResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('CLIENT_WRITE')")
    public ClientDtos.ClientResponse create(@Valid @RequestBody ClientDtos.ClientRequest r) {
        return service.create(r);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('CLIENT_WRITE')")
    public ClientDtos.ClientResponse update(@PathVariable UUID id, @Valid @RequestBody ClientDtos.ClientRequest r) {
        return service.update(id, r);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('CLIENT_WRITE')")
    public void status(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        String s = body.get("status");
        if (!"ACTIVE".equals(s) && !"INACTIVE".equals(s)) throw new IllegalArgumentException("status must be ACTIVE or INACTIVE");
        service.setStatus(id, s);
    }
}

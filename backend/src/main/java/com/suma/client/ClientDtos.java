package com.suma.client;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ClientDtos {
    private ClientDtos() {}

    public record ContactDto(UUID id, @NotBlank @Size(max = 150) String name, @Email @Size(max = 200) String email,
                             @Size(max = 40) String phone, @Size(max = 100) String roleTitle, boolean primary) {}

    public record ClientRequest(@NotBlank @Size(max = 30) @Pattern(regexp = "^[A-Za-z0-9_-]+$") String code,
                                @NotBlank @Size(max = 200) String name, @Size(max = 100) String industry,
                                @Email @Size(max = 200) String email, @Size(max = 40) String phone,
                                @Size(max = 500) String address, @Size(max = 200) String website, UUID accountManagerId,
                                @Pattern(regexp = "ACTIVE|INACTIVE") String status, @Size(max = 4000) String configuration,
                                @Valid List<ContactDto> contacts) {}

    public record ClientResponse(UUID id, String code, String name, String industry, String email, String phone,
                                 String address, String website, UUID accountManagerId, String accountManagerName,
                                 String status, String configuration, List<ContactDto> contacts, long projectCount,
                                 long openTickets, long totalTickets, Instant createdAt) {}
}

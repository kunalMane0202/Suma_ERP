package com.suma.team;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class TeamDtos {
    private TeamDtos() {}

    public record TeamRequest(@NotBlank @Size(max = 120) String name, @Size(max = 500) String description, UUID projectId,
                              UUID teamLeaderId, Set<UUID> memberIds, Boolean active) {}

    public record MemberDto(UUID id, String employeeCode, String fullName, String role, long openTickets) {}

    public record TeamResponse(UUID id, String name, String description, UUID projectId, String projectName,
                               UUID teamLeaderId, String teamLeaderName, boolean active, List<MemberDto> members,
                               long openTickets, long resolvedLast30Days) {}
}

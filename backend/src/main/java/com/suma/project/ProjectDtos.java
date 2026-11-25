package com.suma.project;

import jakarta.validation.constraints.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ProjectDtos {
    private ProjectDtos() {}

    public record ProjectRequest(@NotNull UUID clientId,
                                 @NotBlank @Size(max = 30) @Pattern(regexp = "^[A-Za-z0-9_-]+$") String code,
                                 @NotBlank @Size(max = 200) String name, @Size(max = 4000) String description,
                                 UUID managerId, UUID teamLeaderId, UUID projectLeadId,
                                 @Email @Size(max = 200) String projectLeadEmail, UUID calendarId,
                                 @Size(max = 100) String supportHours, @Size(max = 60) String timezone,
                                 @Pattern(regexp = "ACTIVE|INACTIVE") String status, LocalDate startDate, LocalDate endDate) {}

    public record MemberRequest(@NotNull UUID userId, @Pattern(regexp = "MEMBER|PROJECT_LEAD|TEAM_LEADER|MANAGER") String designation,
                                @Min(0) @Max(100) Integer allocationPercent, Boolean primary) {}

    public record MemberResponse(UUID userId, String employeeCode, String fullName, String role, String designation,
                                 int allocationPercent, boolean primary, Instant assignedAt) {}

    public record CategoryRequest(@NotBlank @Size(max = 120) String name, UUID parentId, @Size(max = 500) String keywords, UUID defaultTeamId) {}

    public record CategoryResponse(UUID id, UUID projectId, UUID parentId, String name, String keywords, UUID defaultTeamId) {}

    public record ProjectResponse(UUID id, UUID clientId, String clientName, String code, String name, String description,
                                  UUID managerId, String managerName, UUID teamLeaderId, String teamLeaderName,
                                  UUID projectLeadId, String projectLeadName, String projectLeadEmail,
                                  boolean projectLeadEmailVerified, UUID calendarId, String supportHours, String timezone,
                                  String status, LocalDate startDate, LocalDate endDate, long memberCount,
                                  long openTickets, long totalTickets, Instant createdAt, List<MemberResponse> members) {}
}

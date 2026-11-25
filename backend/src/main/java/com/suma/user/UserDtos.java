package com.suma.user;

import jakarta.validation.constraints.*;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class UserDtos {
    private UserDtos() {}

    public record UserRequest(@NotBlank @Size(max = 40) @Pattern(regexp = "^[A-Za-z0-9_-]+$") String employeeCode,
                              @NotBlank @Size(min = 3, max = 80) @Pattern(regexp = "^[A-Za-z0-9._-]+$") String username,
                              @NotBlank @Email @Size(max = 200) String email,
                              @NotBlank @Size(max = 150) String fullName,
                              @Size(max = 40) String phone, @Size(max = 100) String jobTitle,
                              @NotBlank @Pattern(regexp = "ADMIN|MANAGER|TEAM_LEADER|L1|L2|L3") String role,
                              UUID shiftId, Set<@Min(1) @Max(7) Integer> weeklyOffs, @Size(max = 60) String timezone,
                              @Min(1) @Max(500) Integer maxOpenTickets,
                              @Size(min = 10, max = 100) @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$", message = "must contain upper, lower case letters and a digit") String password,
                              @Pattern(regexp = "ACTIVE|INACTIVE|LOCKED") String status) {}

    public record ProjectAllocation(UUID projectId, String projectCode, String projectName, String designation, int allocationPercent, boolean primary) {}

    public record UserResponse(UUID id, String employeeCode, String username, String email, String fullName, String phone,
                               String jobTitle, String role, UUID shiftId, String shiftName, Set<Integer> weeklyOffs,
                               String timezone, Integer maxOpenTickets, String status, Instant lastLoginAt, String lastLoginIp,
                               Instant createdAt, List<ProjectAllocation> projects, List<String> teams, Long openTickets) {}

    public record UserSummary(UUID id, String employeeCode, String fullName, String role, String email) {}

    public record ResetPasswordRequest(@NotBlank @Size(min = 10, max = 100) @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$", message = "must contain upper, lower case letters and a digit") String newPassword) {}

    public record ShiftRequest(@NotBlank @Size(max = 100) String name, @NotNull LocalTime startTime, @NotNull LocalTime endTime,
                               @Size(max = 60) String timezone, Boolean active) {}

    public record ShiftResponse(UUID id, String name, LocalTime startTime, LocalTime endTime, String timezone, boolean active) {}

    public record RoleResponse(UUID id, String code, String name, String description, Set<String> permissions) {}

    public record PermissionResponse(String code, String module, String description) {}

    public record RolePermissionsRequest(@NotNull Set<String> permissions) {}

    public record WorkloadRow(UUID userId, String employeeCode, String fullName, String role, long open, long inProgress,
                              long pending, long resolvedLast30Days, long breachedOpen, Integer capacity) {}
}

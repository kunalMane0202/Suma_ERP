package com.suma.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class AuthDtos {
    private AuthDtos() {}

    public record LoginRequest(@NotBlank @Size(max = 200) String username, @NotBlank @Size(max = 200) String password) {}

    public record TokenResponse(String accessToken, long expiresInSeconds, MeResponse user) {}

    public record ProjectRef(UUID id, String code, String name, String designation) {}

    public record MeResponse(UUID id, String employeeCode, String username, String fullName, String email, String role,
                             Set<String> permissions, String themePreference, String timezone, UUID sessionId,
                             List<ProjectRef> projects, boolean projectLead, boolean teamLeader) {}

    public record PreferencesRequest(@Pattern(regexp = "LIGHT|DARK") String themePreference, @Size(max = 60) String timezone) {}

    public record ChangePasswordRequest(@NotBlank String currentPassword,
                                        @NotBlank @Size(min = 10, max = 100)
                                        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$", message = "must contain upper, lower case letters and a digit")
                                        String newPassword) {}
}

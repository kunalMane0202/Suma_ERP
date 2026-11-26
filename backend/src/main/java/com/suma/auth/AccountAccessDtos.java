package com.suma.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class AccountAccessDtos {
    private AccountAccessDtos() {}

    public record RegistrationRequest(@NotBlank @Size(max = 150) String fullName,
                                      @NotBlank @Email @Size(max = 200) String email) {}

    public record PasswordResetRequest(@NotBlank @Email @Size(max = 200) String email) {}

    public record PasswordCompletionRequest(@NotBlank @Size(max = 100) String token,
                                            @NotBlank @Size(min = 10, max = 100)
                                            @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
                                                    message = "must contain upper, lower case letters and a digit")
                                            String newPassword) {}

    public record ApproveRequest(@NotBlank @Size(max = 40) @Pattern(regexp = "^[A-Za-z0-9_-]+$") String employeeCode,
                                 @NotBlank @Size(min = 3, max = 80) @Pattern(regexp = "^[A-Za-z0-9._-]+$") String username,
                                 @NotBlank @Pattern(regexp = "ADMIN|MANAGER|TEAM_LEADER|L1|L2|L3") String role) {}

    public record RegistrationRequestSummary(UUID id, String fullName, String email, Instant createdAt) {}

    public record MessageResponse(String message) {}
}

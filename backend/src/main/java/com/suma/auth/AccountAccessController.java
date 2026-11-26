package com.suma.auth;

import com.suma.common.ClientIpResolver;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Account registration and password recovery")
public class AccountAccessController {
    private final AccountAccessService service;
    private final ClientIpResolver ipResolver;

    public AccountAccessController(AccountAccessService service, ClientIpResolver ipResolver) {
        this.service = service;
        this.ipResolver = ipResolver;
    }

    @PostMapping("/auth/registration-requests")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AccountAccessDtos.MessageResponse requestRegistration(@Valid @RequestBody AccountAccessDtos.RegistrationRequest req,
                                                                 HttpServletRequest http) {
        service.requestRegistration(req, ipResolver.clientInfo(http).ip());
        return new AccountAccessDtos.MessageResponse("If the request is eligible, an administrator will review it.");
    }

    @PostMapping("/auth/password-reset-requests")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AccountAccessDtos.MessageResponse requestPasswordReset(@Valid @RequestBody AccountAccessDtos.PasswordResetRequest req,
                                                                  HttpServletRequest http) {
        service.requestPasswordReset(req.email(), ipResolver.clientInfo(http).ip());
        return new AccountAccessDtos.MessageResponse("If an active account uses that email, a password reset link will be sent.");
    }

    @PostMapping("/auth/password/complete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void completePasswordAction(@Valid @RequestBody AccountAccessDtos.PasswordCompletionRequest req, HttpServletRequest http) {
        service.completePasswordAction(req, ipResolver.clientInfo(http).ip());
    }

    @GetMapping("/account-requests")
    @PreAuthorize("hasAuthority('USER_WRITE')")
    public List<AccountAccessDtos.RegistrationRequestSummary> pendingRequests() {
        return service.pendingRequests();
    }

    @PostMapping("/account-requests/{id}/approve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('USER_WRITE')")
    public void approve(@PathVariable UUID id, @Valid @RequestBody AccountAccessDtos.ApproveRequest req) {
        service.approve(id, req);
    }

    @PostMapping("/account-requests/{id}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('USER_WRITE')")
    public void reject(@PathVariable UUID id) {
        service.reject(id);
    }
}

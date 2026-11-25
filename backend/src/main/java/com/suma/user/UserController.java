package com.suma.user;

import com.suma.common.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Users, roles and shifts")
public class UserController {
    private final UserAdminService service;

    public UserController(UserAdminService service) {
        this.service = service;
    }

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('USER_READ')")
    public PageResponse<UserDtos.UserResponse> list(@RequestParam(required = false) String q, @RequestParam(required = false) String role,
                                                    @RequestParam(required = false) String status,
                                                    @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.list(q, role, status, page, size);
    }

    /** Lightweight list for pickers (assignee, team leader...). Available to any authenticated user, scoped. */
    @GetMapping("/users/directory")
    public List<UserDtos.UserSummary> directory() {
        return service.directory();
    }

    @GetMapping("/users/workload")
    @PreAuthorize("hasAuthority('USER_READ')")
    public List<UserDtos.WorkloadRow> workload(@RequestParam(required = false) UUID projectId) {
        return service.workload(projectId);
    }

    @GetMapping("/users/{id}")
    @PreAuthorize("hasAuthority('USER_READ')")
    public UserDtos.UserResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('USER_WRITE')")
    public UserDtos.UserResponse create(@Valid @RequestBody UserDtos.UserRequest r) {
        return service.create(r);
    }

    @PutMapping("/users/{id}")
    @PreAuthorize("hasAuthority('USER_WRITE')")
    public UserDtos.UserResponse update(@PathVariable UUID id, @Valid @RequestBody UserDtos.UserRequest r) {
        return service.update(id, r);
    }

    @PostMapping("/users/{id}/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('USER_WRITE')")
    public void resetPassword(@PathVariable UUID id, @Valid @RequestBody UserDtos.ResetPasswordRequest r) {
        service.resetPassword(id, r.newPassword());
    }

    @PostMapping("/users/{id}/unlock")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('USER_WRITE')")
    public void unlock(@PathVariable UUID id) {
        service.unlock(id);
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('USER_READ') or hasAuthority('ROLE_MANAGE')")
    public List<UserDtos.RoleResponse> roles() {
        return service.roles();
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    public List<UserDtos.PermissionResponse> permissions() {
        return service.permissions();
    }

    @PutMapping("/roles/{code}/permissions")
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    public UserDtos.RoleResponse setPermissions(@PathVariable String code, @Valid @RequestBody UserDtos.RolePermissionsRequest r) {
        return service.setRolePermissions(code, r.permissions());
    }

    @GetMapping("/shifts")
    public List<UserDtos.ShiftResponse> shifts() {
        return service.shiftList();
    }

    @PostMapping("/shifts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('USER_WRITE')")
    public UserDtos.ShiftResponse createShift(@Valid @RequestBody UserDtos.ShiftRequest r) {
        return service.saveShift(null, r);
    }

    @PutMapping("/shifts/{id}")
    @PreAuthorize("hasAuthority('USER_WRITE')")
    public UserDtos.ShiftResponse updateShift(@PathVariable UUID id, @Valid @RequestBody UserDtos.ShiftRequest r) {
        return service.saveShift(id, r);
    }
}

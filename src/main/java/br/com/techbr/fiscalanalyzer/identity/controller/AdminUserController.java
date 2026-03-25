package br.com.techbr.fiscalanalyzer.identity.controller;

import br.com.techbr.fiscalanalyzer.identity.dto.CreateUserRequest;
import br.com.techbr.fiscalanalyzer.identity.dto.UpdateUserStatusRequest;
import br.com.techbr.fiscalanalyzer.identity.dto.UserResponse;
import br.com.techbr.fiscalanalyzer.identity.model.AppUserStatus;
import br.com.techbr.fiscalanalyzer.identity.service.IdentityAdminService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/users")
@Validated
public class AdminUserController {

    private final IdentityAdminService identityAdminService;

    public AdminUserController(IdentityAdminService identityAdminService) {
        this.identityAdminService = identityAdminService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@RequestBody @Valid CreateUserRequest request) {
        return identityAdminService.createUser(request);
    }

    @GetMapping
    public List<UserResponse> list(@RequestParam(required = false) AppUserStatus status) {
        return identityAdminService.listUsers(status);
    }

    @GetMapping("/{userId}")
    public UserResponse get(@PathVariable @Min(1) Long userId) {
        return identityAdminService.getUser(userId);
    }

    @PatchMapping("/{userId}/status")
    public UserResponse updateStatus(@PathVariable @Min(1) Long userId,
                                     @RequestBody @Valid UpdateUserStatusRequest request) {
        return identityAdminService.updateUserStatus(userId, request);
    }

    @PutMapping("/{userId}/roles/{roleCode}")
    public UserResponse addRole(@PathVariable @Min(1) Long userId,
                                @PathVariable @NotBlank String roleCode) {
        return identityAdminService.addRole(userId, roleCode);
    }

    @DeleteMapping("/{userId}/roles/{roleCode}")
    public UserResponse removeRole(@PathVariable @Min(1) Long userId,
                                   @PathVariable @NotBlank String roleCode) {
        return identityAdminService.removeRole(userId, roleCode);
    }

    @PutMapping("/{userId}/tenants/{tenantId}/empresas/{empresaId}")
    public UserResponse addEmpresaAccess(@PathVariable @Min(1) Long userId,
                                         @PathVariable @Min(1) Long tenantId,
                                         @PathVariable @Min(1) Long empresaId) {
        return identityAdminService.addEmpresaAccess(userId, tenantId, empresaId);
    }

    @DeleteMapping("/{userId}/tenants/{tenantId}/empresas/{empresaId}")
    public UserResponse removeEmpresaAccess(@PathVariable @Min(1) Long userId,
                                            @PathVariable @Min(1) Long tenantId,
                                            @PathVariable @Min(1) Long empresaId) {
        return identityAdminService.removeEmpresaAccess(userId, tenantId, empresaId);
    }
}

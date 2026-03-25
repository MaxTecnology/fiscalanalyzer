package br.com.techbr.fiscalanalyzer.identity.controller;

import br.com.techbr.fiscalanalyzer.identity.dto.BootstrapAdminRequest;
import br.com.techbr.fiscalanalyzer.identity.dto.BootstrapAdminResponse;
import br.com.techbr.fiscalanalyzer.identity.service.IdentityBootstrapService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@Validated
public class AuthBootstrapController {

    private static final String BOOTSTRAP_HEADER = "X-Bootstrap-Token";

    private final IdentityBootstrapService identityBootstrapService;

    public AuthBootstrapController(IdentityBootstrapService identityBootstrapService) {
        this.identityBootstrapService = identityBootstrapService;
    }

    @PostMapping("/bootstrap-admin")
    @ResponseStatus(HttpStatus.CREATED)
    public BootstrapAdminResponse bootstrapAdmin(
            @RequestHeader(value = BOOTSTRAP_HEADER, required = false) String bootstrapToken,
            @RequestBody @Valid BootstrapAdminRequest request
    ) {
        return identityBootstrapService.bootstrap(bootstrapToken, request);
    }
}


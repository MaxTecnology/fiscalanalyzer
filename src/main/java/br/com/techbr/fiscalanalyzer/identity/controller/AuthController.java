package br.com.techbr.fiscalanalyzer.identity.controller;

import br.com.techbr.fiscalanalyzer.identity.dto.AuthLoginRequest;
import br.com.techbr.fiscalanalyzer.identity.dto.AuthLoginResponse;
import br.com.techbr.fiscalanalyzer.identity.dto.AuthMeResponse;
import br.com.techbr.fiscalanalyzer.identity.security.UserAuthRequestContext;
import br.com.techbr.fiscalanalyzer.identity.service.IdentityAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@Validated
public class AuthController {

    private final IdentityAuthService identityAuthService;

    public AuthController(IdentityAuthService identityAuthService) {
        this.identityAuthService = identityAuthService;
    }

    @PostMapping("/login")
    public AuthLoginResponse login(@RequestBody @Valid AuthLoginRequest request) {
        return identityAuthService.login(request);
    }

    @GetMapping("/me")
    public AuthMeResponse me(HttpServletRequest servletRequest) {
        var auth = UserAuthRequestContext.required(servletRequest);
        return new AuthMeResponse(identityAuthService.me(auth));
    }
}


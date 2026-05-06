package com.doller.platform.web;

import com.doller.platform.dto.AuthDtos;
import com.doller.platform.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) { this.authService = authService; }

    @PostMapping("/init-owner")
    public AuthDtos.AuthResponse initOwner(
            @RequestHeader(value = "X-Bootstrap-Token", required = false) String bootstrapToken,
            @Valid @RequestBody AuthDtos.InitOwnerRequest request,
            HttpServletRequest httpRequest
    ) {
        return authService.initOwner(request, bootstrapToken, clientKey(httpRequest));
    }

    @PostMapping("/login")
    public AuthDtos.AuthResponse login(@Valid @RequestBody AuthDtos.LoginRequest request, HttpServletRequest httpRequest) {
        return authService.login(request, clientKey(httpRequest));
    }

    @PostMapping("/refresh")
    public AuthDtos.AuthResponse refresh(@Valid @RequestBody AuthDtos.RefreshRequest request, HttpServletRequest httpRequest) {
        return authService.refresh(request, clientKey(httpRequest));
    }

    @PostMapping("/change-password")
    public void changePassword(@Valid @RequestBody AuthDtos.ChangePasswordRequest request, HttpServletRequest httpRequest) {
        authService.changePassword(request, clientKey(httpRequest));
    }

    private String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}

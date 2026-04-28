package com.doller.platform.web;

import com.doller.platform.dto.AuthDtos;
import com.doller.platform.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) { this.authService = authService; }

    @PostMapping("/init-owner")
    public AuthDtos.AuthResponse initOwner(@Valid @RequestBody AuthDtos.InitOwnerRequest request) { return authService.initOwner(request); }

    @PostMapping("/login")
    public AuthDtos.AuthResponse login(@Valid @RequestBody AuthDtos.LoginRequest request) { return authService.login(request); }

    @PostMapping("/refresh")
    public AuthDtos.AuthResponse refresh(@Valid @RequestBody AuthDtos.RefreshRequest request) { return authService.refresh(request); }

    @PostMapping("/change-password")
    public void changePassword(@Valid @RequestBody AuthDtos.ChangePasswordRequest request) { authService.changePassword(request); }
}

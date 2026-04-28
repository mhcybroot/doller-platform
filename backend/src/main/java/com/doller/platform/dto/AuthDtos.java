package com.doller.platform.dto;

import jakarta.validation.constraints.NotBlank;

public class AuthDtos {
    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
    public record RefreshRequest(@NotBlank String refreshToken) {}
    public record InitOwnerRequest(@NotBlank String username, @NotBlank String password) {}
    public record ChangePasswordRequest(@NotBlank String oldPassword, @NotBlank String newPassword) {}
    public record AuthResponse(String accessToken, String refreshToken, long accessExpiresInSeconds, long refreshExpiresInSeconds, String role, boolean mustChangePassword) {}
}

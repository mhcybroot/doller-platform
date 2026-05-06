package com.doller.platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class AuthDtos {
    public record LoginRequest(
            @NotBlank @Size(min = 3, max = 64) String username,
            @NotBlank @Size(min = 8, max = 256) String password
    ) {}

    public record RefreshRequest(@NotBlank String refreshToken) {}

    public record InitOwnerRequest(
            @NotBlank @Size(min = 3, max = 64) @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "Username contains invalid characters") String username,
            @NotBlank @Size(min = 12, max = 256) String password
    ) {}

    public record ChangePasswordRequest(
            @NotBlank @Size(min = 8, max = 256) String oldPassword,
            @NotBlank @Size(min = 12, max = 256) String newPassword
    ) {}

    public record AuthResponse(String accessToken, String refreshToken, long accessExpiresInSeconds, long refreshExpiresInSeconds, String role, boolean mustChangePassword) {}
}

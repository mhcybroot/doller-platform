package com.doller.platform.dto;

import com.doller.platform.domain.enums.Role;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class MasterDataDtos {
    public record PartyCreateRequest(
            @NotBlank String name,
            String phone,
            String address,
            String notes,
            @DecimalMin("0.00") BigDecimal openingReceivableBdt,
            @DecimalMin("0.00") BigDecimal openingPayableBdt
    ) {}

    public record PartyUpdateRequest(
            @NotBlank String name,
            String phone,
            String address,
            String notes
    ) {}

    public record UserCreateRequest(
            @NotBlank @Size(min = 3, max = 64) @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "Username contains invalid characters") String username,
            @NotBlank @Size(min = 12, max = 256) String password,
            @NotNull Role role
    ) {}

    public record UserResponse(
            Long id,
            String username,
            String role,
            boolean active,
            boolean mustChangePassword
    ) {}

    public record PartyResponse(
            Long id,
            String name,
            String phone,
            String address,
            String notes
    ) {}

    public record AuditLogResponse(
            Long id,
            String action,
            String actor,
            String requestPath,
            String metadata,
            String reason,
            LocalDateTime createdAt
    ) {}
}

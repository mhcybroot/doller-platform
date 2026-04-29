package com.doller.platform.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

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
}

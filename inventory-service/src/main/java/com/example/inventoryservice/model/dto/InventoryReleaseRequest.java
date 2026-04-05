package com.example.inventoryservice.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryReleaseRequest {
    @NotBlank
    private String orderId;

    @NotBlank
    private String reservationId;

    private String reason;
}

package com.example.inventoryservice.model.dto;

import jakarta.validation.constraints.*;
import lombok.*;

// ─── Reserve ─────────────────────────────────────────────────────────────────

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class InventoryRequest {
    @NotBlank private String orderId;
    @NotBlank private String productId;
    @Min(1)  private int quantity;
}

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class InventoryResponse {
    private String reservationId;
    private String orderId;
    private String status;    // RESERVED | OUT_OF_STOCK | FAILED
    private String reason;
}

// ─── Release ─────────────────────────────────────────────────────────────────

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class InventoryReleaseRequest {
    @NotBlank private String orderId;
    @NotBlank private String reservationId;
    private String reason;
}

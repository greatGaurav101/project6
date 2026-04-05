package com.example.orderservice.model.dto;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class InventoryRequest {
    private String orderId;
    private String productId;
    private int quantity;
}

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
class InventoryResponse {
    private String reservationId;
    private String orderId;
    private String status;   // RESERVED | OUT_OF_STOCK | FAILED
    private String reason;

    public boolean isReserved() {
        return "RESERVED".equalsIgnoreCase(status);
    }
}

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
class InventoryReleaseRequest {
    private String orderId;
    private String reservationId;
    private String reason;
}

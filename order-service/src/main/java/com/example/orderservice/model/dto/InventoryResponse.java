package com.example.orderservice.model.dto;

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
public class InventoryResponse {
    private String reservationId;
    private String orderId;
    private String status; // RESERVED | OUT_OF_STOCK | FAILED
    private String reason;

    public boolean isReserved() {
        return "RESERVED".equalsIgnoreCase(status);
    }
}

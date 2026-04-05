package com.example.orderservice.model.dto;

import com.example.orderservice.model.enums.OrderStatus;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// ─── Inbound ─────────────────────────────────────────────────────────────────

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OrderRequest {

    @NotBlank(message = "Customer ID is required")
    private String customerId;

    @NotBlank(message = "Product ID is required")
    private String productId;

    @Min(value = 1, message = "Quantity must be at least 1")
    private int quantity;

    @NotNull @DecimalMin(value = "0.01", message = "Amount must be positive")
    private BigDecimal amount;
}

// ─── Outbound ────────────────────────────────────────────────────────────────

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
class OrderResponse {

    private String orderId;
    private OrderStatus status;
    private String message;
    private LocalDateTime timestamp;

    public static OrderResponse success(String orderId) {
        return OrderResponse.builder()
                .orderId(orderId)
                .status(OrderStatus.CONFIRMED)
                .message("Order placed successfully")
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static OrderResponse failed(String orderId, String reason) {
        return OrderResponse.builder()
                .orderId(orderId)
                .status(OrderStatus.FAILED)
                .message(reason)
                .timestamp(LocalDateTime.now())
                .build();
    }
}

package com.example.orderservice.model.dto;

import com.example.orderservice.model.enums.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderResponse {

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

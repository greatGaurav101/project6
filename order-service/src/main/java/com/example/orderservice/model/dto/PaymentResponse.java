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
public class PaymentResponse {
    private String paymentId;
    private String orderId;
    private String status; // SUCCESS | FAILED | INSUFFICIENT_FUNDS
    private String reason;

    public boolean isSuccess() {
        return "SUCCESS".equalsIgnoreCase(status);
    }
}

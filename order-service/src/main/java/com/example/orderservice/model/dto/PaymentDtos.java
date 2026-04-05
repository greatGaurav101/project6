package com.example.orderservice.model.dto;

import lombok.*;
import java.math.BigDecimal;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PaymentRequest {
    private String orderId;
    private String customerId;
    private BigDecimal amount;
}

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
class PaymentResponse {
    private String paymentId;
    private String orderId;
    private String status;   // SUCCESS | FAILED | INSUFFICIENT_FUNDS
    private String reason;

    public boolean isSuccess() {
        return "SUCCESS".equalsIgnoreCase(status);
    }
}

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
class RefundRequest {
    private String orderId;
    private String paymentId;
    private String reason;
}

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
class RefundResponse {
    private String refundId;
    private String status;
    private String message;
}

package com.example.paymentservice.model.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

// ─── Charge ──────────────────────────────────────────────────────────────────

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PaymentRequest {
    @NotBlank private String orderId;
    @NotBlank private String customerId;
    @NotNull @DecimalMin("0.01") private BigDecimal amount;
}

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PaymentResponse {
    private String paymentId;
    private String orderId;
    private String status;
    private String reason;
}

// ─── Refund ───────────────────────────────────────────────────────────────────

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RefundRequest {
    @NotBlank private String orderId;
    @NotBlank private String paymentId;
    private String reason;
}

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RefundResponse {
    private String refundId;
    private String orderId;
    private String status;
    private String message;
}

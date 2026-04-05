package com.example.orderservice.client;

import com.example.orderservice.model.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Circuit Breaker fallback for PaymentClient.
 *
 * Invoked automatically when:
 *  - Circuit is OPEN (failure threshold exceeded)
 *  - A call times out (timelimiter threshold)
 *  - The remote service throws an exception
 *
 * CRITICAL: Returns a structured failure — never throws here.
 * The Saga Orchestrator reads the status field to decide compensation.
 */
@Slf4j
@Component
public class PaymentClientFallback implements PaymentClient {

    @Override
    public PaymentResponse charge(PaymentRequest request) {
        log.warn("[CIRCUIT BREAKER] Payment service unavailable for orderId={}. Returning fallback.",
                request.getOrderId());
        return PaymentResponse.builder()
                .orderId(request.getOrderId())
                .status("FAILED")
                .reason("Payment service is currently unavailable. Circuit breaker is OPEN.")
                .build();
    }

    @Override
    public RefundResponse refund(RefundRequest request) {
        // Refund fallback — this is a compensation step that FAILED.
        // Log for manual reconciliation / dead-letter processing.
        log.error("[CIRCUIT BREAKER] Refund compensation FAILED for orderId={}, paymentId={}. " +
                  "REQUIRES MANUAL RECONCILIATION.", request.getOrderId(), request.getPaymentId());
        return RefundResponse.builder()
                .status("FAILED")
                .message("Refund service unavailable — scheduled for manual reconciliation")
                .build();
    }
}

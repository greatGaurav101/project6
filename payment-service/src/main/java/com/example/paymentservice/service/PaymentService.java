package com.example.paymentservice.service;

import com.example.paymentservice.model.dto.*;
import com.example.paymentservice.model.entity.Payment;
import com.example.paymentservice.model.enums.PaymentStatus;
import com.example.paymentservice.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;

    // Simulated credit limit per customer for demo purposes
    private static final BigDecimal CUSTOMER_CREDIT_LIMIT = new BigDecimal("10000.00");

    /**
     * Charge a payment for an order.
     *
     * IDEMPOTENT: If a payment for this orderId already exists,
     * returns the existing result instead of charging again.
     * This prevents double-charges if the Saga retries step 2.
     */
    @Transactional
    public PaymentResponse charge(PaymentRequest request) {
        log.info("[PAYMENT] Charge request for orderId={}, amount={}", request.getOrderId(), request.getAmount());

        // Idempotency check — return existing result if already processed
        Optional<Payment> existing = paymentRepository.findByOrderId(request.getOrderId());
        if (existing.isPresent()) {
            Payment p = existing.get();
            log.info("[PAYMENT] Idempotent hit — returning existing payment for orderId={}", request.getOrderId());
            return PaymentResponse.builder()
                    .paymentId(p.getId())
                    .orderId(p.getOrderId())
                    .status(p.getStatus().name())
                    .reason(p.getFailureReason())
                    .build();
        }

        // Simulate credit check
        if (request.getAmount().compareTo(CUSTOMER_CREDIT_LIMIT) > 0) {
            Payment failed = Payment.builder()
                    .orderId(request.getOrderId())
                    .customerId(request.getCustomerId())
                    .amount(request.getAmount())
                    .status(PaymentStatus.INSUFFICIENT_FUNDS)
                    .failureReason("Amount exceeds credit limit of " + CUSTOMER_CREDIT_LIMIT)
                    .build();
            paymentRepository.save(failed);

            log.warn("[PAYMENT] Insufficient funds for orderId={}", request.getOrderId());
            return PaymentResponse.builder()
                    .paymentId(failed.getId())
                    .orderId(request.getOrderId())
                    .status("FAILED")
                    .reason(failed.getFailureReason())
                    .build();
        }

        // Charge success
        Payment payment = Payment.builder()
                .orderId(request.getOrderId())
                .customerId(request.getCustomerId())
                .amount(request.getAmount())
                .status(PaymentStatus.SUCCESS)
                .build();
        paymentRepository.save(payment);

        log.info("[PAYMENT] Charge SUCCESS for orderId={}, paymentId={}", request.getOrderId(), payment.getId());
        return PaymentResponse.builder()
                .paymentId(payment.getId())
                .orderId(payment.getOrderId())
                .status("SUCCESS")
                .build();
    }

    /**
     * Refund a payment (Saga compensation step).
     *
     * IDEMPOTENT: If already refunded, returns success silently.
     * Prevents double-refunds if compensation is retried.
     */
    @Transactional
    public RefundResponse refund(RefundRequest request) {
        log.info("[PAYMENT] Refund request for orderId={}, paymentId={}", request.getOrderId(), request.getPaymentId());

        Payment payment = paymentRepository.findById(request.getPaymentId())
                .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + request.getPaymentId()));

        // Idempotency: already refunded — return success silently
        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            log.info("[PAYMENT] Idempotent refund hit — already refunded for paymentId={}", request.getPaymentId());
            return RefundResponse.builder()
                    .refundId(payment.getId())
                    .orderId(payment.getOrderId())
                    .status("SUCCESS")
                    .message("Already refunded")
                    .build();
        }

        payment.setStatus(PaymentStatus.REFUNDED);
        payment.setFailureReason("Refunded: " + request.getReason());
        paymentRepository.save(payment);

        log.info("[PAYMENT] Refund SUCCESS for paymentId={}", payment.getId());
        return RefundResponse.builder()
                .refundId(payment.getId())
                .orderId(payment.getOrderId())
                .status("SUCCESS")
                .message("Refund processed successfully")
                .build();
    }

    public Payment getPayment(String paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));
    }
}

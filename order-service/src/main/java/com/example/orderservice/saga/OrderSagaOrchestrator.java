package com.example.orderservice.saga;

import com.example.orderservice.client.InventoryClient;
import com.example.orderservice.client.PaymentClient;
import com.example.orderservice.model.dto.*;
import com.example.orderservice.model.entity.Order;
import com.example.orderservice.model.enums.OrderStatus;
import com.example.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Saga Orchestrator — controls the Order → Payment → Inventory distributed transaction.
 *
 * Flow:
 *  1. Create order (PENDING)
 *  2. Charge payment  → success: continue | failure: cancel order
 *  3. Reserve inventory → success: confirm order | failure: refund payment → cancel order
 *
 * Each step persists state to DB before calling the next service.
 * This ensures we can always resume/identify compensation needs after a crash.
 *
 * Compensation is idempotent — safe to retry.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderSagaOrchestrator {

    private final OrderRepository orderRepository;
    private final PaymentClient paymentClient;
    private final InventoryClient inventoryClient;

    @Transactional
    public OrderResponse execute(OrderRequest request) {
        log.info("[SAGA] Starting saga for customerId={}, productId={}",
                request.getCustomerId(), request.getProductId());

        // ── Step 1: Create order locally ──────────────────────────────────────
        Order order = Order.builder()
                .customerId(request.getCustomerId())
                .productId(request.getProductId())
                .quantity(request.getQuantity())
                .amount(request.getAmount())
                .status(OrderStatus.PENDING)
                .build();
        orderRepository.save(order);
        log.info("[SAGA] Step 1 complete — order created: id={}", order.getId());

        // ── Step 2: Charge Payment ────────────────────────────────────────────
        order.setStatus(OrderStatus.PAYMENT_PROCESSING);
        orderRepository.save(order); // persist state BEFORE calling remote service

        PaymentResponse paymentResponse = paymentClient.charge(
                PaymentRequest.builder()
                        .orderId(order.getId())
                        .customerId(order.getCustomerId())
                        .amount(order.getAmount())
                        .build()
        );

        if (!paymentResponse.isSuccess()) {
            // Payment failed or circuit breaker fallback — no money moved, just cancel
            log.warn("[SAGA] Step 2 FAILED — payment rejected for orderId={}. Reason: {}",
                    order.getId(), paymentResponse.getReason());
            return failOrder(order, "Payment failed: " + paymentResponse.getReason());
        }

        // Persist paymentId — critical for compensation if step 3 fails
        order.setPaymentId(paymentResponse.getPaymentId());
        orderRepository.save(order);
        log.info("[SAGA] Step 2 complete — payment charged: paymentId={}", paymentResponse.getPaymentId());

        // ── Step 3: Reserve Inventory ─────────────────────────────────────────
        order.setStatus(OrderStatus.INVENTORY_RESERVING);
        orderRepository.save(order);

        InventoryResponse inventoryResponse = inventoryClient.reserve(
                InventoryRequest.builder()
                        .orderId(order.getId())
                        .productId(order.getProductId())
                        .quantity(order.getQuantity())
                        .build()
        );

        if (!inventoryResponse.isReserved()) {
            log.warn("[SAGA] Step 3 FAILED — inventory unavailable for orderId={}. Reason: {}. Triggering compensation.",
                    order.getId(), inventoryResponse.getReason());
            // COMPENSATION: Payment was charged — must refund
            compensatePayment(order);
            return failOrder(order, "Inventory unavailable: " + inventoryResponse.getReason());
        }

        order.setInventoryReservationId(inventoryResponse.getReservationId());
        orderRepository.save(order);
        log.info("[SAGA] Step 3 complete — inventory reserved: reservationId={}",
                inventoryResponse.getReservationId());

        // ── Step 4: Confirm Order ─────────────────────────────────────────────
        order.setStatus(OrderStatus.CONFIRMED);
        orderRepository.save(order);
        log.info("[SAGA] SUCCESS — order confirmed: id={}", order.getId());

        return OrderResponse.success(order.getId());
    }

    // ── Compensation ──────────────────────────────────────────────────────────

    /**
     * Compensation transaction: refund the payment.
     * Must be IDEMPOTENT — safe to call multiple times with the same orderId.
     * Uses orderId as the idempotency key in Payment Service.
     */
    private void compensatePayment(Order order) {
        order.setStatus(OrderStatus.COMPENSATING);
        orderRepository.save(order);

        log.info("[SAGA COMPENSATION] Refunding payment for orderId={}, paymentId={}",
                order.getId(), order.getPaymentId());
        try {
            RefundResponse refundResponse = paymentClient.refund(
                    RefundRequest.builder()
                            .orderId(order.getId())
                            .paymentId(order.getPaymentId())
                            .reason("Inventory reservation failed — automatic refund")
                            .build()
            );

            if ("SUCCESS".equalsIgnoreCase(refundResponse.getStatus())) {
                log.info("[SAGA COMPENSATION] Refund SUCCESS for orderId={}", order.getId());
                order.setStatus(OrderStatus.COMPENSATED);
            } else {
                // Refund itself failed (possibly circuit breaker fallback)
                // Mark for manual reconciliation — do not re-throw
                log.error("[SAGA COMPENSATION] Refund FAILED for orderId={}. Status={}. Message={}. " +
                          "MANUAL RECONCILIATION REQUIRED.",
                        order.getId(), refundResponse.getStatus(), refundResponse.getMessage());
                order.setFailureReason("COMPENSATION_REQUIRED: " + refundResponse.getMessage());
            }
        } catch (Exception e) {
            log.error("[SAGA COMPENSATION] Unexpected error during refund for orderId={}: {}",
                    order.getId(), e.getMessage(), e);
            order.setFailureReason("COMPENSATION_ERROR: " + e.getMessage());
        }

        orderRepository.save(order);
    }

    private OrderResponse failOrder(Order order, String reason) {
        if (order.getStatus() != OrderStatus.COMPENSATING && order.getStatus() != OrderStatus.COMPENSATED) {
            order.setStatus(OrderStatus.FAILED);
        } else {
            order.setStatus(OrderStatus.FAILED); // final terminal state
        }
        order.setFailureReason(reason);
        orderRepository.save(order);
        return OrderResponse.failed(order.getId(), reason);
    }
}

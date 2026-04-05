package com.example.orderservice.saga;

import com.example.orderservice.client.InventoryClient;
import com.example.orderservice.client.PaymentClient;
import com.example.orderservice.model.dto.*;
import com.example.orderservice.model.entity.Order;
import com.example.orderservice.model.enums.OrderStatus;
import com.example.orderservice.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderSagaOrchestratorTest {

    @Mock private OrderRepository orderRepository;
    @Mock private PaymentClient paymentClient;
    @Mock private InventoryClient inventoryClient;

    @InjectMocks private OrderSagaOrchestrator orchestrator;

    @Captor private ArgumentCaptor<Order> orderCaptor;

    private OrderRequest validRequest;

    @BeforeEach
    void setUp() {
        validRequest = OrderRequest.builder()
                .customerId("CUST-001")
                .productId("PROD-001")
                .quantity(2)
                .amount(new BigDecimal("199.99"))
                .build();

        // Default: save returns the order as-is
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            if (o.getId() == null) {
                // Simulate ID generation
                try {
                    var f = Order.class.getDeclaredField("id");
                    f.setAccessible(true);
                    f.set(o, "order-test-uuid");
                } catch (Exception ignored) {}
            }
            return o;
        });
    }

    @Test
    @DisplayName("Happy path: payment and inventory both succeed → CONFIRMED")
    void execute_happyPath_confirmsOrder() {
        when(paymentClient.charge(any())).thenReturn(PaymentResponse.builder()
                .paymentId("PAY-001").orderId("order-test-uuid").status("SUCCESS").build());

        when(inventoryClient.reserve(any())).thenReturn(InventoryResponse.builder()
                .reservationId("RES-001").orderId("order-test-uuid").status("RESERVED").build());

        OrderResponse response = orchestrator.execute(validRequest);

        assertThat(response.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(response.getOrderId()).isNotNull();

        // Verify payment was called once, inventory was called once
        verify(paymentClient, times(1)).charge(any());
        verify(inventoryClient, times(1)).reserve(any());
        // Compensation should NOT be triggered
        verify(paymentClient, never()).refund(any());
    }

    @Test
    @DisplayName("Payment fails → order FAILED, inventory never called, no compensation")
    void execute_paymentFails_orderFailedNoCompensation() {
        when(paymentClient.charge(any())).thenReturn(PaymentResponse.builder()
                .orderId("order-test-uuid").status("FAILED").reason("Insufficient funds").build());

        OrderResponse response = orchestrator.execute(validRequest);

        assertThat(response.getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(response.getMessage()).contains("Payment failed");

        verify(inventoryClient, never()).reserve(any()); // inventory never reached
        verify(paymentClient, never()).refund(any());    // no compensation needed
    }

    @Test
    @DisplayName("Circuit breaker fallback on payment → order FAILED, no inventory call")
    void execute_paymentCircuitBreakerFallback_orderFailed() {
        // Circuit breaker fallback returns FAILED status (not an exception)
        when(paymentClient.charge(any())).thenReturn(PaymentResponse.builder()
                .orderId("order-test-uuid")
                .status("FAILED")
                .reason("Payment service is currently unavailable. Circuit breaker is OPEN.")
                .build());

        OrderResponse response = orchestrator.execute(validRequest);

        assertThat(response.getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(response.getMessage()).contains("Circuit breaker");
        verify(inventoryClient, never()).reserve(any());
    }

    @Test
    @DisplayName("Inventory fails → compensation: refund triggered → order FAILED")
    void execute_inventoryFails_compensationRefundTriggered() {
        when(paymentClient.charge(any())).thenReturn(PaymentResponse.builder()
                .paymentId("PAY-001").orderId("order-test-uuid").status("SUCCESS").build());

        when(inventoryClient.reserve(any())).thenReturn(InventoryResponse.builder()
                .orderId("order-test-uuid").status("OUT_OF_STOCK").reason("Insufficient stock").build());

        when(paymentClient.refund(any())).thenReturn(RefundResponse.builder()
                .refundId("REF-001").status("SUCCESS").message("Refunded").build());

        OrderResponse response = orchestrator.execute(validRequest);

        assertThat(response.getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(response.getMessage()).contains("Inventory");

        // Compensation MUST have been triggered
        verify(paymentClient, times(1)).refund(any());

        // Order should have gone through COMPENSATING state
        verify(orderRepository, atLeast(3)).save(orderCaptor.capture());
        boolean hadCompensating = orderCaptor.getAllValues().stream()
                .anyMatch(o -> o.getStatus() == OrderStatus.COMPENSATING);
        assertThat(hadCompensating).isTrue();
    }

    @Test
    @DisplayName("Inventory fails + refund compensation also fails → logs error, does not throw")
    void execute_inventoryFails_compensationRefundFails_doesNotThrow() {
        when(paymentClient.charge(any())).thenReturn(PaymentResponse.builder()
                .paymentId("PAY-001").orderId("order-test-uuid").status("SUCCESS").build());

        when(inventoryClient.reserve(any())).thenReturn(InventoryResponse.builder()
                .orderId("order-test-uuid").status("FAILED").reason("Service error").build());

        // Refund itself fails (e.g., circuit breaker also open for refund)
        when(paymentClient.refund(any())).thenReturn(RefundResponse.builder()
                .status("FAILED").message("Payment service unavailable — manual reconciliation").build());

        // Must NOT throw — compensation failures are logged for manual handling
        OrderResponse response = orchestrator.execute(validRequest);

        assertThat(response.getStatus()).isEqualTo(OrderStatus.FAILED);
        verify(paymentClient, times(1)).refund(any()); // compensation was attempted
    }
}

package com.example.orderservice.client;

import com.example.orderservice.model.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class InventoryClientFallback implements InventoryClient {

    @Override
    public InventoryResponse reserve(InventoryRequest request) {
        log.warn("[CIRCUIT BREAKER] Inventory service unavailable for orderId={}. Returning fallback.",
                request.getOrderId());
        return InventoryResponse.builder()
                .orderId(request.getOrderId())
                .status("FAILED")
                .reason("Inventory service is currently unavailable. Circuit breaker is OPEN.")
                .build();
    }

    @Override
    public void release(InventoryReleaseRequest request) {
        log.error("[CIRCUIT BREAKER] Inventory release compensation FAILED for orderId={}, reservationId={}. " +
                  "REQUIRES MANUAL RECONCILIATION.", request.getOrderId(), request.getReservationId());
    }
}

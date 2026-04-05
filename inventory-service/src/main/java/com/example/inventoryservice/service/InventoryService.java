package com.example.inventoryservice.service;

import com.example.inventoryservice.model.dto.*;
import com.example.inventoryservice.model.entity.Product;
import com.example.inventoryservice.model.entity.Reservation;
import com.example.inventoryservice.model.enums.ReservationStatus;
import com.example.inventoryservice.repository.ProductRepository;
import com.example.inventoryservice.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final ProductRepository productRepository;
    private final ReservationRepository reservationRepository;

    /**
     * Reserve stock for an order.
     *
     * IDEMPOTENT: If a reservation for this orderId already exists, return existing result.
     * CONCURRENCY SAFE: Uses @Version (optimistic locking) on Product to prevent overselling.
     */
    @Transactional
    public InventoryResponse reserve(InventoryRequest request) {
        log.info("[INVENTORY] Reserve request: orderId={}, productId={}, qty={}",
                request.getOrderId(), request.getProductId(), request.getQuantity());

        // Idempotency check
        Optional<Reservation> existing = reservationRepository.findByOrderId(request.getOrderId());
        if (existing.isPresent()) {
            Reservation r = existing.get();
            log.info("[INVENTORY] Idempotent hit — returning existing reservation for orderId={}", request.getOrderId());
            return InventoryResponse.builder()
                    .reservationId(r.getId())
                    .orderId(r.getOrderId())
                    .status(r.getStatus().name())
                    .reason(r.getFailureReason())
                    .build();
        }

        // Load product
        Product product = productRepository.findById(request.getProductId())
                .orElse(null);

        if (product == null) {
            return saveAndReturn(Reservation.builder()
                    .orderId(request.getOrderId())
                    .productId(request.getProductId())
                    .quantity(request.getQuantity())
                    .status(ReservationStatus.FAILED)
                    .failureReason("Product not found: " + request.getProductId())
                    .build(), "FAILED");
        }

        // Stock check
        if (product.getAvailableQuantity() < request.getQuantity()) {
            log.warn("[INVENTORY] Out of stock: productId={}, available={}, requested={}",
                    request.getProductId(), product.getAvailableQuantity(), request.getQuantity());
            return saveAndReturn(Reservation.builder()
                    .orderId(request.getOrderId())
                    .productId(request.getProductId())
                    .quantity(request.getQuantity())
                    .status(ReservationStatus.OUT_OF_STOCK)
                    .failureReason("Insufficient stock. Available: " + product.getAvailableQuantity()
                            + ", Requested: " + request.getQuantity())
                    .build(), "OUT_OF_STOCK");
        }

        // Deduct stock — optimistic lock prevents concurrent overselling
        try {
            product.setAvailableQuantity(product.getAvailableQuantity() - request.getQuantity());
            productRepository.save(product); // throws ObjectOptimisticLockingFailureException on conflict
        } catch (ObjectOptimisticLockingFailureException e) {
            log.error("[INVENTORY] Optimistic lock conflict for productId={} — concurrent reservation detected",
                    request.getProductId());
            return InventoryResponse.builder()
                    .orderId(request.getOrderId())
                    .status("FAILED")
                    .reason("Concurrent stock update conflict. Please retry.")
                    .build();
        }

        Reservation reservation = Reservation.builder()
                .orderId(request.getOrderId())
                .productId(request.getProductId())
                .quantity(request.getQuantity())
                .status(ReservationStatus.RESERVED)
                .build();
        reservationRepository.save(reservation);

        log.info("[INVENTORY] Reserve SUCCESS: reservationId={}, productId={}, remaining stock={}",
                reservation.getId(), request.getProductId(), product.getAvailableQuantity());

        return InventoryResponse.builder()
                .reservationId(reservation.getId())
                .orderId(reservation.getOrderId())
                .status("RESERVED")
                .build();
    }

    /**
     * Release reserved stock (Saga compensation step).
     *
     * IDEMPOTENT: If already released, returns silently.
     */
    @Transactional
    public void release(InventoryReleaseRequest request) {
        log.info("[INVENTORY] Release request: orderId={}, reservationId={}",
                request.getOrderId(), request.getReservationId());

        Reservation reservation = reservationRepository.findById(request.getReservationId())
                .orElse(null);

        if (reservation == null) {
            log.warn("[INVENTORY] Reservation not found for release: {}", request.getReservationId());
            return;
        }

        // Idempotency: already released
        if (reservation.getStatus() == ReservationStatus.RELEASED) {
            log.info("[INVENTORY] Idempotent release hit — already released for reservationId={}",
                    request.getReservationId());
            return;
        }

        // Restore stock
        productRepository.findById(reservation.getProductId()).ifPresent(product -> {
            product.setAvailableQuantity(product.getAvailableQuantity() + reservation.getQuantity());
            productRepository.save(product);
            log.info("[INVENTORY] Stock restored: productId={}, restored qty={}, new stock={}",
                    product.getId(), reservation.getQuantity(), product.getAvailableQuantity());
        });

        reservation.setStatus(ReservationStatus.RELEASED);
        reservation.setFailureReason("Released: " + request.getReason());
        reservationRepository.save(reservation);
    }

    public Product getProduct(String productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));
    }

    private InventoryResponse saveAndReturn(Reservation reservation, String status) {
        reservationRepository.save(reservation);
        return InventoryResponse.builder()
                .reservationId(reservation.getId())
                .orderId(reservation.getOrderId())
                .status(status)
                .reason(reservation.getFailureReason())
                .build();
    }
}

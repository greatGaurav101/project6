package com.example.inventoryservice.repository;

import com.example.inventoryservice.model.entity.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, String> {
    Optional<Reservation> findByOrderId(String orderId);
}

package com.example.inventoryservice.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "products")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Product {

    @Id
    private String id;       // productId — set explicitly (e.g. "PROD-001")

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int availableQuantity;

    @Version  // Optimistic locking — prevents overselling under concurrent requests
    private Long version;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}

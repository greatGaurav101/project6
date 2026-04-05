package com.example.orderservice.model.enums;

public enum OrderStatus {
    PENDING,
    PAYMENT_PROCESSING,
    INVENTORY_RESERVING,
    CONFIRMED,
    FAILED,
    COMPENSATING,
    COMPENSATED
}

package com.example.orderservice.service;

import com.example.orderservice.exception.OrderNotFoundException;
import com.example.orderservice.model.dto.OrderRequest;
import com.example.orderservice.model.dto.OrderResponse;
import com.example.orderservice.model.entity.Order;
import com.example.orderservice.repository.OrderRepository;
import com.example.orderservice.saga.OrderSagaOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderSagaOrchestrator sagaOrchestrator;
    private final OrderRepository orderRepository;

    /**
     * Entry point for placing an order.
     * Delegates entirely to the Saga Orchestrator.
     */
    public OrderResponse placeOrder(OrderRequest request) {
        return sagaOrchestrator.execute(request);
    }

    public Order getOrder(String orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));
    }

    public List<Order> getOrdersByCustomer(String customerId) {
        return orderRepository.findByCustomerId(customerId);
    }
}

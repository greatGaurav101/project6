package com.example.orderservice.client;

import com.example.orderservice.model.dto.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(
        name = "payment-service",
        url = "${payment.service.url}",
        fallback = PaymentClientFallback.class
)
public interface PaymentClient {

    @PostMapping("/api/payments/charge")
    PaymentResponse charge(@RequestBody PaymentRequest request);

    @PostMapping("/api/payments/refund")
    RefundResponse refund(@RequestBody RefundRequest request);
}

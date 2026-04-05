package com.example.paymentservice.controller;

import com.example.paymentservice.model.dto.*;
import com.example.paymentservice.model.entity.Payment;
import com.example.paymentservice.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/charge")
    public ResponseEntity<PaymentResponse> charge(@Valid @RequestBody PaymentRequest request) {
        return ResponseEntity.ok(paymentService.charge(request));
    }

    @PostMapping("/refund")
    public ResponseEntity<RefundResponse> refund(@Valid @RequestBody RefundRequest request) {
        return ResponseEntity.ok(paymentService.refund(request));
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<Payment> getPayment(@PathVariable String paymentId) {
        return ResponseEntity.ok(paymentService.getPayment(paymentId));
    }
}

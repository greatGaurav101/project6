package com.example.inventoryservice.controller;

import com.example.inventoryservice.model.dto.*;
import com.example.inventoryservice.model.entity.Product;
import com.example.inventoryservice.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @PostMapping("/reserve")
    public ResponseEntity<InventoryResponse> reserve(@Valid @RequestBody InventoryRequest request) {
        return ResponseEntity.ok(inventoryService.reserve(request));
    }

    @PostMapping("/release")
    public ResponseEntity<Void> release(@Valid @RequestBody InventoryReleaseRequest request) {
        inventoryService.release(request);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/product/{productId}")
    public ResponseEntity<Product> getProduct(@PathVariable String productId) {
        return ResponseEntity.ok(inventoryService.getProduct(productId));
    }
}

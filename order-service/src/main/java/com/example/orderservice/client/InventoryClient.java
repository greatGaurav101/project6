package com.example.orderservice.client;

import com.example.orderservice.model.dto.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(
        name = "inventory-service",
        url = "${inventory.service.url}",
        fallback = InventoryClientFallback.class
)
public interface InventoryClient {

    @PostMapping("/api/inventory/reserve")
    InventoryResponse reserve(@RequestBody InventoryRequest request);

    @PostMapping("/api/inventory/release")
    void release(@RequestBody InventoryReleaseRequest request);
}

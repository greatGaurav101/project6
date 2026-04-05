package com.example.inventoryservice.config;

import com.example.inventoryservice.model.entity.Product;
import com.example.inventoryservice.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final ProductRepository productRepository;

    @Override
    public void run(String... args) {
        productRepository.save(Product.builder()
                .id("PROD-001").name("Laptop").availableQuantity(50).build());
        productRepository.save(Product.builder()
                .id("PROD-002").name("Smartphone").availableQuantity(100).build());
        productRepository.save(Product.builder()
                .id("PROD-003").name("Headphones").availableQuantity(5).build());  // low stock for demo
        productRepository.save(Product.builder()
                .id("PROD-004").name("Keyboard").availableQuantity(0).build());    // out of stock for demo

        log.info("[SEEDER] Products seeded: PROD-001 (50), PROD-002 (100), PROD-003 (5), PROD-004 (0)");
    }
}

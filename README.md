# Order-Payment-Inventory Microservices
## Circuit Breaker + Saga Pattern (Spring Boot 3 + Resilience4j + Feign)

---

## Architecture Overview

```
Client
  └── POST /api/orders
        └── Order Service (port 8081)
              ├── Saga Orchestrator
              │     ├── [Step 2] PaymentClient  ──[CB]──► Payment Service (port 8082)
              │     └── [Step 3] InventoryClient ──[CB]──► Inventory Service (port 8083)
              └── OrderRepository ──► H2 (orderdb)
```

- **Circuit Breaker** (Resilience4j) wraps every Feign call — trips when failure threshold is exceeded
- **Saga Orchestrator** (Orchestration style) coordinates the distributed transaction and triggers compensation
- **Idempotency** on Payment and Inventory — safe to retry; no double-charges, no double-reservations
- **Optimistic Locking** on Product stock — prevents overselling under concurrent load

---

## Project Structure

```
microservices/
├── order-service/                          # Port 8081
│   └── src/main/java/com/example/orderservice/
│       ├── OrderServiceApplication.java
│       ├── controller/
│       │   └── OrderController.java
│       ├── service/
│       │   └── OrderService.java
│       ├── saga/
│       │   └── OrderSagaOrchestrator.java  ◄── Core Saga logic
│       ├── client/
│       │   ├── PaymentClient.java          ◄── Feign client
│       │   ├── PaymentClientFallback.java  ◄── Circuit breaker fallback
│       │   ├── InventoryClient.java
│       │   └── InventoryClientFallback.java
│       ├── model/
│       │   ├── entity/Order.java
│       │   ├── enums/OrderStatus.java
│       │   └── dto/                        (OrderDtos, PaymentDtos, InventoryDtos)
│       ├── repository/OrderRepository.java
│       └── exception/Exceptions.java
│
├── payment-service/                        # Port 8082
│   └── src/main/java/com/example/paymentservice/
│       ├── PaymentServiceApplication.java
│       ├── controller/PaymentController.java
│       ├── service/PaymentService.java     ◄── Idempotent charge + refund
│       ├── model/
│       │   ├── entity/Payment.java
│       │   ├── enums/PaymentStatus.java
│       │   └── dto/PaymentDtos.java
│       └── repository/PaymentRepository.java
│
└── inventory-service/                      # Port 8083
    └── src/main/java/com/example/inventoryservice/
        ├── InventoryServiceApplication.java
        ├── controller/InventoryController.java
        ├── service/InventoryService.java   ◄── Idempotent reserve + release
        ├── config/DataSeeder.java          ◄── Seeds test products on startup
        ├── model/
        │   ├── entity/Product.java         ◄── @Version optimistic locking
        │   ├── entity/Reservation.java
        │   ├── enums/ReservationStatus.java
        │   └── dto/InventoryDtos.java
        └── repository/Repositories.java
```

---

## How to Run

### Prerequisites
- Java 17+
- Maven 3.8+

### Start all three services (separate terminals)

```bash
# Terminal 1 — Inventory Service (seed data loads on startup)
cd inventory-service
mvn spring-boot:run

# Terminal 2 — Payment Service
cd payment-service
mvn spring-boot:run

# Terminal 3 — Order Service
cd order-service
mvn spring-boot:run
```

### H2 Consoles (browser)
| Service   | URL                                        | JDBC URL              |
|-----------|--------------------------------------------|-----------------------|
| Order     | http://localhost:8081/h2-console           | jdbc:h2:mem:orderdb   |
| Payment   | http://localhost:8082/h2-console           | jdbc:h2:mem:paymentdb |
| Inventory | http://localhost:8083/h2-console           | jdbc:h2:mem:inventorydb |

---

## Test Scenarios (curl)

### 1. Happy Path — order confirmed
```bash
curl -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "CUST-001",
    "productId": "PROD-001",
    "quantity": 2,
    "amount": 199.99
  }'

# Expected: 201 Created
# {
#   "orderId": "...",
#   "status": "CONFIRMED",
#   "message": "Order placed successfully"
# }
```

### 2. Out of Stock — Saga compensation triggered
```bash
# PROD-004 has 0 stock (seeded out-of-stock)
curl -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "CUST-001",
    "productId": "PROD-004",
    "quantity": 1,
    "amount": 49.99
  }'

# Expected: 422 Unprocessable Entity
# Saga flow:
#   1. Order PENDING
#   2. Payment CHARGED (PAY-xxx created in payment DB)
#   3. Inventory FAILS (out of stock)
#   4. COMPENSATION: Payment refunded automatically
#   5. Order FAILED
```

### 3. Insufficient Funds — payment rejected
```bash
curl -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "CUST-001",
    "productId": "PROD-001",
    "quantity": 1,
    "amount": 99999.00
  }'

# Expected: 422 — payment rejected, no compensation needed (nothing was charged)
```

### 4. Simulate Circuit Breaker — stop Payment Service, then place order
```bash
# Stop payment-service (Ctrl+C in Terminal 2), then:
curl -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "CUST-001",
    "productId": "PROD-001",
    "quantity": 1,
    "amount": 99.99
  }'

# Expected: Immediate failure (no hang) — fallback fires
# After 5+ failures: circuit trips to OPEN state
# Check circuit state:
curl http://localhost:8081/actuator/health
```

### 5. Check Circuit Breaker state via Actuator
```bash
curl http://localhost:8081/actuator/health | jq .

# Look for:
# "circuitBreakers": {
#   "payment-service": { "status": "OPEN" | "CLOSED" | "HALF_OPEN" },
#   "inventory-service": { "status": "CLOSED" }
# }
```

### 6. Get order details
```bash
curl http://localhost:8081/api/orders/{orderId}
```

### 7. Get all orders for a customer
```bash
curl http://localhost:8081/api/orders/customer/CUST-001
```

---

## Seeded Products (Inventory Service)

| Product ID | Name        | Stock | Use For                     |
|------------|-------------|-------|-----------------------------|
| PROD-001   | Laptop      | 50    | Happy path                  |
| PROD-002   | Smartphone  | 100   | Happy path                  |
| PROD-003   | Headphones  | 5     | Low stock / concurrency test|
| PROD-004   | Keyboard    | 0     | Out-of-stock / compensation |

---

## Circuit Breaker Configuration Reference

```yaml
# In order-service application.properties
resilience4j:
  circuitbreaker:
    instances:
      payment-service:
        sliding-window-size: 10           # Watch last 10 calls
        minimum-number-of-calls: 5        # Need 5 calls before evaluating
        failure-rate-threshold: 50        # Open if ≥50% fail
        slow-call-duration-threshold: 2s  # Calls >2s count as slow
        slow-call-rate-threshold: 80      # Open if ≥80% are slow
        wait-duration-in-open-state: 10s  # Stay OPEN for 10s
        permitted-number-of-calls-in-half-open-state: 3  # Probe with 3 calls
```

---

## Saga State Transitions

```
PENDING
  └─► PAYMENT_PROCESSING
        ├─► [payment fails]   ──────────────────────► FAILED
        └─► INVENTORY_RESERVING
              ├─► [inventory fails] ──► COMPENSATING ─► FAILED
              └─► CONFIRMED
```

---

## Key Design Decisions

| Decision | Rationale |
|---|---|
| Orchestration (not choreography) | Simpler to trace, debug, and reason about without Kafka |
| Idempotency keys = orderId | Prevents double-charge/reserve on saga retry |
| @Version on Product | Prevents overselling under concurrent order placement |
| Fallback returns structured DTO | Saga reads `.status` field cleanly — no try/catch sprawl |
| Compensation never throws | Failed compensation is logged for manual reconciliation, not re-thrown |
| State persisted before each remote call | Crash recovery: always know which step completed |

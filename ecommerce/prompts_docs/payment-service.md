# Payment Service — Design & Implementation Guide

## Table of Contents
1. [Overview](#overview)
2. [Architecture Decision — Choreography Saga](#architecture-decision--choreography-saga)
3. [Request Flow](#request-flow)
4. [Project Structure](#project-structure)
5. [Kafka Events](#kafka-events)
6. [Payment Method Routing](#payment-method-routing)
7. [Database Schema](#database-schema)
8. [API Endpoints](#api-endpoints)
9. [Implementation Steps](#implementation-steps)
   - [Phase 1 — Order Service Changes](#phase-1--order-service-changes)
   - [Phase 2 — Payment Service Skeleton](#phase-2--payment-service-skeleton)
   - [Phase 3 — Gateway Integration](#phase-3--gateway-integration)
10. [Payment Gateway Options](#payment-gateway-options)
11. [Configuration](#configuration)
12. [Kubernetes Deployment](#kubernetes-deployment)
13. [Error Handling & Compensation](#error-handling--compensation)
14. [Testing the Saga](#testing-the-saga)

---

## Overview

The payment service is a standalone Spring Boot microservice that processes payments for orders placed in the ecommerce platform. It is deliberately decoupled from the order-service using the **Choreography-based Saga pattern** over Kafka — neither service makes direct HTTP calls to the other.

**Port:** `2030`  
**Database:** MySQL — `payment_db`  
**Messaging:** Kafka (consumes `order.events`, publishes `payment.events`)  
**Supported gateways:** Razorpay (primary), Stripe (international), COD (no gateway)

---

## Architecture Decision — Choreography Saga

### Why not a direct Feign call from order-service?

| Concern | Feign (tight coupling) | Kafka Saga (chosen) |
|---|---|---|
| Payment service down | Order placement fails entirely | Order persists; payment retried on recovery |
| Circuit breaker opens | All orders blocked | Orders queue up; processed when CB resets |
| Adding a new gateway | Change order-service | Change only payment-service |
| Retry logic | Tangled with order transaction | Isolated in payment-service |
| Audit trail | None | Full event log in Kafka + outbox |

The existing outbox pattern in order-service already publishes `ORDER_CREATED` to Kafka. Payment-service plugs into this with zero changes to the order creation flow.

---

## Request Flow

```
Client
  │  POST /api/v1/orders  (paymentMethod: CASH_ON_DELIVERY | UPI | CARD | ...)
  ▼
API Gateway
  │  forwards to order-service
  ▼
Order Service
  │  1. Validates user + products (Feign → user-service, product-service)
  │  2. Deducts stock
  │  3. Saves Order  →  status: PENDING, paymentStatus: PENDING
  │  4. Writes ORDER_CREATED to outbox  ──────────────────────────────────────┐
  │  5. Returns 201 to client (order confirmed as received)                    │
  ▼                                                                             │ Kafka
Client sees order in PENDING state                                              │
                                                                                ▼
                                                              Payment Service
                                                              │  consumes ORDER_CREATED
                                                              │  routes by paymentMethod:
                                                              │    COD        → auto-confirm
                                                              │    UPI/CARD   → call Razorpay
                                                              │    NET_BANKING → call gateway
                                                              │
                                                              │  saves Payment record
                                                              │  publishes PAYMENT_COMPLETED
                                                              │    or PAYMENT_FAILED  ─────────┐
                                                                                                │ Kafka
                                                                                                ▼
                                                              Order Service
                                                                PAYMENT_COMPLETED:
                                                                  → status: CONFIRMED
                                                                  → paymentStatus: PAID
                                                                  → confirmedAt: now()

                                                                PAYMENT_FAILED:
                                                                  → status: PAYMENT_FAILED
                                                                  → paymentStatus: FAILED
                                                                  → triggers stock restore
```

---

## Project Structure

```
services/payment-service/
├── Dockerfile
├── pom.xml
└── src/main/java/com/payment/
    ├── PaymentServiceApplication.java
    │
    ├── config/
    │   ├── SecurityConfig.java          — JWT resource server + GatewaySecretFilter
    │   ├── KeycloakJwtConverter.java    — copy from user-service (role mapping)
    │   └── KafkaConfig.java             — topic declarations
    │
    ├── controller/
    │   └── PaymentController.java       — GET /api/v1/payments/{orderId}
    │
    ├── service/
    │   └── PaymentService.java          — orchestrates gateway + event publishing
    │
    ├── gateway/
    │   ├── PaymentGateway.java          — interface
    │   ├── RazorpayGateway.java         — Razorpay SDK integration
    │   ├── StripeGateway.java           — Stripe SDK integration
    │   └── CodGateway.java              — auto-confirms, no external call
    │
    ├── kafka/
    │   ├── event/
    │   │   ├── OrderEvent.java          — consumed: ORDER_CREATED payload
    │   │   └── PaymentEvent.java        — produced: PAYMENT_COMPLETED / FAILED
    │   ├── OrderEventConsumer.java      — @KafkaListener on order.events
    │   └── PaymentEventPublisher.java   — publishes to payment.events
    │
    ├── entity/
    │   ├── Payment.java                 — JPA entity (payment_db)
    │   └── OutboxEvent.java             — transactional outbox (same pattern as order-service)
    │
    ├── repository/
    │   ├── PaymentRepository.java
    │   └── OutboxEventRepository.java
    │
    ├── dto/
    │   ├── PaymentResponse.java
    │   └── GatewayChargeResult.java     — result from Razorpay/Stripe
    │
    ├── enums/
    │   ├── PaymentStatus.java           — PENDING, PROCESSING, COMPLETED, FAILED, REFUNDED
    │   └── GatewayProvider.java        — RAZORPAY, STRIPE, COD, MOCK
    │
    ├── exception/
    │   ├── PaymentGatewayException.java
    │   └── GlobalExceptionHandler.java
    │
    └── scheduler/
        └── OutboxPoller.java            — relays outbox events to Kafka (same as order-service)

src/main/resources/
    ├── application.properties
    ├── logback-spring.xml
    └── db/migration/
        └── V1__create_payment_table.sql
```

---

## Kafka Events

### Topics

| Topic | Producer | Consumer | Purpose |
|---|---|---|---|
| `order.events` | order-service | payment-service | Carries ORDER_CREATED (and other order lifecycle events) |
| `payment.events` | payment-service | order-service | Carries PAYMENT_COMPLETED / PAYMENT_FAILED |

### ORDER_CREATED payload (consumed by payment-service)

```json
{
  "eventType": "ORDER_CREATED",
  "orderId": 42,
  "orderNumber": "ORD-2026-00042",
  "userId": 4,
  "userEmail": "user@example.com",
  "paymentMethod": "UPI",
  "totalAmount": 1180.00,
  "currency": "INR",
  "timestamp": "2026-06-02T15:00:00Z"
}
```

### PAYMENT_COMPLETED payload (published by payment-service)

```json
{
  "eventType": "PAYMENT_COMPLETED",
  "orderId": 42,
  "paymentId": 7,
  "gatewayTransactionId": "pay_QXyz123",
  "gatewayProvider": "RAZORPAY",
  "amount": 1180.00,
  "currency": "INR",
  "timestamp": "2026-06-02T15:00:05Z"
}
```

### PAYMENT_FAILED payload (published by payment-service)

```json
{
  "eventType": "PAYMENT_FAILED",
  "orderId": 42,
  "paymentId": 7,
  "reason": "Insufficient funds",
  "gatewayProvider": "RAZORPAY",
  "timestamp": "2026-06-02T15:00:10Z"
}
```

---

## Payment Method Routing

```
ORDER_CREATED received
        │
        ├─── CASH_ON_DELIVERY ──► CodGateway.confirm()
        │                              │
        │                              └─► publish PAYMENT_COMPLETED immediately
        │                                  (no gateway API call needed)
        │
        ├─── UPI ─────────────►
        ├─── CARD ────────────►  RazorpayGateway.charge(orderId, amount)
        ├─── CREDIT_CARD ─────►       │
        ├─── DEBIT_CARD ──────►       ├─► success → publish PAYMENT_COMPLETED
        │                              └─► failure → publish PAYMENT_FAILED
        │
        └─── NET_BANKING ─────►  RazorpayGateway.charge(orderId, amount)
             WALLET ──────────►  (or StripeGateway if configured)
```

`PaymentGateway` interface:

```java
public interface PaymentGateway {
    GatewayChargeResult charge(Long orderId, BigDecimal amount, String currency, PaymentMethod method);
    boolean supports(PaymentMethod method);
}
```

---

## Database Schema

### `payment_db.payments`

```sql
CREATE TABLE payments (
    id                   BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    version              BIGINT       NOT NULL DEFAULT 0,
    order_id             BIGINT       NOT NULL UNIQUE,
    order_number         VARCHAR(30)  NOT NULL,
    user_id              BIGINT       NOT NULL,
    payment_method       VARCHAR(30)  NOT NULL,
    gateway_provider     VARCHAR(30),
    gateway_txn_id       VARCHAR(200),
    amount               DECIMAL(12,2) NOT NULL,
    currency             VARCHAR(10)  NOT NULL DEFAULT 'INR',
    status               VARCHAR(30)  NOT NULL DEFAULT 'PENDING',
    failure_reason       TEXT,
    retry_count          INT          NOT NULL DEFAULT 0,
    created_at           DATETIME     NOT NULL,
    updated_at           DATETIME,
    CONSTRAINT ux_order_id UNIQUE (order_id)
);
```

### `payment_db.payment_outbox`

```sql
CREATE TABLE payment_outbox (
    id           BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    payment_id   BIGINT      NOT NULL,
    event_type   VARCHAR(50) NOT NULL,
    payload      TEXT,
    status       VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count  INT         NOT NULL DEFAULT 0,
    created_at   DATETIME    NOT NULL,
    processed_at DATETIME
);
```

---

## API Endpoints

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/api/v1/payments/{orderId}` | USER / ADMIN | Get payment status for an order |
| `GET` | `/api/v1/payments/admin` | ADMIN | List all payments (paginated) |
| `POST` | `/api/v1/payments/{orderId}/retry` | USER / ADMIN | Manually retry a failed payment |

The payment-service does NOT expose a POST endpoint for creating payments — creation is driven exclusively by the `ORDER_CREATED` Kafka event.

---

## Implementation Steps

### Phase 1 — Order Service Changes

Order-service needs to consume `payment.events` and update order status accordingly.

**1. Add `PaymentEvent` DTO**

```
services/order-service/src/main/java/com/order/kafka/event/PaymentEvent.java
```

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentEvent {
    private String  eventType;          // PAYMENT_COMPLETED | PAYMENT_FAILED
    private Long    orderId;
    private Long    paymentId;
    private String  gatewayTransactionId;
    private String  reason;             // populated on FAILED
}
```

**2. Add `PaymentEventConsumer`**

```
services/order-service/src/main/java/com/order/kafka/PaymentEventConsumer.java
```

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventConsumer {

    private final OrderService orderService;

    @KafkaListener(topics = "payment.events", groupId = "order-service-payment")
    public void onPaymentEvent(PaymentEvent event) {
        log.info("Received payment event: type={} orderId={}", event.getEventType(), event.getOrderId());

        switch (event.getEventType()) {
            case "PAYMENT_COMPLETED" -> orderService.confirmPayment(
                    event.getOrderId(),
                    new PaymentConfirmRequest(event.getGatewayTransactionId()),
                    "payment-service");
            case "PAYMENT_FAILED" -> orderService.markPaymentFailed(
                    event.getOrderId(), event.getReason(), "payment-service");
            default -> log.warn("Unknown payment event type: {}", event.getEventType());
        }
    }
}
```

**3. Add `markPaymentFailed()` to `OrderService`**

```java
@Transactional
@Caching(evict = {
    @CacheEvict(value = "orders",     key = "#id"),
    @CacheEvict(value = "ordersPage", allEntries = true)
})
public OrderResponse markPaymentFailed(Long id, String reason, String changedBy) {
    Order order = findOrderById(id);
    validateTransition(order, OrderStatus.PAYMENT_FAILED);
    order.setStatus(OrderStatus.PAYMENT_FAILED);
    order.setPaymentStatus(PaymentStatus.FAILED);
    Order saved = orderRepository.saveAndFlush(order);
    appendHistory(saved, OrderStatus.PENDING, OrderStatus.PAYMENT_FAILED, reason, changedBy);
    saveToOutbox(saved.getId(), "ORDER_PAYMENT_FAILED", reason);
    // Restore stock — same compensation path as cancellation
    saved.getItems().forEach(item ->
        self.restoreProductStock(saved.getId(), item.getProductId(), item.getQuantity()));
    return orderMapper.toResponse(saved);
}
```

**4. Update `application.properties` — add consumer for `payment.events`**

```properties
# Payment event consumer
spring.kafka.consumer.group-id=order-service-payment
spring.kafka.consumer.properties.spring.json.trusted.packages=com.order.kafka.event,com.payment.kafka.event
```

**5. Ensure `ORDER_CREATED` event carries `paymentMethod` and `totalAmount`**

Check `OutboxEvent` / `OrderEventPublisher` in order-service to confirm these fields are included in the event payload. Payment-service needs them to route and charge correctly.

---

### Phase 2 — Payment Service Skeleton

Create `services/payment-service/` as a new Spring Boot project.

**`pom.xml` key dependencies:**

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.kafka</groupId>
        <artifactId>spring-kafka</artifactId>
    </dependency>
    <dependency>
        <groupId>com.mysql</groupId>
        <artifactId>mysql-connector-j</artifactId>
    </dependency>
    <dependency>
        <groupId>org.flywaydb</groupId>
        <artifactId>flyway-mysql</artifactId>
    </dependency>

    <!-- Razorpay SDK (Phase 3) -->
    <!-- <dependency>
        <groupId>com.razorpay</groupId>
        <artifactId>razorpay-java</artifactId>
        <version>1.4.5</version>
    </dependency> -->
</dependencies>
```

**`OrderEventConsumer.java` — the entry point:**

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventConsumer {

    private final PaymentService paymentService;

    @KafkaListener(topics = "order.events", groupId = "payment-service")
    public void onOrderEvent(OrderEvent event) {
        if (!"ORDER_CREATED".equals(event.getEventType())) return;
        log.info("Processing payment for orderId={} method={}", event.getOrderId(), event.getPaymentMethod());
        paymentService.processPayment(event);
    }
}
```

**`PaymentService.java` — core logic:**

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final List<PaymentGateway>       gateways;
    private final PaymentRepository          paymentRepository;
    private final PaymentEventPublisher      eventPublisher;

    @Transactional
    public void processPayment(OrderEvent event) {
        // Idempotency: skip if already processed
        if (paymentRepository.existsByOrderId(event.getOrderId())) {
            log.info("Payment already processed for orderId={}", event.getOrderId());
            return;
        }

        Payment payment = Payment.builder()
                .orderId(event.getOrderId())
                .orderNumber(event.getOrderNumber())
                .userId(event.getUserId())
                .paymentMethod(event.getPaymentMethod())
                .amount(event.getTotalAmount())
                .currency("INR")
                .status(PaymentStatus.PROCESSING)
                .build();
        payment = paymentRepository.saveAndFlush(payment);

        PaymentGateway gateway = gateways.stream()
                .filter(g -> g.supports(event.getPaymentMethod()))
                .findFirst()
                .orElseThrow(() -> new PaymentGatewayException(
                        "No gateway configured for method: " + event.getPaymentMethod()));

        try {
            GatewayChargeResult result = gateway.charge(
                    event.getOrderId(), event.getTotalAmount(), "INR", event.getPaymentMethod());

            payment.setStatus(PaymentStatus.COMPLETED);
            payment.setGatewayProvider(result.getProvider());
            payment.setGatewayTxnId(result.getTransactionId());
            paymentRepository.save(payment);

            eventPublisher.publishCompleted(payment);
            log.info("Payment completed for orderId={} txn={}", event.getOrderId(), result.getTransactionId());

        } catch (Exception ex) {
            log.error("Payment failed for orderId={}: {}", event.getOrderId(), ex.getMessage());
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(ex.getMessage());
            paymentRepository.save(payment);

            eventPublisher.publishFailed(payment, ex.getMessage());
        }
    }
}
```

---

### Phase 3 — Gateway Integration

**`CodGateway.java` — implement first (no external dependency):**

```java
@Component
public class CodGateway implements PaymentGateway {

    @Override
    public GatewayChargeResult charge(Long orderId, BigDecimal amount, String currency, PaymentMethod method) {
        return GatewayChargeResult.builder()
                .provider(GatewayProvider.COD)
                .transactionId("COD-" + orderId)
                .success(true)
                .build();
    }

    @Override
    public boolean supports(PaymentMethod method) {
        return method == PaymentMethod.CASH_ON_DELIVERY;
    }
}
```

**`RazorpayGateway.java` — add after Razorpay account setup:**

```java
@Component
@ConditionalOnProperty(name = "payment.gateway.razorpay.enabled", havingValue = "true")
public class RazorpayGateway implements PaymentGateway {

    private final RazorpayClient razorpayClient;
    private final Set<PaymentMethod> SUPPORTED = Set.of(
            PaymentMethod.UPI, PaymentMethod.CARD, PaymentMethod.CREDIT_CARD,
            PaymentMethod.DEBIT_CARD, PaymentMethod.NET_BANKING, PaymentMethod.WALLET);

    public RazorpayGateway(
            @Value("${payment.gateway.razorpay.key-id}") String keyId,
            @Value("${payment.gateway.razorpay.key-secret}") String keySecret) throws RazorpayException {
        this.razorpayClient = new RazorpayClient(keyId, keySecret);
    }

    @Override
    public GatewayChargeResult charge(Long orderId, BigDecimal amount, String currency, PaymentMethod method) {
        try {
            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", amount.multiply(BigDecimal.valueOf(100)).intValue()); // paise
            orderRequest.put("currency", currency);
            orderRequest.put("receipt", "order_" + orderId);

            com.razorpay.Order razorpayOrder = razorpayClient.orders.create(orderRequest);
            return GatewayChargeResult.builder()
                    .provider(GatewayProvider.RAZORPAY)
                    .transactionId(razorpayOrder.get("id"))
                    .success(true)
                    .build();

        } catch (RazorpayException ex) {
            throw new PaymentGatewayException("Razorpay error: " + ex.getMessage(), ex);
        }
    }

    @Override
    public boolean supports(PaymentMethod method) {
        return SUPPORTED.contains(method);
    }
}
```

---

## Payment Gateway Options

| Gateway | Best for | SDK | Docs |
|---|---|---|---|
| **Razorpay** | India (UPI, Cards, NetBanking, Wallets) | `com.razorpay:razorpay-java` | razorpay.com/docs |
| **Stripe** | International (Cards, Wallets) | `com.stripe:stripe-java` | stripe.com/docs |
| **PhonePe** | India (UPI-first) | REST API | developer.phonepe.com |
| **Mock** | Local dev / testing | none (auto-confirm all) | — |

For local development without a real gateway account, create a `MockGateway` that auto-confirms every payment after a 500ms delay. Use `@Profile("!prod")` to disable it in production.

---

## Configuration

### `services/payment-service/src/main/resources/application.properties`

```properties
spring.application.name=payment-service
server.port=2030

# ── MySQL ──────────────────────────────────────────────────────────────────────
spring.datasource.url=jdbc:mysql://localhost:30036/payment_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&createDatabaseIfNotExist=true
spring.datasource.username=root
spring.datasource.password=root123

# ── Flyway ─────────────────────────────────────────────────────────────────────
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration
spring.flyway.baseline-on-migrate=false

# ── JPA ────────────────────────────────────────────────────────────────────────
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect

# ── Keycloak JWT ───────────────────────────────────────────────────────────────
spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:30080/realms/microservices-realm/protocol/openid-connect/certs

# ── Gateway secret (shared with all services) ─────────────────────────────────
gateway.internal-secret=gw-secret-change-in-prod

# ── Kafka ──────────────────────────────────────────────────────────────────────
spring.kafka.bootstrap-servers=localhost:9092
spring.kafka.consumer.group-id=payment-service
spring.kafka.consumer.auto-offset-reset=earliest
spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer
spring.kafka.consumer.value-deserializer=org.springframework.kafka.support.serializer.JsonDeserializer
spring.kafka.consumer.properties.spring.json.trusted.packages=com.payment.kafka.event,com.order.kafka.event
spring.kafka.consumer.properties.spring.json.use.type.headers=false
spring.kafka.consumer.properties.spring.json.value.default.type=com.payment.kafka.event.OrderEvent
spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer
spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer
spring.kafka.producer.properties.spring.json.add.type.headers=false
spring.kafka.producer.properties.max.block.ms=3000

# ── Payment Gateways ───────────────────────────────────────────────────────────
payment.gateway.razorpay.enabled=false
payment.gateway.razorpay.key-id=YOUR_RAZORPAY_KEY_ID
payment.gateway.razorpay.key-secret=YOUR_RAZORPAY_KEY_SECRET

# ── Actuator ───────────────────────────────────────────────────────────────────
management.endpoints.web.exposure.include=health,prometheus,metrics,info
management.endpoint.health.show-details=always
management.tracing.sampling.probability=1.0
management.otlp.tracing.endpoint=http://localhost:30418/v1/traces
```

---

## Kubernetes Deployment

### Add to gateway `application.yaml` — new route

```yaml
- id: payment-service
  uri: http://payment-service:2030
  predicates:
    - Path=/api/v1/payments/**
  filters:
    - AddRequestHeader=X-Gateway-Secret, ${gateway.internal-secret}
    - name: CircuitBreaker
      args:
        id: payment-service
        fallbackUri: forward:/fallback/service-unavailable
        statusCodes: [500, 502, 503, 504]
```

### New Kafka topics — add to `KafkaConfig` in any service

```java
@Bean
public NewTopic paymentEventsTopic() {
    return TopicBuilder.name("payment.events")
            .partitions(3)
            .replicas(1)
            .build();
}
```

### K8s manifests needed

```
deployment/k8s/ecommerce-platform/templates/applications/payment-service/
  ├── configmap.yaml      — env vars (same pattern as order-service)
  ├── secret.yaml         — Razorpay key-id and key-secret (base64)
  ├── deployment.yaml     — image: ecommerce/payment-service:latest
  ├── service.yaml        — ClusterIP on port 2030
  └── hpa.yaml            — min 1, max 3 replicas
```

---

## Error Handling & Compensation

### Payment fails after stock deducted

When `PAYMENT_FAILED` is consumed by order-service, `markPaymentFailed()` calls `restoreProductStock()` for every order item. This restores stock via product-service Feign — same compensation path as order cancellation. If product-service is down, `PendingStockRestore` records are saved and retried by `StockCompensationScheduler`.

### Kafka message processing failure

If `OrderEventConsumer.onOrderEvent()` throws, Spring Kafka retries the message according to the consumer's retry configuration. After max retries, the message goes to the dead-letter topic (`order.events.DLT`) for manual inspection.

```properties
# Add to payment-service application.properties
spring.kafka.listener.ack-mode=RECORD
spring.kafka.consumer.max-poll-records=10
```

### Gateway timeout

`RazorpayGateway.charge()` should set a timeout. If the gateway times out, catch the exception, set payment to `FAILED`, and publish `PAYMENT_FAILED`. Order-service will restore stock. The user can retry payment later via `POST /api/v1/payments/{orderId}/retry`.

### Idempotency

`PaymentService.processPayment()` checks `paymentRepository.existsByOrderId()` before processing. If Kafka delivers `ORDER_CREATED` more than once (at-least-once delivery guarantee), duplicate payment attempts are safely skipped.

---

## Testing the Saga

### Step 1 — Validate COD flow (no gateway needed)

1. Place an order with `paymentMethod: CASH_ON_DELIVERY`
2. Check Kafka: `payment.events` should receive `PAYMENT_COMPLETED`
3. Check order status: should transition to `CONFIRMED`

```bash
# Watch order status
kubectl exec -n ecommerce deployment/order-service -- \
  curl -s http://localhost:2029/actuator/health

# Check Kafka messages (Kafka UI at http://localhost:30808)
# Topic: payment.events → should see PAYMENT_COMPLETED message
```

### Step 2 — Validate PAYMENT_FAILED path

Temporarily make `RazorpayGateway.charge()` throw an exception, place an order, and verify:
- Payment record is `FAILED`
- Order status is `PAYMENT_FAILED`
- Stock is restored (check product stock count)

### Step 3 — Enable Razorpay test mode

```properties
payment.gateway.razorpay.enabled=true
payment.gateway.razorpay.key-id=rzp_test_XXXX
payment.gateway.razorpay.key-secret=XXXX
```

Use Razorpay's test card `4111 1111 1111 1111` to simulate successful and failed payments.

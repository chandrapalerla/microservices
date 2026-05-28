# E-Commerce Microservices — Gaps & Improvement Checklist

> Analysis covers: `user-service` · `order-service` · `product-service` · `apigateway` · `serviceregistry`
> Date: 2026-05-28

---

## Table of Contents

1. [Observability](#1-observability)
   - 1.1 [Structured Logging](#11-structured-logging--missing-in-all-services)
   - 1.2 [Distributed Tracing](#12-distributed-tracing--missing-in-all-services)
   - 1.3 [Metrics & Prometheus](#13-metrics--prometheus--missing-in-all-services)
   - 1.4 [Health Checks](#14-health-checks--incomplete)
   - 1.5 [Correlation ID Filter](#15-correlation-id-filter--missing-in-all-services)
2. [API Gateway](#2-api-gateway)
   - 2.1 [Rate Limiting](#21-rate-limiting--missing)
   - 2.2 [Service Discovery Routing](#22-service-discovery-not-used-for-routing)
   - 2.3 [Global Error Handling](#23-global-error-handling--missing)
   - 2.4 [Request/Response Logging](#24-requestresponse-logging-filter--missing)
   - 2.5 [Circuit Breaker at Gateway](#25-circuit-breaker-at-gateway-level--missing)
3. [User Service](#3-user-service)
4. [Order Service](#4-order-service)
5. [Product Service](#5-product-service)
6. [Service Registry](#6-service-registry)
7. [Cross-Cutting Patterns](#7-cross-cutting-missing-patterns)
8. [Security](#8-security-gaps)
9. [Infrastructure & Deployment](#9-infrastructure--deployment-gaps)
10. [Summary Table](#10-summary-table)

---

## 1. Observability

### 1.1 Structured Logging — Missing in ALL services

**Problem:**
No service has structured logging. All rely on default Spring Boot plain-text console output.

**What is missing:**
- No `logback-spring.xml` in any service
- No JSON/Logstash-encoded log format for log aggregation (ELK / Loki)
- No MDC (Mapped Diagnostic Context) — trace ID, span ID, user ID, request ID not in log lines
- No log level separation by Spring profile (`dev` vs `prod`)
- No `logstash-logback-encoder` dependency

**Files to create per service:**
```
src/main/resources/logback-spring.xml
```

**Content template:**
```xml
<configuration>
  <springProfile name="prod">
    <appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
      <encoder class="net.logstash.logback.encoder.LogstashEncoder">
        <includeMdcKeyName>traceId</includeMdcKeyName>
        <includeMdcKeyName>spanId</includeMdcKeyName>
        <includeMdcKeyName>requestId</includeMdcKeyName>
        <includeMdcKeyName>userId</includeMdcKeyName>
        <includeMdcKeyName>service</includeMdcKeyName>
      </encoder>
    </appender>
    <root level="INFO"><appender-ref ref="JSON" /></root>
  </springProfile>

  <springProfile name="default,dev">
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
      <encoder>
        <pattern>%clr(%d{HH:mm:ss.SSS}){faint} %clr(%-5level) %clr([%X{requestId}]){cyan} %clr(%logger{36}){blue} - %msg%n</pattern>
      </encoder>
    </appender>
    <root level="DEBUG"><appender-ref ref="CONSOLE" /></root>
  </springProfile>
</configuration>
```

**Dependency to add in every `pom.xml`:**
```xml
<dependency>
  <groupId>net.logstash.logback</groupId>
  <artifactId>logstash-logback-encoder</artifactId>
  <version>7.4</version>
</dependency>
```

**`application.properties` additions:**
```properties
logging.pattern.correlation=[${spring.application.name:},%X{traceId:-},%X{spanId:-}]
logging.level.com.yourpackage=INFO
logging.level.org.springframework.security=WARN
logging.level.org.hibernate.SQL=DEBUG      # dev only
logging.level.org.hibernate.type=TRACE     # dev only
```

**Service logger usage pattern (all controllers/services):**
```java
private static final Logger log = LoggerFactory.getLogger(OrderService.class);

// Structured log with MDC fields already populated by filter:
log.info("Order created orderId={} userId={} total={}", order.getId(), order.getUserId(), order.getTotalAmount());
log.warn("Stock low productId={} remaining={}", productId, remaining);
log.error("Payment confirmation failed orderId={}", orderId, exception);
```

---

### 1.2 Distributed Tracing — Missing in ALL services

**Problem:**
Cannot correlate a single user request across Gateway → Order-Service → Product-Service → User-Service. No trace/span ID is propagated or generated.

**What is missing:**
- No `micrometer-tracing-bridge-brave` or OpenTelemetry bridge in any service
- No `zipkin-reporter-brave` dependency
- No trace context propagation in Feign calls (`FeignSecurityConfig` only propagates JWT, not trace headers)
- Cannot use Zipkin / Tempo / Jaeger to view distributed call graphs

**Dependencies to add in every `pom.xml`:**
```xml
<!-- Micrometer tracing (Spring Boot 3.x) -->
<dependency>
  <groupId>io.micrometer</groupId>
  <artifactId>micrometer-tracing-bridge-brave</artifactId>
</dependency>
<dependency>
  <groupId>io.zipkin.reporter2</groupId>
  <artifactId>zipkin-reporter-brave</artifactId>
</dependency>
<!-- Feign trace propagation -->
<dependency>
  <groupId>io.github.openfeign</groupId>
  <artifactId>feign-micrometer</artifactId>
</dependency>
```

**`application.properties` additions (every service):**
```properties
management.tracing.sampling.probability=1.0
management.zipkin.tracing.endpoint=http://localhost:9411/api/v2/spans
```

**`order-service/application.properties` additional Feign trace propagation:**
```properties
spring.cloud.openfeign.micrometer.enabled=true
```

**Kubernetes deployment: add Zipkin container to `deployment/k8s/`:**
```yaml
# deployment/k8s/observability.yml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: zipkin
spec:
  replicas: 1
  template:
    spec:
      containers:
        - name: zipkin
          image: openzipkin/zipkin:latest
          ports:
            - containerPort: 9411
```

---

### 1.3 Metrics & Prometheus — Missing in ALL services

**Problem:**
`user-service`, `order-service`, and `product-service` have no Actuator or Prometheus endpoint. Only the gateway exposes `/actuator/health`. No Grafana dashboards are possible.

**What is missing:**
- `spring-boot-starter-actuator` not in `user-service`, `order-service`, `product-service` `pom.xml`
- No Micrometer Prometheus registry in any service
- No custom business metrics (orders per minute, revenue, cache hit rate, stock deductions)
- No JVM, HikariCP, Hazelcast, Kafka producer metrics exposed

**Dependency to add in every `pom.xml`:**
```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
  <groupId>io.micrometer</groupId>
  <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

**`application.properties` additions (every service):**
```properties
management.endpoints.web.exposure.include=health,info,prometheus,metrics,caches,env
management.endpoint.health.show-details=when-authorized
management.endpoint.health.probes.enabled=true
management.metrics.tags.application=${spring.application.name}
management.metrics.tags.environment=${spring.profiles.active:default}
management.metrics.distribution.percentiles-histogram.http.server.requests=true
management.metrics.distribution.slo.http.server.requests=50ms,100ms,200ms,500ms,1s
```

**Custom business metrics to add in `OrderService.java`:**
```java
@Autowired
private MeterRegistry meterRegistry;

// In create():
meterRegistry.counter("orders.placed.total",
    "payment_method", order.getPaymentMethod().name(),
    "status", "success"
).increment();

meterRegistry.gauge("orders.total.value", order.getTotalAmount().doubleValue());

// In shipOrder():
meterRegistry.timer("order.fulfillment.duration")
    .record(Duration.between(order.getCreatedAt(), LocalDateTime.now()));
```

**Custom business metrics to add in `ProductService.java`:**
```java
// In deductStock():
meterRegistry.counter("stock.deductions.total", "product_id", id.toString()).increment();

// When stock goes low:
meterRegistry.gauge("product.stock.quantity",
    Tags.of("product_id", id.toString(), "sku", product.getSku()),
    product.getStockQuantity()
);
```

---

### 1.4 Health Checks — Incomplete

**Problem:**
Services expose no custom health indicators. Kubernetes liveness/readiness probes have nothing meaningful to check. A service can appear UP while its Kafka producer or downstream dependency is broken.

**What is missing:**

| Service | Missing Health Indicator |
|---------|--------------------------|
| `order-service` | Kafka producer health, downstream Feign service reachability |
| `product-service` | Kafka producer health |
| `user-service` | DB connection pool saturation indicator |
| `apigateway` | Individual downstream service health |

**File to create in `order-service`:**
```java
// src/main/java/com/order/health/KafkaHealthIndicator.java
@Component
public class KafkaHealthIndicator implements HealthIndicator {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public Health health() {
        try {
            kafkaTemplate.send("health-ping", "ping").get(3, TimeUnit.SECONDS);
            return Health.up().withDetail("broker", "reachable").build();
        } catch (Exception e) {
            return Health.down()
                .withDetail("broker", "unreachable")
                .withException(e)
                .build();
        }
    }
}
```

**File to create in `apigateway`:**
```java
// src/main/java/com/apigateway/health/DownstreamHealthIndicator.java
@Component
public class DownstreamHealthIndicator implements HealthIndicator {

    private final RestClient restClient;

    @Override
    public Health health() {
        Map<String, String> status = new LinkedHashMap<>();
        checkService("user-service",    "http://localhost:2026/actuator/health", status);
        checkService("order-service",   "http://localhost:2029/actuator/health", status);
        checkService("product-service", "http://localhost:2028/actuator/health", status);
        boolean allUp = status.values().stream().allMatch("UP"::equals);
        return allUp ? Health.up().withDetails(status).build()
                     : Health.down().withDetails(status).build();
    }
}
```

---

### 1.5 Correlation ID Filter — Missing in ALL services

**Problem:**
Requests have no correlation ID. When a bug is reported, it is impossible to find the specific request's log entries across 4 services without scanning by timestamp only.

**What is missing:**
- No `X-Correlation-ID` generation at gateway
- No `X-Correlation-ID` propagation from gateway to downstream services
- No MDC population with correlation ID in any service
- No `X-Correlation-ID` on HTTP responses

**File to create in `apigateway`:**
```java
// src/main/java/com/apigateway/filter/CorrelationIdFilter.java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String CORRELATION_HEADER = "X-Correlation-ID";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String correlationId = Optional.ofNullable(request.getHeader(CORRELATION_HEADER))
            .filter(h -> !h.isBlank())
            .orElse(UUID.randomUUID().toString());

        MDC.put("requestId", correlationId);
        MDC.put("method",    request.getMethod());
        MDC.put("path",      request.getRequestURI());
        response.setHeader(CORRELATION_HEADER, correlationId);

        try {
            chain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
```

**File to create in every downstream service:**
```java
// Same pattern — reads X-Correlation-ID from incoming request, populates MDC
// Adds userId from JWT SecurityContext after authentication
@Override
protected void doFilterInternal(...) {
    String correlationId = request.getHeader("X-Correlation-ID");
    MDC.put("requestId", correlationId);

    // Extract userId from SecurityContext after JWT filter runs
    // Place after UsernamePasswordAuthenticationFilter in filter chain
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth instanceof JwtAuthenticationToken jwt) {
        MDC.put("userId", jwt.getName());
    }
    try {
        chain.doFilter(request, response);
    } finally {
        MDC.clear();
    }
}
```

**FeignSecurityConfig update in `order-service` to propagate correlation ID:**
```java
@Bean
public RequestInterceptor correlationIdInterceptor() {
    return template -> {
        String correlationId = MDC.get("requestId");
        if (correlationId != null) {
            template.header("X-Correlation-ID", correlationId);
        }
    };
}
```

---

## 2. API Gateway

### 2.1 Rate Limiting — Missing

**Problem:**
No rate limiting exists on any endpoint. A single client can flood `/api/v1/orders` (POST) causing order-service to hammer product-service with stock deductions, potentially causing a cascade failure.

**What is missing:**
- No per-user or per-IP rate limiting
- No 429 Too Many Requests response
- No `Retry-After` header on throttled responses
- No different limits for authenticated vs anonymous users

**Dependency to add in `apigateway/pom.xml`:**
```xml
<dependency>
    <groupId>com.bucket4j</groupId>
    <artifactId>bucket4j-core</artifactId>
    <version>8.10.1</version>
</dependency>
<!-- For distributed rate limiting across gateway replicas: -->
<dependency>
    <groupId>com.bucket4j</groupId>
    <artifactId>bucket4j-redis</artifactId>
    <version>8.10.1</version>
</dependency>
```

**File to create:**
```java
// src/main/java/com/apigateway/filter/RateLimitFilter.java
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    // Per authenticated user: 100 req/min for order creation
    // Per IP: 300 req/min globally
    // Anonymous: 60 req/min

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest req, ...) {
        String key = resolveKey(req); // userId from JWT or IP
        Bucket bucket = buckets.computeIfAbsent(key, this::newBucket);

        if (bucket.tryConsume(1)) {
            chain.doFilter(req, res);
        } else {
            res.setStatus(429);
            res.setHeader("Retry-After", "60");
            res.setHeader("X-RateLimit-Limit", "100");
            res.getWriter().write("{\"error\":\"Too many requests\"}");
        }
    }

    private Bucket newBucket(String key) {
        return Bucket.builder()
            .addLimit(Bandwidth.classic(100, Refill.greedy(100, Duration.ofMinutes(1))))
            .build();
    }
}
```

---

### 2.2 Service Discovery Not Used for Routing

**Problem:**
`GatewayConfig.java` routes to `http://localhost:2026`, `http://localhost:2029`, `http://localhost:2028`. This hardcoding:
- Breaks when services run on different ports or hosts (Docker, Kubernetes)
- Does not use Eureka for load balancing across multiple instances
- Requires redeployment of the gateway whenever a service moves

**Current (broken for multi-instance):**
```java
.uri("http://localhost:2026")
```

**Fix Option A — Spring Cloud Gateway MVC with LoadBalancer RestClient:**
```java
// GatewayConfig.java
@Bean
public RouterFunction<ServerResponse> userServiceRoute(LoadBalancerClient lbClient) {
    return RouterFunctions.route()
        .route(path("/api/v1/users/**").or(path("/api/v1/auth/**")),
            HandlerFunctions.http(resolveUri(lbClient, "user-service")))
        .filter(addGatewaySecretHeader())
        .build();
}

private String resolveUri(LoadBalancerClient lbClient, String serviceId) {
    ServiceInstance instance = lbClient.choose(serviceId);
    return instance.getUri().toString();
}
```

**Fix Option B — use `spring.cloud.gateway.mvc.routes` in YAML with lb:// support (Spring Cloud 2023+):**
```yaml
spring:
  cloud:
    gateway:
      mvc:
        routes:
          - id: user-service
            uri: lb://user-service
            predicates:
              - Path=/api/v1/users/**, /api/v1/auth/**
            filters:
              - AddRequestHeader=X-Gateway-Secret, ${gateway.internal-secret}
```

**`application.yaml` properties to add:**
```yaml
spring:
  cloud:
    loadbalancer:
      ribbon:
        enabled: false
      cache:
        ttl: 30s
```

---

### 2.3 Global Error Handling — Missing

**Problem:**
When a downstream service is unreachable or returns 5xx, the gateway returns a raw Spring error JSON with a stack trace, exposing internal implementation details.

**What is missing:**
- No `@RestControllerAdvice` in gateway
- No friendly error message when upstream is down
- No correlation ID in error response
- Stack traces potentially exposed in production

**File to create:**
```java
// src/main/java/com/apigateway/exception/GatewayExceptionHandler.java
@RestControllerAdvice
public class GatewayExceptionHandler {

    @ExceptionHandler(HttpServerErrorException.ServiceUnavailable.class)
    public ResponseEntity<ProblemDetail> handleServiceDown(HttpServletRequest req) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.SERVICE_UNAVAILABLE);
        pd.setTitle("Service Temporarily Unavailable");
        pd.setDetail("A downstream service is not responding. Please try again.");
        pd.setProperty("requestId", MDC.get("requestId"));
        pd.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(503).body(pd);
    }

    @ExceptionHandler(HttpClientErrorException.TooManyRequests.class)
    public ResponseEntity<ProblemDetail> handleRateLimit() { ... }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGeneric(Exception ex) {
        // Log full stack trace internally, return sanitized message externally
        log.error("Unhandled gateway error requestId={}", MDC.get("requestId"), ex);
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        pd.setTitle("Internal Gateway Error");
        pd.setDetail("An unexpected error occurred.");
        pd.setProperty("requestId", MDC.get("requestId"));
        return ResponseEntity.status(500).body(pd);
    }
}
```

---

### 2.4 Request/Response Logging Filter — Missing

**Problem:**
No audit trail of requests passing through the gateway. Impossible to debug "who called what endpoint when" after the fact.

**File to create:**
```java
// src/main/java/com/apigateway/filter/GatewayAccessLogFilter.java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class GatewayAccessLogFilter extends OncePerRequestFilter {

    private static final Logger ACCESS_LOG = LoggerFactory.getLogger("ACCESS_LOG");

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain) throws IOException, ServletException {
        long start = System.currentTimeMillis();
        try {
            chain.doFilter(req, res);
        } finally {
            long duration = System.currentTimeMillis() - start;
            ACCESS_LOG.info("method={} path={} status={} duration={}ms userId={} requestId={}",
                req.getMethod(), req.getRequestURI(), res.getStatus(),
                duration, MDC.get("userId"), MDC.get("requestId"));
        }
    }
}
```

---

### 2.5 Circuit Breaker at Gateway Level — Missing

**Problem:**
Resilience4j is only present in `order-service` (for its Feign calls to other services). The gateway itself has no circuit breaker — if `user-service` is completely down, all threads calling `/api/v1/users/**` will block until timeout, eventually causing thread pool exhaustion in the gateway.

**Dependency to add in `apigateway/pom.xml`:**
```xml
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot3</artifactId>
</dependency>
```

**`application.yaml` additions:**
```yaml
resilience4j:
  circuitbreaker:
    instances:
      user-service:
        failure-rate-threshold: 50
        slow-call-rate-threshold: 80
        slow-call-duration-threshold: 3s
        wait-duration-in-open-state: 15s
        sliding-window-size: 10
        permitted-number-of-calls-in-half-open-state: 3
      order-service:
        failure-rate-threshold: 50
        wait-duration-in-open-state: 10s
        sliding-window-size: 10
      product-service:
        failure-rate-threshold: 60
        wait-duration-in-open-state: 10s
  timelimiter:
    instances:
      user-service:
        timeout-duration: 5s
      order-service:
        timeout-duration: 8s
      product-service:
        timeout-duration: 5s
```

---

## 3. User Service

### 3.1 No Kafka Event Publishing

**Problem:**
User creation, update, deactivation, and deletion are invisible to all other services. If a user is deleted, `order-service` has no way to know — orders still reference that `userId`.

**What is missing:**
- No `UserEventPublisher` class
- No Kafka dependency in `pom.xml`
- No `user-events` Kafka topic
- No `KafkaConfig.java`

**Dependency to add in `user-service/pom.xml`:**
```xml
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
</dependency>
```

**Events to publish:**
```java
public enum UserEventType {
    USER_CREATED, USER_UPDATED, USER_DEACTIVATED, USER_DELETED, USER_ROLE_CHANGED
}
```

**Publish on every write in `UserService.java`:**
```java
// After save:
eventPublisher.publish(UserEventType.USER_CREATED, savedUser);

// After update:
eventPublisher.publish(UserEventType.USER_UPDATED, updatedUser);

// After delete:
eventPublisher.publish(UserEventType.USER_DELETED, userId);
```

---

### 3.2 No Dedicated Status Change Endpoint

**Problem:**
`UserStatus` has `INACTIVE` and `SUSPENDED` states, but there is no `PATCH /api/v1/users/{id}/status` endpoint. Admins must issue a full `PUT` with the entire user payload just to suspend an account.

**Missing endpoint to add in `UserController.java`:**
```java
@PatchMapping("/{id}/status")
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<UserDto> updateStatus(
    @PathVariable Long id,
    @RequestParam UserStatus status
) {
    return ResponseEntity.ok(userService.updateStatus(id, status));
}
```

---

### 3.3 Paginated Cache Key Bug

**Problem:**
`@Cacheable(value = "usersPage", key = "#pageable")` uses `Pageable.toString()` as the key. This produces inconsistent keys across JVM restarts and is fragile if `Pageable` implementation changes.

**Current (fragile):**
```java
@Cacheable(value = "usersPage", key = "#pageable")
```

**Fix in `UserService.java`:**
```java
@Cacheable(
    value = "usersPage",
    key = "#pageable.pageNumber + '_' + #pageable.pageSize + '_' + #pageable.sort.toString()"
)
public Page<UserDto> getUsers(Pageable pageable) { ... }
```

---

### 3.4 No Input Sanitization

**Problem:**
`UserDto.name` and `UserDto.phone` are stored directly. No HTML sanitization is performed. If name values are ever rendered in an admin UI without escaping, stored XSS is possible.

**Fix — add custom validator or sanitizer in `UserService.java`:**
```java
private String sanitize(String input) {
    return input == null ? null : Jsoup.clean(input, Safelist.none()).trim();
}

// In save/update methods:
user.setName(sanitize(dto.getName()));
```

---

### 3.5 No Force-Logout / Session Invalidation

**Problem:**
There is no endpoint to revoke all active tokens for a user (e.g., after a password reset or account compromise). Because JWTs are stateless, the only way to invalidate them before expiry is via Keycloak's session management API.

**Missing endpoint:**
```java
// POST /api/v1/users/{id}/revoke-sessions
// Calls Keycloak Admin REST API to delete all user sessions:
// DELETE /realms/{realm}/users/{userId}/sessions
```

---

## 4. Order Service

### 4.1 Transactional Outbox Pattern — Missing

**Problem:**
`OrderService.create()` saves the order to MySQL and then calls `kafkaTemplate.send()` outside the transaction. If the service crashes between `orderRepository.save()` and `eventPublisher.publish()`, the order exists in the DB but no `ORDER_CREATED` event is ever published. Consumers (notification service, analytics) never learn about this order.

**What is missing:**
- No `OutboxEvent` entity / table
- No outbox publisher / poller
- No at-least-once delivery guarantee for Kafka events

**Fix — create outbox table (new Flyway migration `V4__create_outbox.sql`):**
```sql
CREATE TABLE outbox_events (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id   VARCHAR(100) NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    payload        JSON         NOT NULL,
    status         ENUM('PENDING','PUBLISHED','FAILED') DEFAULT 'PENDING',
    created_at     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    published_at   TIMESTAMP    NULL,
    retry_count    INT          DEFAULT 0
);
```

**Save outbox record in same transaction as order:**
```java
@Transactional
public OrderResponse create(OrderRequest req) {
    // ... existing logic ...
    Order saved = orderRepository.save(order);
    // Save outbox IN SAME TRANSACTION — atomically:
    outboxRepository.save(new OutboxEvent("ORDER", saved.getId(), "ORDER_CREATED", toJson(event)));
    return orderMapper.toResponse(saved);
}

// Separate scheduled poller (or Debezium CDC):
@Scheduled(fixedDelay = 5000)
@Transactional
public void publishPendingEvents() {
    outboxRepository.findByStatus(PENDING).forEach(event -> {
        kafkaTemplate.send("order-events", event.getAggregateId(), event.getPayload());
        event.markPublished();
    });
}
```

---

### 4.2 No Idempotency Key on Order Creation

**Problem:**
A user double-clicking "Place Order" or a network retry will create two identical orders. There is no deduplication mechanism.

**What is missing:**
- No `X-Idempotency-Key` header handling
- No idempotency store (Redis or DB table)
- No duplicate detection in `OrderController.java`

**Fix — add idempotency table (`V5__idempotency.sql`):**
```sql
CREATE TABLE idempotency_keys (
    idempotency_key  VARCHAR(64)  PRIMARY KEY,
    response_body    JSON         NOT NULL,
    http_status      INT          NOT NULL,
    created_at       TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    expires_at       TIMESTAMP    NOT NULL
);
```

**Fix in `OrderController.java`:**
```java
@PostMapping
public ResponseEntity<OrderResponse> createOrder(
    @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
    @Valid @RequestBody OrderRequest req,
    Authentication auth
) {
    if (idempotencyKey != null) {
        Optional<OrderResponse> cached = idempotencyService.get(idempotencyKey);
        if (cached.isPresent()) {
            return ResponseEntity.status(200).body(cached.get()); // replay cached response
        }
    }
    OrderResponse response = orderService.create(req, auth);
    if (idempotencyKey != null) {
        idempotencyService.store(idempotencyKey, response, Duration.ofHours(24));
    }
    return ResponseEntity.status(201).body(response);
}
```

---

### 4.3 Saga / Compensating Transaction — Missing

**Problem:**
`OrderService.create()` calls Feign to deduct stock from `product-service` and then persists the order. If the DB `save()` fails after stock is already deducted, `restoreStock()` is called in the catch block — but if `product-service` is also unreachable at that moment, the restoration fails silently and stock is permanently inconsistent.

**What is missing:**
- No saga orchestrator or choreography for the create-order flow
- `restoreStock()` failure in catch block is only logged, not retried
- No compensation record to retry restoration later

**Minimum viable fix — store pending stock restoration in DB:**
```java
@Transactional
public OrderResponse create(OrderRequest req) {
    List<StockDeduction> deducted = new ArrayList<>();
    try {
        for (OrderItemRequest item : req.getItems()) {
            productClient.deductStock(item.getProductId(), item.getQuantity());
            deducted.add(new StockDeduction(item.getProductId(), item.getQuantity()));
        }
        Order saved = orderRepository.save(buildOrder(req, deducted));
        // Remove deduction records — order committed successfully
        deductionRepository.deleteAll(deducted);
        return orderMapper.toResponse(saved);
    } catch (Exception e) {
        // Schedule compensations for retry even if product-service is down:
        compensationRepository.saveAll(
            deducted.stream().map(d -> new PendingStockRestore(d)).toList()
        );
        throw e;
    }
}

@Scheduled(fixedDelay = 30_000)
public void retryStockRestorations() {
    compensationRepository.findAll().forEach(c -> {
        try {
            productClient.restoreStock(c.getProductId(), c.getQuantity());
            compensationRepository.delete(c);
        } catch (Exception e) {
            log.warn("Stock restoration retry failed productId={}", c.getProductId(), e);
        }
    });
}
```

---

### 4.4 Feign `restoreStock()` Has No Circuit Breaker

**Problem:**
`cancelOrder()` calls `productServiceClient.restoreStock()` via Feign. This call has no `@CircuitBreaker` or `@Retry` annotation. If `product-service` is down during a cancellation, the exception propagates and the cancellation fails — the user cannot cancel their order even though the business logic is valid.

**Fix in `OrderService.java`:**
```java
@CircuitBreaker(name = "product-service", fallbackMethod = "restoreStockFallback")
@Retry(name = "product-service")
private void restoreStockSafe(Long productId, int quantity) {
    productServiceClient.restoreStock(productId, quantity);
}

private void restoreStockFallback(Long productId, int quantity, Exception e) {
    log.error("Stock restoration failed — scheduling for retry productId={} qty={}", productId, quantity, e);
    compensationRepository.save(new PendingStockRestore(productId, quantity));
}
```

**Also fix `resilience4j` retry to handle HTTP 409 (optimistic lock conflict from product-service):**
```properties
resilience4j.retry.instances.product-service.retry-exceptions=\
  feign.FeignException$Conflict,feign.RetryableException,\
  java.net.ConnectException,java.net.SocketTimeoutException
```

---

### 4.5 No Kafka Consumer — Order Events Never Consumed

**Problem:**
`order-service` publishes events (`ORDER_CREATED`, `ORDER_CONFIRMED`, `ORDER_SHIPPED`, `ORDER_DELIVERED`, `ORDER_CANCELLED`) to the `order-events` topic, but no service consumes them. The Kafka setup is producers-only.

**What is missing:**
- No `notification-service` that sends email/SMS on order status changes
- No `analytics-service` that tracks order metrics
- No in-service Kafka listener even for self-healing (e.g., retry failed payments)

**Minimum: add self-consumption listener for `PAYMENT_FAILED` retry in `order-service`:**
```java
@KafkaListener(topics = "order-events", groupId = "order-service-internal")
public void handleOrderEvent(OrderEvent event) {
    if (event.getEventType() == OrderEventType.PAYMENT_FAILED) {
        // Schedule retry or send alert
    }
}
```

**Recommended: create separate `notification-service`:**
```
notification-service/
  - Listens on order-events + product-events
  - Sends email via JavaMailSender / SendGrid
  - Sends SMS via Twilio
  - Stores notification history in MongoDB
```

---

### 4.6 No Dead Letter Queue for Kafka Producer Failures

**Problem:**
`OrderEventPublisher.publish()` calls `kafkaTemplate.send()` with no error callback. If Kafka is unavailable, the exception is caught and logged — the event is permanently lost with no way to replay it (without the outbox pattern from §4.1).

**Fix in `application.properties`:**
```properties
# Producer reliability settings
spring.kafka.producer.acks=all
spring.kafka.producer.retries=3
spring.kafka.producer.properties.enable.idempotence=true
spring.kafka.producer.properties.max.in.flight.requests.per.connection=1
spring.kafka.producer.properties.delivery.timeout.ms=30000
```

**Fix in `OrderEventPublisher.java`:**
```java
kafkaTemplate.send("order-events", event.getOrderId().toString(), event)
    .whenComplete((result, ex) -> {
        if (ex != null) {
            log.error("Failed to publish event eventType={} orderId={}", event.getEventType(), event.getOrderId(), ex);
            deadLetterRepository.save(new DeadLetterEvent("order-events", event));
        } else {
            log.debug("Event published eventType={} offset={}", event.getEventType(), result.getRecordMetadata().offset());
        }
    });
```

---

### 4.7 Coupon Code Validation — Stubbed but Never Implemented

**Problem:**
`OrderRequest` has `couponCode` field, `Order` entity has `discountAmount` and `couponCode` columns, but `OrderService.create()` never validates the coupon or applies any discount. `discountAmount` is always 0.

**What is missing:**
- No `Coupon` entity / `coupon` table
- No `CouponService` or `CouponRepository`
- No coupon validation in `OrderService.create()`
- No coupon management endpoints for admins

**Minimum fix — add coupon table and validation:**
```sql
-- V6__coupons.sql
CREATE TABLE coupons (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    code            VARCHAR(50)    UNIQUE NOT NULL,
    discount_type   ENUM('PERCENT','FLAT') NOT NULL,
    discount_value  DECIMAL(10,2)  NOT NULL,
    min_order_value DECIMAL(10,2)  DEFAULT 0,
    max_uses        INT            DEFAULT NULL,
    used_count      INT            DEFAULT 0,
    valid_from      TIMESTAMP      NOT NULL,
    valid_until     TIMESTAMP      NOT NULL,
    active          BOOLEAN        DEFAULT TRUE
);
```

---

### 4.8 `out_for_delivery` Status Missing from `order_status_history` Logic

**Problem:**
The `OrderController` exposes `POST /orders/{id}/out-for-delivery` but the `VALID_TRANSITIONS` map in `OrderService` and the corresponding service method needs verification that `SHIPPED → OUT_FOR_DELIVERY → DELIVERED` is enforced and audited correctly.

**Verify this transition exists in `OrderService.java`:**
```java
VALID_TRANSITIONS.put(OrderStatus.SHIPPED, Set.of(
    OrderStatus.OUT_FOR_DELIVERY, OrderStatus.DELIVERED // fallback direct if needed
));
VALID_TRANSITIONS.put(OrderStatus.OUT_FOR_DELIVERY, Set.of(
    OrderStatus.DELIVERED
));
```

---

## 5. Product Service

### 5.1 No Optimistic Lock Retry on `deductStock`

**Problem:**
`deductStock()` uses `@Version` for concurrent safety. Under high load (multiple simultaneous orders for the same product), one request throws `ObjectOptimisticLockingFailureException` (HTTP 409). The `order-service` Resilience4j retry configuration does not retry on HTTP 409 — so the order creation fails permanently even though a retry would succeed.

**Fix in `ProductService.java` (retry within the service itself):**
```java
@Retryable(
    retryFor = ObjectOptimisticLockingFailureException.class,
    maxAttempts = 3,
    backoff = @Backoff(delay = 50, multiplier = 2, random = true)
)
@Transactional
public ProductResponse deductStock(Long id, int quantity) {
    // existing logic
}
```

**Add `@EnableRetry` to `ProductServiceApplication.java`:**
```java
@SpringBootApplication
@EnableRetry
public class ProductServiceApplication { ... }
```

**Also fix in `order-service/application.properties`:**
```properties
resilience4j.retry.instances.product-service.retry-exceptions=\
  feign.FeignException$Conflict,\
  feign.RetryableException,\
  java.net.ConnectException
```

---

### 5.2 Category Cache Eviction Bug

**Problem:**
`deleteCategory()` evicts only the specific category by ID. If a parent category is deleted, child categories that reference it still have a stale `parentName` cached in Hazelcast.

**Current (buggy):**
```java
@CacheEvict(value = "categories", key = "#id")
public void deleteCategory(Long id) { ... }
```

**Fix in `CategoryService.java`:**
```java
@Caching(evict = {
    @CacheEvict(value = "categories", key = "#id"),
    @CacheEvict(value = "categories", allEntries = true)  // clears all category caches
})
public void deleteCategory(Long id) {
    // validate no products use this category
    // then delete
}
```

---

### 5.3 No Product Image Upload Endpoint

**Problem:**
`thumbnailUrl` is a plain URL string. There is no way to upload images through the API. Admins must host images externally and paste URLs manually.

**What is missing:**
- No `POST /api/v1/products/{id}/image` endpoint
- No S3 / MinIO integration
- No file size or MIME type validation

**Dependency to add:**
```xml
<dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>s3</artifactId>
</dependency>
```

**Endpoint to add in `ProductController.java`:**
```java
@PostMapping(value = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<ProductResponse> uploadImage(
    @PathVariable Long id,
    @RequestPart("file") MultipartFile file
) {
    validateImageFile(file); // check MIME type, max 5MB
    String url = s3Service.upload("products/" + id + "/" + file.getOriginalFilename(), file);
    return ResponseEntity.ok(productService.updateThumbnail(id, url));
}
```

---

### 5.4 `LIKE` Search — No Full-Text Search

**Problem:**
`ProductSpecification` builds `LIKE '%name%'` queries for product name and brand search. This:
- Cannot use database indexes (leading wildcard)
- Does not support typo tolerance or relevance scoring
- Degrades significantly as catalog grows

**What is missing:**
- No Elasticsearch / OpenSearch integration
- No `@Document` product index
- No relevance-ranked search results

**Dependency to add:**
```xml
<dependency>
    <groupId>org.springframework.data</groupId>
    <artifactId>spring-data-elasticsearch</artifactId>
</dependency>
```

**Minimum change — use MySQL FULLTEXT index:**
```sql
-- V4__fulltext_search.sql
ALTER TABLE products ADD FULLTEXT INDEX ft_product_search (name, description, brand);
```

```java
// ProductRepository.java
@Query(value = "SELECT * FROM products WHERE MATCH(name, description, brand) AGAINST (?1 IN BOOLEAN MODE)", nativeQuery = true)
List<Product> fullTextSearch(String query);
```

---

### 5.5 No Kafka Consumer for Product Events

**Problem:**
`product-service` publishes `STOCK_LOW`, `STOCK_OUT`, `PRODUCT_CREATED` events but no service consumes them. Low stock alerts go unnoticed; no replenishment workflow is triggered.

**What is missing:**
- No inventory/replenishment service
- No alerting on `STOCK_OUT` events
- No analytics on product popularity

**Minimum — add self-listener for low stock alerts:**
```java
@KafkaListener(topics = "product-events", groupId = "product-service-alerts")
public void handleProductEvent(ProductEvent event) {
    if (event.getEventType() == ProductEventType.STOCK_LOW) {
        log.warn("LOW_STOCK_ALERT productId={} sku={} remaining={}",
            event.getProductId(), event.getSku(), event.getStockQuantity());
        // Optionally: publish to a notification queue
    }
}
```

---

## 6. Service Registry

### 6.1 No Security on Eureka Dashboard

**Problem:**
Port 8761 is completely open. Anyone on the network can see all registered service instances, their IPs, ports, and metadata — a significant information disclosure risk.

**Dependency to add in `serviceregistry/pom.xml`:**
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
```

**`application.properties`:**
```properties
spring.security.user.name=eureka-admin
spring.security.user.password=${EUREKA_PASSWORD:changeme}

# All clients must use credentials in their defaultZone URL:
# eureka.client.service-url.defaultZone=http://eureka-admin:${EUREKA_PASSWORD}@localhost:8761/eureka
```

**Security config to create:**
```java
@Configuration
@EnableWebSecurity
public class EurekaSecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(csrf -> csrf.ignoringRequestMatchers("/eureka/**"))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
                .anyRequest().authenticated()
            )
            .httpBasic(Customizer.withDefaults())
            .build();
    }
}
```

---

### 6.2 No High Availability — Single Point of Failure

**Problem:**
A single Eureka instance means if the registry goes down, no new service instances can register and existing registrations become stale. In production this is a critical SPOF.

**Fix — peer-aware Eureka cluster:**
```properties
# serviceregistry-1/application.properties
server.port=8761
eureka.instance.hostname=eureka1
eureka.client.register-with-eureka=true
eureka.client.fetch-registry=true
eureka.client.service-url.defaultZone=http://eureka2:8762/eureka

# serviceregistry-2/application.properties
server.port=8762
eureka.instance.hostname=eureka2
eureka.client.service-url.defaultZone=http://eureka1:8761/eureka
```

---

### 6.3 Self-Preservation Mode Not Tuned

**Problem:**
Default self-preservation thresholds can cause stale registrations in low-traffic environments (typical in development and staging).

**`application.properties` additions:**
```properties
# Lower threshold for dev/staging:
eureka.server.renewal-percent-threshold=0.49
eureka.server.eviction-interval-timer-in-ms=5000

# Production — keep defaults but tune response cache:
eureka.server.response-cache-update-interval-ms=5000
eureka.server.use-read-only-response-cache=false
```

---

## 7. Cross-Cutting Missing Patterns

### 7.1 Exception Response Format Inconsistency

**Problem:**
- `user-service` — returns custom error body (plain message string)
- `order-service` — returns RFC 7807 `ProblemDetail`
- `product-service` — returns RFC 7807 `ProblemDetail`
- `apigateway` — returns raw Spring error JSON

Clients cannot write uniform error handling because the shape differs per service.

**Fix — standardize all services on RFC 7807 with these extra fields:**
```json
{
  "type":      "https://shopzone.com/errors/insufficient-stock",
  "title":     "Insufficient Stock",
  "status":    409,
  "detail":    "Product SKU-001 has only 2 units available, requested 5",
  "instance":  "/api/v1/orders",
  "requestId": "550e8400-e29b-41d4-a716-446655440000",
  "timestamp": "2026-05-28T10:30:00Z",
  "service":   "order-service"
}
```

**Update `user-service/GlobalExceptionHandler.java` to use `ProblemDetail`:**
```java
@ExceptionHandler(ResourceNotFoundException.class)
public ResponseEntity<ProblemDetail> handleNotFound(ResourceNotFoundException ex, HttpServletRequest req) {
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    pd.setType(URI.create("https://shopzone.com/errors/not-found"));
    pd.setProperty("requestId", MDC.get("requestId"));
    pd.setProperty("timestamp", Instant.now());
    pd.setProperty("service",   "user-service");
    return ResponseEntity.status(404).body(pd);
}
```

---

### 7.2 No Global Request Timeout on Feign Clients

**Problem:**
`order-service` Feign clients have no explicit connect/read timeout. A slow `product-service` (e.g., due to a complex JPA Criteria query) blocks the `order-service` thread indefinitely, eventually exhausting the thread pool.

**Fix in `order-service/application.properties`:**
```properties
# Global Feign timeouts
spring.cloud.openfeign.client.config.default.connect-timeout=2000
spring.cloud.openfeign.client.config.default.read-timeout=5000

# Per-client overrides
spring.cloud.openfeign.client.config.user-service.read-timeout=3000
spring.cloud.openfeign.client.config.product-service.read-timeout=4000
```

---

### 7.3 No API Versioning Strategy

**Problem:**
All endpoints are `/api/v1/...` but there is no mechanism to introduce a `v2` without breaking existing clients. No `Deprecation` response header is used to signal upcoming endpoint removal.

**Recommended approach — Header-based versioning in `apigateway`:**
```java
// Route requests with Accept: application/vnd.shopzone.v2+json
// to new v2 controller implementations
```

**Or URL-based versioning with alias routes:**
```properties
# application.yaml
spring.cloud.gateway.mvc.routes:
  - id: order-service-v2
    uri: lb://order-service
    predicates:
      - Path=/api/v2/orders/**
    filters:
      - RewritePath=/api/v2/(?<segment>.*), /api/v2/${segment}
```

---

### 7.4 No Admin Audit Log for Sensitive Operations

**Problem:**
No service records *who* changed *what* at the admin level:
- Who changed a product's price from ₹500 to ₹50?
- Who elevated a user's role to ADMIN?
- Who cancelled an order?

`order_status_history` captures order transitions but there is no equivalent for product or user changes.

**Fix — add `@EntityListeners` with Spring Data JPA Auditing:**
```java
// Add to every entity:
@CreatedBy
@Column(updatable = false)
private String createdBy;

@LastModifiedBy
private String lastModifiedBy;

// AuditorAware bean:
@Bean
public AuditorAware<String> auditorAware() {
    return () -> Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
        .map(Authentication::getName);
}
```

**Or AOP-based audit log for admin endpoints:**
```java
@Aspect
@Component
public class AdminAuditAspect {

    @AfterReturning(
        pointcut = "execution(* com.product.controller.ProductController.update*(..)) && @annotation(org.springframework.security.access.prepost.PreAuthorize)",
        returning = "result"
    )
    public void logAdminAction(JoinPoint jp, Object result) {
        String user = SecurityContextHolder.getContext().getAuthentication().getName();
        log.info("ADMIN_AUDIT user={} action={} args={}", user, jp.getSignature().getName(), jp.getArgs());
        auditRepository.save(new AuditEntry(user, jp.getSignature().getName(), jp.getArgs(), Instant.now()));
    }
}
```

---

### 7.5 No Centralized Configuration Service

**Problem:**
Each service has its own `application.properties`. Changing a shared property (Kafka broker URL, Keycloak realm, gateway secret) requires modifying and redeploying all 5 services.

**Missing service: `config-server`**
```
config-server/
├── pom.xml                          # spring-cloud-config-server
├── src/main/resources/
│   └── application.properties       # spring.cloud.config.server.git.uri=...
└── config-repo/
    ├── application.properties       # shared: kafka, keycloak, eureka URLs
    ├── user-service.properties      # user-service specific
    ├── order-service.properties
    └── product-service.properties
```

**Each service's `bootstrap.properties`:**
```properties
spring.config.import=configserver:http://localhost:8888
spring.cloud.config.fail-fast=true
spring.cloud.config.retry.max-attempts=5
```

---

### 7.6 No XSS / Input Sanitization

**Problem:**
`ProductRequest.description`, `OrderRequest.notes`, `ShipRequest.trackingNumber`, and `UserDto.name` are stored as-is. If values are rendered in a browser without escaping, stored XSS is possible.

**Dependency to add (once, in shared util or per service):**
```xml
<dependency>
    <groupId>org.jsoup</groupId>
    <artifactId>jsoup</artifactId>
    <version>1.17.2</version>
</dependency>
```

**Sanitization utility:**
```java
public class Sanitizer {
    public static String clean(String input) {
        return input == null ? null : Jsoup.clean(input.trim(), Safelist.none());
    }
    public static String allowBasicHtml(String input) {
        return input == null ? null : Jsoup.clean(input.trim(), Safelist.basic());
    }
}
```

---

## 8. Security Gaps

### 8.1 `X-Gateway-Secret` Default Value Committed to Source Control

**Problem:**
```properties
# All three services:
gateway.internal-secret=gw-secret-change-in-prod
```

This default value is in source control. Any attacker with repo access can bypass the gateway and call services directly with `X-Gateway-Secret: gw-secret-change-in-prod`.

**Fix in every `application.properties`:**
```properties
gateway.internal-secret=${GATEWAY_SECRET}
```

**In Kubernetes `Secret`:**
```yaml
apiVersion: v1
kind: Secret
metadata:
  name: gateway-secret
type: Opaque
stringData:
  GATEWAY_SECRET: <base64-encoded-strong-random-value>
```

---

### 8.2 Keycloak Client Secret in Properties File

**Problem:**
```properties
spring.security.oauth2.client.registration.keycloak.client-secret=your-service-client-secret
```

Secrets belong in environment variables or a secrets manager, not property files.

**Fix:**
```properties
spring.security.oauth2.client.registration.keycloak.client-secret=${KEYCLOAK_CLIENT_SECRET}
```

---

### 8.3 No TLS Between Services

**Problem:**
All inter-service calls (Feign: order → product, order → user, gateway → all services) are plain HTTP. In a shared network environment (Kubernetes namespace), these calls are readable by other pods.

**Minimum fix for Kubernetes — use mTLS via a service mesh:**
```yaml
# Istio / Linkerd PeerAuthentication:
apiVersion: security.istio.io/v1beta1
kind: PeerAuthentication
metadata:
  name: default
spec:
  mtls:
    mode: STRICT
```

**Without a service mesh — configure TLS in each Spring Boot service:**
```properties
server.ssl.enabled=true
server.ssl.key-store=classpath:keystore.p12
server.ssl.key-store-password=${KEYSTORE_PASSWORD}
server.ssl.key-store-type=PKCS12
```

---

### 8.4 JWT Expiry Not Re-Validated on Feign Calls

**Problem:**
`FeignSecurityConfig.jwtRelayInterceptor()` extracts the JWT from the current request and attaches it to outbound Feign calls. If the JWT is close to expiry, the downstream service may reject it with 401 — but this surfaces as a Feign exception that is caught by the circuit breaker and treated as a service failure, not as an auth error.

**Fix in `FeignSecurityConfig.java`:**
```java
@Bean
public RequestInterceptor jwtRelayInterceptor() {
    return template -> {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            // Don't relay if token expires in < 30 seconds
            if (jwt.getExpiresAt() != null &&
                jwt.getExpiresAt().isAfter(Instant.now().plusSeconds(30))) {
                template.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.getTokenValue());
            } else {
                throw new TokenExpiredException("JWT too close to expiry for downstream call");
            }
        }
    };
}
```

---

### 8.5 CORS — Only Gateway Configured, Not Consistent

**Problem:**
`apigateway/SecurityConfig.java` has CORS configured for `http://localhost:5173`. The individual services (`user-service`, `order-service`, `product-service`) also have `SecurityConfig` but no CORS configuration. If a developer ever calls a service directly (bypassing the gateway during local development), CORS will block the browser.

**Fix — add CORS to each service's `SecurityConfig.java` (for dev profile):**
```java
@Bean
@Profile("dev")
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(List.of("http://localhost:5173", "http://localhost:2027"));
    config.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));
    config.setAllowedHeaders(List.of("*"));
    config.setAllowCredentials(true);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
}
```

---

## 9. Infrastructure & Deployment Gaps

### 9.1 No Kubernetes Deployment Manifests for Services

**Problem:**
`deployment/k8s/` only contains `databases.yml`, `keycloak.yaml`, `kafka.yml`. No Kubernetes `Deployment`, `Service`, `ConfigMap`, or `Secret` manifests exist for any of the Spring Boot services.

**Files to create per service:**
```
deployment/k8s/
├── user-service/
│   ├── deployment.yml      # Deployment + HPA
│   ├── service.yml         # ClusterIP Service
│   └── configmap.yml       # Non-sensitive properties
├── order-service/
│   ├── deployment.yml
│   ├── service.yml
│   └── configmap.yml
├── product-service/
│   ├── deployment.yml
│   ├── service.yml
│   └── configmap.yml
├── apigateway/
│   ├── deployment.yml
│   ├── service.yml         # LoadBalancer or NodePort
│   └── ingress.yml         # NGINX Ingress with TLS
├── serviceregistry/
│   ├── deployment.yml      # StatefulSet for peer-awareness
│   └── service.yml
└── secrets.yml             # SealedSecrets or External Secrets reference
```

**Sample `deployment.yml` for `order-service`:**
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: order-service
spec:
  replicas: 2
  selector:
    matchLabels:
      app: order-service
  template:
    metadata:
      labels:
        app: order-service
    spec:
      containers:
        - name: order-service
          image: shopzone/order-service:latest
          ports:
            - containerPort: 2029
          env:
            - name: SPRING_PROFILES_ACTIVE
              value: "prod"
            - name: GATEWAY_SECRET
              valueFrom:
                secretKeyRef:
                  name: gateway-secret
                  key: GATEWAY_SECRET
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 2029
            initialDelaySeconds: 20
            periodSeconds: 10
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 2029
            initialDelaySeconds: 30
            periodSeconds: 15
          resources:
            requests:
              memory: "512Mi"
              cpu: "250m"
            limits:
              memory: "1Gi"
              cpu: "500m"
---
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: order-service-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: order-service
  minReplicas: 2
  maxReplicas: 5
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
```

---

### 9.2 No Docker Compose for Local Development

**Problem:**
CLAUDE.md mentions a `docker-compose.yml` snippet but there is no actual compose file at the project root. Developers must manually start MySQL, MongoDB, Keycloak, Kafka, and Zookeeper individually.

**File to create: `docker-compose.yml` at project root:**
```yaml
version: '3.8'
services:

  mysql:
    image: mysql:8.0
    ports:
      - "3306:3306"
    environment:
      MYSQL_ROOT_PASSWORD: root
      MYSQL_DATABASE: user_db
    volumes:
      - mysql_data:/var/lib/mysql
      - ./deployment/docker/mysql-init:/docker-entrypoint-initdb.d

  mongodb:
    image: mongo:7
    ports:
      - "27017:27017"
    environment:
      MONGO_INITDB_ROOT_USERNAME: admin
      MONGO_INITDB_ROOT_PASSWORD: admin123

  zookeeper:
    image: confluentinc/cp-zookeeper:7.5.0
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181

  kafka:
    image: confluentinc/cp-kafka:7.5.0
    ports:
      - "9092:9092"
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
    depends_on:
      - zookeeper

  keycloak:
    image: quay.io/keycloak/keycloak:24.0
    ports:
      - "8080:8080"
    environment:
      KEYCLOAK_ADMIN: admin
      KEYCLOAK_ADMIN_PASSWORD: admin
    command: start-dev

  zipkin:
    image: openzipkin/zipkin:latest
    ports:
      - "9411:9411"

  prometheus:
    image: prom/prometheus:latest
    ports:
      - "9090:9090"
    volumes:
      - ./deployment/docker/prometheus.yml:/etc/prometheus/prometheus.yml

  grafana:
    image: grafana/grafana:latest
    ports:
      - "3000:3000"
    environment:
      GF_SECURITY_ADMIN_PASSWORD: admin

volumes:
  mysql_data:
```

---

### 9.3 No Integration Test Configuration

**Problem:**
`spring.jpa.hibernate.ddl-auto=validate` with Flyway means all tests require a running MySQL instance. No `application-test.properties` or Testcontainers setup exists. Running tests on CI without a database will fail immediately.

**Dependency to add in every service `pom.xml`:**
```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>mysql</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>kafka</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
```

**Base test class:**
```java
@SpringBootTest
@Testcontainers
public abstract class IntegrationTestBase {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("order_db_test")
        .withUsername("test")
        .withPassword("test");

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      mysql::getJdbcUrl);
        registry.add("spring.datasource.username",  mysql::getUsername);
        registry.add("spring.datasource.password",  mysql::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }
}
```

---

### 9.4 No CI/CD Pipeline

**Problem:**
No `Jenkinsfile`, `.github/workflows/`, or `gitlab-ci.yml` exists. There is no automated build, test, or deploy process.

**File to create: `.github/workflows/ci.yml`:**
```yaml
name: CI
on:
  push:
    branches: [main, develop]
  pull_request:

jobs:
  build-and-test:
    runs-on: ubuntu-latest
    strategy:
      matrix:
        service: [user-service, order-service, product-service, apigateway, serviceregistry]
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: maven
      - name: Build ${{ matrix.service }}
        working-directory: ${{ matrix.service }}
        run: mvn clean verify -DskipTests=false
      - name: Build Docker image
        working-directory: ${{ matrix.service }}
        run: docker build -t shopzone/${{ matrix.service }}:${{ github.sha }} .
```

---

## 10. Summary Table

| # | Area | Affected Service(s) | Severity | Gap |
|---|------|---------------------|----------|-----|
| 1 | Structured logging (logback-spring.xml, JSON format, MDC) | All | 🔴 High | No JSON logs, no MDC fields, no logstash encoder |
| 2 | Distributed tracing (Micrometer Tracing / Zipkin) | All | 🔴 High | Cannot trace requests across services |
| 3 | Metrics & Prometheus endpoint | user, order, product | 🔴 High | No actuator, no /prometheus, no custom business metrics |
| 4 | Correlation ID filter (X-Correlation-ID in MDC) | All | 🔴 High | Cannot correlate logs across services |
| 5 | Rate limiting | apigateway | 🔴 High | No 429 / token bucket limiting |
| 6 | Service discovery routing (lb:// instead of hardcoded URLs) | apigateway | 🔴 High | Gateway breaks with multiple service instances |
| 7 | Transactional Outbox (event reliability) | order, product | 🔴 High | Kafka events lost if service crashes after DB write |
| 8 | Gateway secret in source control | order, product, user | 🔴 High | Default secret `gw-secret-change-in-prod` is committed |
| 9 | Keycloak client secret externalised | apigateway, user | 🔴 High | Secrets in properties files |
| 10 | No TLS between services | All | 🔴 High | Plain HTTP internally |
| 11 | Saga / compensating transaction for stock deduction | order | 🔴 High | Stock permanently inconsistent if DB save fails after deduction |
| 12 | Circuit breaker at gateway level | apigateway | 🟠 Medium | Downstream failure cascades to gateway thread exhaustion |
| 13 | Global error handler at gateway | apigateway | 🟠 Medium | Stack traces and inconsistent errors exposed to clients |
| 14 | Idempotency key for order creation | order | 🟠 Medium | Double-submit creates duplicate orders |
| 15 | Feign timeouts (connect + read) | order | 🟠 Medium | No timeout = thread exhaustion under slow downstream |
| 16 | Circuit breaker on restoreStock() Feign call | order | 🟠 Medium | Cancel order fails permanently if product-service is down |
| 17 | Kafka consumer (notification / analytics) | order, product | 🟠 Medium | Events published but never consumed |
| 18 | Dead letter queue for Kafka producer | order, product | 🟠 Medium | Failed events silently dropped |
| 19 | Eureka security (HTTP Basic auth on dashboard) | serviceregistry | 🟠 Medium | Registry fully open on the network |
| 20 | Eureka high availability (peer cluster) | serviceregistry | 🟠 Medium | Single point of failure |
| 21 | Health indicators (Kafka, downstream services) | order, product | 🟠 Medium | Services appear UP when dependencies are broken |
| 22 | Error response format consistency (RFC 7807) | user, apigateway | 🟠 Medium | Inconsistent error shapes across services |
| 23 | Admin audit log for sensitive operations | All | 🟠 Medium | No record of who changed prices, roles, status |
| 24 | Centralized config service (Spring Cloud Config) | All | 🟠 Medium | Each service has its own properties, no shared config |
| 25 | Kubernetes Deployment / HPA manifests | All | 🟠 Medium | Only DB/Kafka K8s manifests exist |
| 26 | Docker Compose for local development | All | 🟠 Medium | No compose file — manual setup required |
| 27 | User Kafka event publishing | user | 🟠 Medium | User lifecycle events invisible to other services |
| 28 | User status change endpoint (PATCH /users/{id}/status) | user | 🟡 Low | Full PUT required just to suspend an account |
| 29 | Paginated cache key robustness | user | 🟡 Low | Cache key uses Pageable.toString() — fragile |
| 30 | Input sanitization / XSS prevention | All | 🟡 Low | User-supplied text stored without HTML sanitization |
| 31 | Optimistic lock retry inside deductStock() | product | 🟡 Low | Concurrent stock deductions return 409 without retry |
| 32 | Category cache all-entries eviction on delete | product | 🟡 Low | Stale parentName in child category cache after delete |
| 33 | Product image upload endpoint (S3 / MinIO) | product | 🟡 Low | thumbnailUrl is a raw string, no upload API |
| 34 | Full-text search (LIKE → Elasticsearch / MySQL FULLTEXT) | product | 🟡 Low | LIKE '%name%' cannot use indexes |
| 35 | Coupon code validation and discount logic | order | 🟡 Low | couponCode field exists but is never validated |
| 36 | Request/response access logging at gateway | apigateway | 🟡 Low | No request audit trail through gateway |
| 37 | JWT expiry check before Feign relay | order | 🟡 Low | Expiring token relayed to downstream, causes misleading 401 |
| 38 | CORS on individual services for dev profile | user, order, product | 🟡 Low | Direct service calls blocked by browser in dev |
| 39 | API versioning strategy | All | 🟡 Low | No deprecation headers or v2 routing mechanism |
| 40 | Integration tests with Testcontainers | All | 🟡 Low | Tests require live MySQL — broken on CI without DB |
| 41 | CI/CD pipeline (.github/workflows) | All | 🟡 Low | No automated build, test, or Docker image pipeline |

---

*Legend: 🔴 High — data loss / security risk / production-breaking | 🟠 Medium — reliability / operational risk | 🟡 Low — quality / developer experience*

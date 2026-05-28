# Missing Microservices Concerns — Current Architecture Audit

> **Scope:** `serviceregistry` · `apigateway` · `user-service`  
> **Date:** 2026-05-27  
> **Method:** Full file-by-file audit of every `.java`, `.properties`, `.yaml`, `.xml` in the repo.

---

## Table of Contents

1. [Observability — Zero Coverage](#1-observability--zero-coverage)
2. [Resilience — Zero Coverage](#2-resilience--zero-coverage)
3. [Configuration Management — Hardcoded Everywhere](#3-configuration-management--hardcoded-everywhere)
4. [Service-to-Service Communication — Not Implemented](#4-service-to-service-communication--not-implemented)
5. [Database Management — ddl-auto=update in Production](#5-database-management--ddl-autoupdate-in-production)
6. [Testing — Nearly Empty](#6-testing--nearly-empty)
7. [Security Gaps](#7-security-gaps)
8. [API Design Gaps](#8-api-design-gaps)
9. [Kubernetes / Deployment Gaps](#9-kubernetes--deployment-gaps)
10. [Hazelcast — Embedded Not Clustered](#10-hazelcast--embedded-not-clustered)

---

## 1. Observability — Zero Coverage

### Current State

Nothing is instrumented. `spring-boot-starter-actuator` is not in any `pom.xml`.  
There are no trace IDs, no metrics endpoints, no structured log format.  
When something breaks in production there is **no trace, no metric, no searchable log**.

```
user-service/pom.xml     — no actuator, no micrometer, no OTel
apigateway/pom.xml       — no actuator, no micrometer, no OTel
application.properties   — no management.* properties at all
resources/               — no logback-spring.xml
```

---

### 1.1 No Spring Boot Actuator

**Impact:** K8s has no `/actuator/health/liveness` or `/actuator/health/readiness` endpoints.  
The cluster cannot detect a crashed or hung service → continues routing traffic to a broken pod.

**Fix — add to every service `pom.xml`:**

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

**Fix — add to every `application.properties`:**

```properties
# Expose health, info, metrics, prometheus endpoints
management.endpoints.web.exposure.include=health,info,metrics,prometheus
management.endpoint.health.show-details=always

# Split liveness and readiness for K8s probes
management.endpoint.health.probes.enabled=true
management.health.livenessState.enabled=true
management.health.readinessState.enabled=true
```

**Fix — K8s Deployment manifest for every service:**

```yaml
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 2026
  initialDelaySeconds: 30
  periodSeconds: 10
  failureThreshold: 3

readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 2026
  initialDelaySeconds: 20
  periodSeconds: 5
  failureThreshold: 3
```

---

### 1.2 No Distributed Tracing

**Impact:** A request flows through gateway → user-service → MySQL.  
When it fails or is slow there is no way to know **which hop** caused the problem.  
With multiple services (order → user → product → Kafka → notification) this becomes impossible to debug.

**Fix — add to every service `pom.xml`:**

```xml
<!-- Micrometer → OpenTelemetry bridge (modern; replaces old Brave/Zipkin) -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-otel</artifactId>
</dependency>

<!-- OTLP exporter: sends spans to OTel Collector → Grafana Tempo -->
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-otlp</artifactId>
</dependency>
```

**Fix — add to every `application.properties`:**

```properties
# Sample every request in dev; lower to 0.1 in production
management.tracing.sampling.probability=1.0

# OTel Collector OTLP HTTP endpoint (collector forwards to Tempo)
management.otlp.tracing.endpoint=http://localhost:4318/v1/traces

# Tag every span and metric with the service name
management.metrics.tags.application=${spring.application.name}
```

**What you get:** Every request gets a `traceId`. The gateway creates the root span.  
Each downstream service creates a child span. All linked in Grafana Tempo waterfall view.

```
traceId: 4bf92f3577b34da6a3ce929d0e0e4736

apigateway          [==================================] 120ms
  user-service      [=========================]          95ms
    hazelcast       [=]                                   2ms  (cache miss)
    mysql-select    [================]                   45ms
```

---

### 1.3 No Metrics Export

**Impact:** Cannot see request rate, error rate, p99 latency, JVM heap, thread pool saturation.  
Cannot set alerts. Cannot know the system is degrading before users complain.

**Fix — add to every service `pom.xml`:**

```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

This exposes `/actuator/prometheus` — Prometheus scrapes it every 15 seconds.

**Key metrics auto-exposed by Spring Boot + Micrometer:**

| Metric | Description |
|--------|-------------|
| `http_server_requests_seconds` | Request latency, tagged by URI, method, status |
| `jvm_memory_used_bytes` | Heap + non-heap memory |
| `jvm_gc_pause_seconds` | GC pause duration |
| `hikaricp_connections_active` | DB connection pool usage |
| `cache_gets_total` | Hazelcast cache hit/miss ratio |
| `system_cpu_usage` | CPU usage |

---

### 1.4 No Structured Logging

**Current log output (plain text — unsearchable):**
```
2026-05-27 10:00:01.123  INFO 12345 --- [nio-2026-exec-1] c.u.s.UserService : Getting all users
```

**Impact:**
- Cannot search logs by `traceId` to correlate with a Tempo trace
- Cannot filter by `level=ERROR` programmatically
- Log aggregation (Loki/ELK) cannot parse free-text

**Fix — create `src/main/resources/logback-spring.xml` in every service:**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>

    <appender name="JSON_CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <!--
              Micrometer Tracing automatically populates traceId and spanId
              into SLF4J MDC when a request is in scope.
              These fields appear in every log line automatically.
            -->
            <includeMdcKeyName>traceId</includeMdcKeyName>
            <includeMdcKeyName>spanId</includeMdcKeyName>
        </encoder>
    </appender>

    <!-- Keep SQL debug output readable in dev -->
    <springProfile name="dev">
        <logger name="org.hibernate.SQL" level="DEBUG"/>
    </springProfile>

    <root level="INFO">
        <appender-ref ref="JSON_CONSOLE"/>
    </root>

</configuration>
```

**Fix — add to `pom.xml`:**

```xml
<dependency>
    <groupId>net.logstash.logback</groupId>
    <artifactId>logstash-logback-encoder</artifactId>
    <version>7.4</version>
</dependency>
```

**Structured log output (JSON — fully searchable in Loki):**
```json
{
  "timestamp":  "2026-05-27T10:00:01.123Z",
  "level":      "INFO",
  "service":    "user-service",
  "traceId":    "4bf92f3577b34da6a3ce929d0e0e4736",
  "spanId":     "00f067aa0ba902b7",
  "logger":     "com.user.service.UserService",
  "thread":     "nio-2026-exec-1",
  "message":    "Getting all users — page 0, size 10"
}
```

**Grafana Loki query to find all logs for one request:**
```
{service="user-service"} | json | traceId="4bf92f3577b34da6a3ce929d0e0e4736"
```

---

### 1.5 No Log Aggregation

**Impact:** Logs are written to stdout. When a K8s pod restarts, all logs are lost.  
Cannot search logs across multiple service instances.

**Fix — deploy Promtail as a DaemonSet in K8s:**

```yaml
# Promtail scrapes stdout from every pod and ships to Loki
# Label: job=user-service, namespace=ecommerce
# Loki stores and indexes the JSON logs
```

**Fix — deploy Loki + Grafana datasource:**

```yaml
# deployment/k8s/observability.yaml (to be created)
# - OTel Collector  :4317/:4318
# - Grafana Tempo   :3200
# - Grafana Loki    :3100
# - Promtail        (DaemonSet)
# - Prometheus      :9090
# - Grafana         :3000
```

---

### 1.6 No Grafana Dashboards

**Fix — configure these datasources in Grafana:**

| Datasource | URL | Purpose |
|------------|-----|---------|
| Prometheus | `http://prometheus:9090` | Metrics — latency, error rate, JVM |
| Loki | `http://loki:3100` | Logs — searchable JSON with traceId |
| Tempo | `http://tempo:3200` | Traces — waterfall view |

**Correlations to enable (trace → log → metric in one click):**
- Tempo trace → Loki logs: click traceId in Tempo → see all logs for that request
- Prometheus spike → Tempo trace: exemplar on a latency spike links to the exact slow trace
- Loki log error → Tempo trace: click traceId field in Loki → open trace

---

## 2. Resilience — Zero Coverage

### Current State

No `resilience4j` dependency anywhere. No timeout on any HTTP client.  
A single slow MySQL query or unresponsive downstream service will:
1. Hold a Tomcat thread for the full request timeout (default 60s)
2. Exhaust the thread pool
3. Gateway returns 503 to all users — **full outage**

---

### 2.1 No Circuit Breaker

**Impact:** If user-service's MySQL goes down, every request hangs for 60s.  
Tomcat has 200 threads. After 200 concurrent requests the gateway returns 503 to everyone.

**Fix — add to `pom.xml`:**

```xml
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot3</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>
```

**Fix — annotate service methods:**

```java
@CircuitBreaker(name = "userDb", fallbackMethod = "getUserFallback")
@Transactional(readOnly = true)
public User getById(Long id) {
    return userRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("User not found"));
}

// Called automatically when circuit is OPEN
private User getUserFallback(Long id, Exception ex) {
    log.warn("Circuit OPEN for user {}: {}", id, ex.getMessage());
    throw new ServiceUnavailableException("User service temporarily unavailable");
}
```

**Fix — `application.properties` circuit breaker config:**

```properties
# Opens after 50% failure rate over 10 calls
resilience4j.circuitbreaker.instances.userDb.sliding-window-size=10
resilience4j.circuitbreaker.instances.userDb.failure-rate-threshold=50
resilience4j.circuitbreaker.instances.userDb.wait-duration-in-open-state=30s
resilience4j.circuitbreaker.instances.userDb.permitted-number-of-calls-in-half-open-state=3
resilience4j.circuitbreaker.instances.userDb.automatic-transition-from-open-to-half-open-enabled=true
```

**Circuit breaker state machine:**
```
CLOSED ──(50% failure rate)──► OPEN ──(30s)──► HALF-OPEN ──(probe OK)──► CLOSED
  ↑                                                    │
  └────────────────────────────────────────────────────┘ (probe FAIL → back to OPEN)
```

---

### 2.2 No Retry

**Impact:** A transient network blip (packet loss, brief DB connection drop) permanently fails the request.  
These are recoverable errors — a simple retry would succeed.

**Fix:**

```java
@Retry(name = "userDb", fallbackMethod = "getUserFallback")
@CircuitBreaker(name = "userDb", fallbackMethod = "getUserFallback")
public User getById(Long id) { ... }
```

```properties
resilience4j.retry.instances.userDb.max-attempts=3
resilience4j.retry.instances.userDb.wait-duration=500ms
# Retry on network/connection errors but NOT on 404
resilience4j.retry.instances.userDb.retry-exceptions=java.net.ConnectException,\
  java.sql.SQLTransientConnectionException
resilience4j.retry.instances.userDb.ignore-exceptions=com.user.exception.ResourceNotFoundException
```

---

### 2.3 No Timeout

**Impact:** If MySQL hangs (not down — just slow), the request waits forever.  
Default Tomcat request timeout is 0 (infinite).

**Fix — `application.properties`:**

```properties
# Tomcat: abort requests taking longer than 10s
server.tomcat.connection-timeout=10000

# HikariCP: fail fast if no DB connection available in 20s
spring.datasource.hikari.connection-timeout=20000
# Return a connection to the pool after 30s idle
spring.datasource.hikari.idle-timeout=30000
```

**Fix — Resilience4j timeout on service calls:**

```java
@TimeLimiter(name = "userDb")
public CompletableFuture<User> getByIdAsync(Long id) {
    return CompletableFuture.supplyAsync(() -> userRepository.findById(id).orElseThrow(...));
}
```

```properties
resilience4j.timelimiter.instances.userDb.timeout-duration=3s
resilience4j.timelimiter.instances.userDb.cancel-running-future=true
```

---

### 2.4 No Rate Limiting at Gateway

**Impact:** A single client can send unlimited requests — brute-force login, API abuse, accidental loop.  
No protection against denial-of-service.

**Fix — add Redis-backed rate limiter to `apigateway`:**

```xml
<!-- apigateway pom.xml -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

```java
// apigateway GatewayConfig.java — add to each route
.filter(FilterFunctions.requestRateLimiter(config -> config
    .setRateLimiter(redisRateLimiter())
    .setKeyResolver(ipKeyResolver())))
```

```java
@Bean
public RedisRateLimiter redisRateLimiter() {
    // 20 requests/second, burst up to 40
    return new RedisRateLimiter(20, 40, 1);
}

@Bean
public KeyResolver ipKeyResolver() {
    return exchange -> Mono.just(
        exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
    );
}
```

---

### 2.5 No Graceful Shutdown

**Impact:** When K8s restarts a pod (`kubectl rollout restart`), in-flight requests are killed mid-response.  
Users get connection reset errors during every deployment.

**Fix — `application.properties` (every service):**

```properties
# Wait for in-flight requests to complete before shutting down
server.shutdown=graceful

# Maximum time to wait for in-flight requests
spring.lifecycle.timeout-per-shutdown-phase=30s
```

**Fix — K8s Deployment (add `preStop` hook):**

```yaml
lifecycle:
  preStop:
    exec:
      # Give K8s time to stop sending new requests before shutdown begins
      command: ["sh", "-c", "sleep 5"]
terminationGracePeriodSeconds: 40
```

---

### 2.6 No Bulkhead

**Impact:** A slow `/api/v1/users` (bulk export) can exhaust all Tomcat threads,  
blocking `/api/v1/users/{id}` (fast single lookup) entirely.

**Fix:**

```java
@Bulkhead(name = "userBulkhead", type = Bulkhead.Type.SEMAPHORE)
public Page<User> getAll(Pageable pageable) { ... }
```

```properties
# Max 10 concurrent calls to getAll; others fail fast
resilience4j.bulkhead.instances.userBulkhead.max-concurrent-calls=10
resilience4j.bulkhead.instances.userBulkhead.max-wait-duration=0
```

---

## 3. Configuration Management — Hardcoded Everywhere

### Current State

Sensitive values committed in plain text to source control:

```properties
# user-service/src/main/resources/application.properties
spring.datasource.password=root123                         # ← in Git
client-secret=your-service-client-secret                  # ← in Git
gateway.internal-secret=gw-secret-change-in-prod          # ← in Git

# apigateway/src/main/resources/application.yaml
client-secret: your-client-secret                         # ← in Git
gateway.internal-secret: gw-secret-change-in-prod         # ← in Git
```

No Spring Cloud Config Server. No environment profiles. Every config change requires a rebuild.

---

### 3.1 No Spring Cloud Config Server

**Impact:** All 5+ services each have their own config. Changing a shared value (Keycloak URL, gateway secret)  
requires editing every service, rebuilding, redeploying all of them.

**Fix — create a new `config-server` service:**

```xml
<!-- config-server pom.xml -->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-config-server</artifactId>
</dependency>
```

```java
@SpringBootApplication
@EnableConfigServer
public class ConfigServerApplication { ... }
```

```yaml
# config-server application.yaml
server:
  port: 8888
spring:
  cloud:
    config:
      server:
        git:
          uri: https://github.com/your-org/ecommerce-config  # separate config repo
          default-label: main
          search-paths: '{application}'  # folder per service
```

**Fix — every service `bootstrap.properties`:**

```properties
spring.config.import=configserver:http://localhost:8888
spring.cloud.config.fail-fast=true
spring.cloud.config.retry.max-attempts=6
```

**Config repo structure:**
```
ecommerce-config/
├── user-service/
│   ├── application.properties          (common)
│   ├── application-dev.properties      (dev overrides)
│   └── application-prod.properties     (prod overrides)
├── apigateway/
│   ├── application.yaml
│   └── application-prod.yaml
└── order-service/
    └── application.properties
```

---

### 3.2 Secrets in Plain-Text Properties

**Impact:** DB passwords, client secrets, and the shared gateway secret are in source control.  
Anyone with read access to the repo can access the database and impersonate the gateway.

**Fix Option A — Kubernetes Secrets (minimum viable):**

```yaml
# K8s secret (base64 encoded values — NOT stored in Git)
apiVersion: v1
kind: Secret
metadata:
  name: user-service-secrets
  namespace: ecommerce
type: Opaque
stringData:
  db-password: "your-real-password"
  keycloak-client-secret: "your-real-secret"
  gateway-internal-secret: "your-real-secret"
```

```yaml
# K8s Deployment — mount secrets as env vars
env:
  - name: SPRING_DATASOURCE_PASSWORD
    valueFrom:
      secretKeyRef:
        name: user-service-secrets
        key: db-password
  - name: GATEWAY_INTERNAL_SECRET
    valueFrom:
      secretKeyRef:
        name: user-service-secrets
        key: gateway-internal-secret
```

**Fix Option B — HashiCorp Vault (production-grade):**

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-vault-config</artifactId>
</dependency>
```

```properties
spring.cloud.vault.host=vault
spring.cloud.vault.port=8200
spring.cloud.vault.scheme=https
spring.cloud.vault.authentication=KUBERNETES
spring.cloud.vault.kv.enabled=true
spring.cloud.vault.kv.backend=secret
spring.cloud.vault.kv.default-context=user-service
```

---

### 3.3 No Environment Profiles

**Impact:** The same `application.properties` is used for local dev, staging, and production.  
Dev has `ddl-auto=update`, `show-sql=true`, `sampling.probability=1.0` — all wrong for production.

**Fix — create profile-specific files:**

```
user-service/src/main/resources/
├── application.properties           ← shared (non-secret) defaults
├── application-dev.properties       ← local dev overrides
├── application-staging.properties   ← staging overrides
└── application-prod.properties      ← production overrides
```

```properties
# application-prod.properties
spring.jpa.show-sql=false
spring.jpa.hibernate.ddl-auto=validate   # never auto-alter in prod
management.tracing.sampling.probability=0.1   # 10% sampling in prod
logging.level.root=WARN
```

**Activate via:**

```bash
# K8s Deployment env var
SPRING_PROFILES_ACTIVE=prod
```

---

### 3.4 No Dynamic Config Refresh

**Impact:** Changing a log level, feature flag, or timeout requires a full redeploy.

**Fix — annotate beans that should reload on config change:**

```java
@RefreshScope          // re-creates this bean when /actuator/refresh is called
@Service
public class UserService { ... }
```

**Trigger refresh across all instances:**

```bash
# Config Server sends refresh event to all subscribers via Spring Cloud Bus
POST http://config-server:8888/actuator/busrefresh
```

---

## 4. Service-to-Service Communication — Not Implemented

### Current State

`CLAUDE.md` documents two features that **do not exist** in the codebase:

```
# CLAUDE.md says:
"TokenPropagationFilter (in gateway) stores the raw JWT..."
"@RequiresAdmin and @RequiresUser in user-service/src/main/java/com/user/aspect/"

# Reality — files do not exist:
apigateway/  → no TokenPropagationFilter.java
user-service/ → no aspect/ package, no @RequiresAdmin, no @RequiresUser
```

No Feign clients. No service-to-service calls. No JWT forwarding between services.  
Gateway routes to hardcoded `localhost:2026` — breaks with multiple instances.

---

### 4.1 No OpenFeign Clients

**Impact:** When Order Service needs to validate a user or get a product price,  
there is no framework for the HTTP call. Manual `RestClient` code with no retry, no circuit breaker.

**Fix — add to services that call others:**

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-openfeign</artifactId>
</dependency>
```

```java
@SpringBootApplication
@EnableFeignClients
public class OrderServiceApplication { ... }
```

```java
// Feign client — Spring generates the implementation at runtime
@FeignClient(name = "user-service", url = "${services.user-service.url}")
public interface UserServiceClient {

    @GetMapping("/api/v1/users/{id}")
    UserDto getUser(@PathVariable Long id);
}
```

```java
// Usage in OrderService
@Service
@RequiredArgsConstructor
public class OrderService {

    private final UserServiceClient userServiceClient;

    public Order createOrder(OrderRequest request) {
        UserDto user = userServiceClient.getUser(request.getUserId()); // automatic HTTP call
        ...
    }
}
```

---

### 4.2 No JWT Token Forwarding

**Impact:** Service A receives a Bearer token from the gateway.  
When Service A calls Service B via Feign, the token is NOT forwarded.  
Service B has no authenticated principal → every inter-service call returns 401 or 403.

**Fix — Feign `RequestInterceptor` that copies the incoming JWT:**

```java
@Configuration
public class FeignSecurityConfig {

    @Bean
    public RequestInterceptor jwtForwardingInterceptor() {
        return requestTemplate -> {
            // Get the JWT from the current request's SecurityContext
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth instanceof JwtAuthenticationToken jwtAuth) {
                String token = jwtAuth.getToken().getTokenValue();
                requestTemplate.header("Authorization", "Bearer " + token);
                requestTemplate.header("X-Gateway-Secret",
                    "${gateway.internal-secret}"); // also stamp the secret
            }
        };
    }
}
```

---

### 4.3 No Load Balancing

**Impact:** Gateway config has `user-service: http://localhost:2026`.  
When two instances of user-service run (scaled under load), the gateway **always hits only one**.  
The second instance receives zero traffic — scaling has no effect.

**Current problematic config:**
```java
// GatewayConfig.java
@Value("${gateway.routes.user-service:http://localhost:2026}")
private String userServiceUrl;   // ← hardcoded, single instance only
```

**Fix — switch to Spring Cloud LoadBalancer with Eureka:**

```xml
<!-- apigateway pom.xml -->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-loadbalancer</artifactId>
</dependency>
```

```java
// GatewayConfig.java — use lb:// scheme (resolved via Eureka)
.route(path("/api/v1/users/**"),
    HandlerFunctions.http("lb://user-service"))   // ← round-robins across instances
```

> **Note:** The current `GatewayConfig` comment explains why `lb://` was avoided  
> (`HandlerFunctions.http()` uses its own `RestClient`). The fix is to replace  
> `HandlerFunctions.http()` with a load-balancer-aware proxy handler.

---

### 4.4 Missing TokenPropagationFilter (documented but absent)

**`CLAUDE.md` documents this class but it does not exist:**

```java
// SHOULD EXIST in apigateway — currently missing
// apigateway/src/main/java/com/apigateway/filter/TokenPropagationFilter.java

@Component
public class TokenPropagationFilter implements HandlerFilterFunction<ServerResponse, ServerResponse> {

    @Override
    public ServerResponse filter(ServerRequest request, HandlerFunction<ServerResponse> next)
            throws Exception {
        // Store raw JWT for downstream services to use
        String authHeader = request.headers().firstHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            // Forwarded downstream via X-Gateway-Secret + Authorization header
        }
        return next.handle(request);
    }
}
```

---

### 4.5 Missing AOP Security Annotations (documented but absent)

**`CLAUDE.md` documents these but the `aspect/` package does not exist:**

```java
// SHOULD EXIST — currently missing
// user-service/src/main/java/com/user/aspect/RequiresAdmin.java

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresAdmin {}

// user-service/src/main/java/com/user/aspect/AuthorizationAspect.java

@Aspect
@Component
@Slf4j
public class AuthorizationAspect {

    @Before("@annotation(com.user.aspect.RequiresAdmin)")
    public void checkAdmin(JoinPoint jp) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = auth.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!isAdmin) {
            log.warn("Unauthorized ADMIN access attempt by {}", auth.getName());
            throw new AccessDeniedException("Admin role required");
        }
    }
}
```

---

## 5. Database Management — ddl-auto=update in Production

### Current State

```properties
# user-service/application.properties — DANGEROUS:
spring.jpa.hibernate.ddl-auto=update
spring.jpa.properties.hibernate.enable_lazy_load_no_trans=true
```

No Flyway. No Liquibase. No migration history. No rollback strategy.  
Schema changes are applied silently by Hibernate on every startup.

---

### 5.1 `ddl-auto=update` is Dangerous

**Impact:**
- Hibernate **can silently drop columns** or indexes it thinks are no longer mapped
- No migration history — impossible to know what changed between deployments
- Cannot roll back a bad schema change
- In production with data, an update can corrupt tables

**Fix — replace with `validate` and add Flyway:**

```properties
# application.properties
spring.jpa.hibernate.ddl-auto=validate   # only validates — never alters

# application-dev.properties
spring.jpa.hibernate.ddl-auto=validate   # still validate in dev; use Flyway
```

---

### 5.2 No Flyway Database Migrations

**Fix — add to `pom.xml`:**

```xml
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-mysql</artifactId>
</dependency>
```

**Fix — create migration scripts:**

```
user-service/src/main/resources/db/migration/
├── V1__create_users_table.sql
├── V2__add_email_unique_constraint.sql
└── V3__add_index_on_name.sql
```

```sql
-- V1__create_users_table.sql
CREATE TABLE users (
    id      BIGINT       NOT NULL AUTO_INCREMENT,
    version BIGINT,
    name    VARCHAR(200) NOT NULL,
    email   VARCHAR(255) NOT NULL,
    PRIMARY KEY (id)
);

-- V2__add_email_unique_constraint.sql
ALTER TABLE users ADD CONSTRAINT uq_users_email UNIQUE (email);
```

**How Flyway works:**
```
Startup
  → Flyway checks flyway_schema_history table
  → Runs any V*.sql scripts not yet applied (in version order)
  → Validates current schema matches applied migrations
  → Fails startup if schema is inconsistent (catches config drift)
```

---

### 5.3 `enable_lazy_load_no_trans=true` — Antipattern

**Impact:** This setting silently opens a new database connection for every lazy association  
loaded outside a transaction — causes N+1 queries that are invisible in testing  
but catastrophic under load.

**Fix:**

```properties
# Remove this line entirely:
# spring.jpa.properties.hibernate.enable_lazy_load_no_trans=true
```

```java
// Instead: annotate service methods properly
@Transactional(readOnly = true)
public User getById(Long id) {
    return userRepository.findById(id).orElseThrow(...);
}

// If you need associations loaded, use a JOIN FETCH query
@Query("SELECT u FROM User u LEFT JOIN FETCH u.orders WHERE u.id = :id")
Optional<User> findByIdWithOrders(@Param("id") Long id);
```

---

### 5.4 Email Not Unique at Database Level

**Impact:** Two users with the same email can be created — login becomes ambiguous.

**Fix — entity:**

```java
@Column(unique = true, nullable = false)
private String email;
```

**Fix — migration:**

```sql
-- V2__add_email_unique_constraint.sql
ALTER TABLE users ADD CONSTRAINT uq_users_email UNIQUE (email);
```

**Fix — GlobalExceptionHandler:**

```java
@ExceptionHandler(DataIntegrityViolationException.class)
public ResponseEntity<Object> handleDuplicate(DataIntegrityViolationException ex) {
    if (ex.getMessage().contains("uq_users_email")) {
        return body(HttpStatus.CONFLICT, "Conflict", "Email address already registered", null);
    }
    return body(HttpStatus.CONFLICT, "Conflict", "Database constraint violation", null);
}
```

---

## 6. Testing — Nearly Empty

### Current State

```java
// The only test in user-service:
@SpringBootTest
class MysqlmongodbApplicationTests {
    @Test
    void contextLoads() { }   // ← does nothing
}
```

No unit tests. No repository tests. No controller tests. No integration tests.  
Zero business logic is verified by the test suite.

---

### 6.1 No Service Unit Tests

**Fix — `UserServiceTest.java`:**

```java
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository userRepository;
    @InjectMocks UserService userService;

    @Test
    void getById_found_returnsUser() {
        User user = User.builder().id(1L).name("Alice").email("alice@example.com").build();
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        User result = userService.getById(1L);

        assertThat(result.getName()).isEqualTo("Alice");
    }

    @Test
    void getById_notFound_throwsResourceNotFoundException() {
        given(userRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getById(99L))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessage("User not found");
    }

    @Test
    void create_setsIdAndVersionToNull() {
        User input = User.builder().id(999L).version(5L).name("Bob").email("bob@example.com").build();
        User saved  = User.builder().id(1L).version(0L).name("Bob").email("bob@example.com").build();
        given(userRepository.saveAndFlush(any())).willReturn(saved);

        User result = userService.create(input);

        assertThat(result.getId()).isEqualTo(1L);   // assigned by DB
    }
}
```

---

### 6.2 No Controller Tests

**Fix — `UserControllerTest.java`:**

```java
@WebMvcTest(UserController.class)
@Import({SecurityConfig.class})
class UserControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean  UserService userService;
    @MockBean  UserMapper userMapper;

    @Test
    @WithMockUser(roles = "USER")
    void getById_returnsDto() throws Exception {
        given(userService.getById(1L))
            .willReturn(User.builder().id(1L).name("Alice").build());
        given(userMapper.toDto(any()))
            .willReturn(new UserDto(1L, 0L, "Alice", "alice@example.com"));

        mockMvc.perform(get("/api/v1/users/1")
                    .header("X-Gateway-Secret", "gw-secret-change-in-prod"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.name").value("Alice"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void create_invalidEmail_returns400() throws Exception {
        String body = """
            { "name": "Alice", "email": "not-an-email" }
            """;

        mockMvc.perform(post("/api/v1/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
                    .header("X-Gateway-Secret", "gw-secret-change-in-prod"))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.errors[0].field").value("email"));
    }
}
```

---

### 6.3 No Repository Tests with TestContainers

**Impact:** `@DataJpaTest` uses H2 in-memory DB by default.  
MySQL-specific queries (e.g. `LIMIT`, `OFFSET`, column types) may behave differently.

**Fix — add TestContainers:**

```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>mysql</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
```

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class UserRepositoryTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("user_db");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",    mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired UserRepository userRepository;

    @Test
    void save_andFindById() {
        User saved = userRepository.save(
            User.builder().name("Alice").email("alice@example.com").build());

        assertThat(userRepository.findById(saved.getId())).isPresent();
    }
}
```

---

### 6.4 No Contract Tests

**Impact:** When Order Service's Feign client expects `UserDto { id, name, email }`,  
but User Service changes the response to `{ userId, fullName, emailAddress }`,  
the contract is broken — but this is only discovered at runtime in integration/staging.

**Fix — Spring Cloud Contract (provider side in user-service):**

```groovy
// src/test/resources/contracts/getUserById.groovy
Contract.make {
    request {
        method GET()
        url "/api/v1/users/1"
        headers { header("X-Gateway-Secret", "gw-secret-change-in-prod") }
    }
    response {
        status OK()
        body(id: 1, name: "Alice", email: "alice@example.com")
        headers { contentType(applicationJson()) }
    }
}
```

---

## 7. Security Gaps

### 7.1 HTTP Everywhere — No TLS

**Impact:** JWT tokens, DB credentials, and all API data are transmitted in plaintext.  
Anyone on the same network can read them.

**Fix — TLS termination at gateway (K8s with cert-manager):**

```yaml
# K8s Ingress with cert-manager auto-provisioned TLS
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  annotations:
    cert-manager.io/cluster-issuer: letsencrypt-prod
spec:
  tls:
    - hosts: [api.yourdomain.com]
      secretName: api-tls-secret
  rules:
    - host: api.yourdomain.com
      http:
        paths:
          - path: /
            backend:
              service:
                name: apigateway
                port: { number: 2027 }
```

---

### 7.2 CORS Origins Hardcoded

**Current (hardcoded, breaks in any environment):**

```java
// apigateway SecurityConfig.java
config.setAllowedOrigins(Arrays.asList(
    "http://localhost:5173",    // ← only works locally
    "http://localhost:2026"
));
```

**Fix — externalize to `application.yaml`:**

```yaml
# apigateway application.yaml
cors:
  allowed-origins:
    - http://localhost:5173        # dev
    - https://app.yourdomain.com   # production
```

```java
@Value("${cors.allowed-origins}")
private List<String> allowedOrigins;

config.setAllowedOrigins(allowedOrigins);
```

---

### 7.3 Dead Code — `WebExchangeBindException` in MVC Handler

**Current `GlobalExceptionHandler.java` still imports a WebFlux class:**

```java
// This is a WebFlux-only class — never thrown in Spring MVC
@ExceptionHandler(WebExchangeBindException.class)
public ResponseEntity<Object> handleWebExchangeBind(WebExchangeBindException ex) { ... }
```

**Impact:** Dead code that causes confusion. `WebExchangeBindException` extends  
`MethodArgumentNotValidException`, so the MVC handler below it catches both anyway.

**Fix — remove the `WebExchangeBindException` handler entirely.**  
The `MethodArgumentNotValidException` handler already covers all MVC validation failures.

---

### 7.4 No Service-Level Identity

**Impact:** Services identify each other only by the shared `X-Gateway-Secret`.  
If any one service is compromised, it can impersonate any other.

**Fix — OAuth2 `client_credentials` per service:**

```properties
# Each service has its own client in Keycloak
spring.security.oauth2.client.registration.self.client-id=user-service-client
spring.security.oauth2.client.registration.self.client-secret=${KEYCLOAK_CLIENT_SECRET}
spring.security.oauth2.client.registration.self.authorization-grant-type=client_credentials
```

---

## 8. API Design Gaps

### 8.1 No Idempotency on POST

**Impact:** A client submits `POST /api/v1/users`. The network times out.  
The client retries. Two identical users are created.

**Fix — idempotency key header:**

```java
@PostMapping
public ResponseEntity<UserDto> create(
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @Valid @RequestBody UserDto userDto) {

    if (idempotencyKey != null) {
        // Check if this key was seen recently (store in Redis with TTL)
        Optional<UserDto> cached = idempotencyCache.get(idempotencyKey);
        if (cached.isPresent()) return ResponseEntity.ok(cached.get()); // replay
    }

    User created = userService.create(userMapper.toEntity(userDto));
    UserDto dto  = userMapper.toDto(created);

    if (idempotencyKey != null) {
        idempotencyCache.put(idempotencyKey, dto, Duration.ofHours(24));
    }

    return ResponseEntity.status(HttpStatus.CREATED).body(dto);
}
```

---

### 8.2 No Unified Swagger / API Portal at Gateway

**Impact:** Developers must know each service's port to find its Swagger.  
No single place to browse all APIs.

**Fix — SpringDoc gateway aggregator in `apigateway`:**

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.8.6</version>
</dependency>
```

```yaml
# apigateway application.yaml
springdoc:
  swagger-ui:
    urls:
      - name: user-service
        url: http://localhost:2026/v3/api-docs
      - name: product-service
        url: http://localhost:2028/v3/api-docs
      - name: order-service
        url: http://localhost:2029/v3/api-docs
```

Single Swagger portal at `http://localhost:2027/swagger-ui.html`  
with a dropdown to switch between all services.

---

## 9. Kubernetes / Deployment Gaps

### Current State

Only infrastructure (MySQL, MongoDB, Keycloak) has K8s manifests.  
The actual services (`user-service`, `apigateway`, `serviceregistry`) have **no Dockerfiles and no K8s manifests**.

```
deployment/k8s/
├── databases.yml    ← MySQL + MongoDB  ✅
└── keycloak.yaml    ← Keycloak         ✅

Missing:
├── user-service.yaml      ❌
├── apigateway.yaml        ❌
├── serviceregistry.yaml   ❌
└── observability.yaml     ❌
```

---

### 9.1 No Dockerfile per Service

**Fix — `user-service/Dockerfile`:**

```dockerfile
# Multi-stage build: compile in JDK, run in lean JRE
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
COPY --from=builder /app/target/*.jar app.jar
USER appuser
EXPOSE 2026
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**OR — use Spring Boot's built-in image builder (no Dockerfile needed):**

```powershell
mvn spring-boot:build-image -Dspring-boot.build-image.imageName=ecommerce/user-service:latest
```

---

### 9.2 No K8s Deployment Manifests for Services

**Fix — `deployment/k8s/user-service.yaml` (template):**

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: user-service
  namespace: ecommerce
spec:
  replicas: 2
  selector:
    matchLabels:
      app: user-service
  template:
    metadata:
      labels:
        app: user-service
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/path:   "/actuator/prometheus"
        prometheus.io/port:   "2026"
    spec:
      containers:
        - name: user-service
          image: ecommerce/user-service:latest
          ports:
            - containerPort: 2026
          env:
            - name: SPRING_PROFILES_ACTIVE
              value: prod
            - name: SPRING_DATASOURCE_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: user-service-secrets
                  key: db-password
            - name: GATEWAY_INTERNAL_SECRET
              valueFrom:
                secretKeyRef:
                  name: user-service-secrets
                  key: gateway-internal-secret
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 2026
            initialDelaySeconds: 30
            periodSeconds: 10
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 2026
            initialDelaySeconds: 20
            periodSeconds: 5
          resources:
            requests:
              memory: "256Mi"
              cpu: "100m"
            limits:
              memory: "512Mi"
              cpu: "500m"
---
apiVersion: v1
kind: Service
metadata:
  name: user-service
  namespace: ecommerce
spec:
  selector:
    app: user-service
  ports:
    - port: 2026
      targetPort: 2026
---
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: user-service-hpa
  namespace: ecommerce
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: user-service
  minReplicas: 2
  maxReplicas: 10
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
```

---

### 9.3 No CI/CD Pipeline

**Fix — `.github/workflows/build-deploy.yml`:**

```yaml
name: Build and Deploy

on:
  push:
    branches: [main]
    paths:
      - 'user-service/**'

jobs:
  build-test-deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Run tests
        run: mvn test
        working-directory: user-service

      - name: Build image
        run: mvn spring-boot:build-image
        working-directory: user-service

      - name: Push to registry
        run: docker push ecommerce/user-service:${{ github.sha }}

      - name: Deploy to K8s
        run: kubectl set image deployment/user-service user-service=ecommerce/user-service:${{ github.sha }}
```

---

## 10. Hazelcast — Embedded, Not Clustered

### Current State

```xml
<!-- hazelcast.xml join config -->
<join>
    <multicast enabled="false"/>
    <tcp-ip enabled="false"/>   ← clustering disabled
</join>
```

Each service instance runs a completely isolated Hazelcast cache.

---

### 10.1 Cache Inconsistency on Scale

**Impact (with 2 instances of user-service):**

```
Instance A: PUT /api/v1/users/1  → updates DB → evicts users cache on Instance A
Instance B: GET /api/v1/users/1  → cache HIT  → returns STALE data from Instance B's cache

User sees their update disappear on the next request (routed to Instance B).
```

**Fix Option A — Distributed Hazelcast cluster (connect instances):**

```xml
<!-- hazelcast.xml — enable K8s discovery -->
<join>
    <multicast enabled="false"/>
    <tcp-ip enabled="false"/>
    <kubernetes enabled="true">
        <namespace>ecommerce</namespace>
        <service-name>user-service</service-name>
    </kubernetes>
</join>
```

```xml
<!-- pom.xml — add K8s discovery plugin -->
<dependency>
    <groupId>com.hazelcast</groupId>
    <artifactId>hazelcast-kubernetes</artifactId>
    <version>2.2.3</version>
</dependency>
```

**Fix Option B — Switch to Redis (simpler for Spring Boot):**

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

```properties
spring.cache.type=redis
spring.data.redis.host=redis
spring.data.redis.port=6379
spring.cache.redis.time-to-live=600000   # 10 minutes
```

No code changes needed — `@Cacheable`, `@CachePut`, `@CacheEvict` all work with Redis.  
All instances share one Redis — cache eviction on Instance A is immediately visible to Instance B.

---

## Summary — Priority Matrix

| # | Category | Severity | Effort | Fix First |
|---|----------|----------|--------|-----------|
| 1 | Observability | 🔴 Critical | Medium | Actuator + OTel + Logstash |
| 2 | Resilience | 🔴 Critical | Medium | Resilience4j + Graceful shutdown |
| 5 | Database (`ddl-auto`) | 🔴 Critical | Low | `validate` + Flyway |
| 3 | Config Management | 🔴 High | High | Secrets via K8s → Config Server later |
| 7 | Security gaps | 🟠 High | Low-Med | Remove dead code + unique email first |
| 4 | Service-to-Service | 🟠 High | Medium | Needed before adding Order Service |
| 6 | Testing | 🟠 High | Medium | Unit tests first, TestContainers next |
| 8 | API Design | 🟡 Medium | Low | Unique email constraint + Swagger agg |
| 9 | K8s / Deployment | 🟡 Medium | High | Dockerfile + manifests when deploying |
| 10 | Hazelcast clustering | 🟡 Medium | Low | Switch to Redis when scaling > 1 |

# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Architecture Overview

This is a Spring Boot microservices project with independent services under `services/`, a React frontend under `client/`, and Kubernetes manifests under `deployment/k8s/`.

```
ecommerce/
├── services/
│   ├── apigateway/      — Spring Cloud Gateway MVC (port 2027)
│   ├── user-service/    — User management service (port 2026)
│   ├── product-service/ — Product catalogue + stock management (port 2028)
│   └── order-service/   — Order lifecycle + Outbox/Kafka events (port 2029)
├── client/              — React TypeScript frontend (Vite 6)
└── deployment/k8s/      — Kubernetes manifests (templates/, kustomization)
```

**Request flow:** Client → API Gateway (port 2027) → user-service / product-service / order-service via K8s DNS.

## Build and Run Commands

Each service is a standalone Maven project under `services/`. Run commands from within each service directory:

```powershell
# Build
cd services/user-service
mvn clean package

# Run
mvn spring-boot:run

# Skip tests during build
mvn clean package -DskipTests

# Run tests only
mvn test

# Run a single test class
mvn test -Dtest=MysqlmongodbApplicationTests

# Compile-only check
mvn clean compile
```

Frontend (Vite + React):
```powershell
cd client
npm install
npm run dev      # dev server at http://localhost:5173, proxies /api and /auth to gateway port 2027
npm run build    # production build
npm run lint
```

Startup order matters: **infrastructure (MySQL, Kafka, Keycloak) → apigateway → user-service / product-service / order-service**.

## Infrastructure Dependencies

The services expect the following infrastructure (configured via K8s NodePorts):

| Service   | K8s NodePort | Notes                                |
|-----------|--------------|--------------------------------------|
| MySQL     | 30036        | Database: `user_db`                  |
| MongoDB   | 30017        | User: `admin`, Pass: `admin123`      |
| Keycloak  | 30080        | Realm: `microservices-realm`         |
| Kafka     | 30092        | 3-partition topics; UI at NodePort 30808 |

Apply K8s manifests:
```powershell
kubectl apply -f deployment/k8s/databases.yml
kubectl apply -f deployment/k8s/keycloak.yaml
```

For local development without Kubernetes, use Docker Compose targeting the same ports (see `services/user-service/README.md` for a `docker-compose.yml` snippet).

## Key Architectural Decisions

### Mixed Reactive/Servlet in user-service
The `user-service` uses `spring.main.web-application-type=reactive` with `@EnableWebFluxSecurity`, but the `UserController` and `UserService` are standard Spring MVC beans backed by blocking JPA. The `AuthenticationController` returns `Mono<>`. This is intentional — the service runs in reactive mode primarily to support non-blocking security while keeping JPA data access synchronous.

### Keycloak JWT Role Mapping
Both the gateway and user-service use `KeycloakJwtConverter` to extract roles from Keycloak JWTs. Keycloak puts roles in `realm_access.roles` (and `resource_access.<clientId>.roles`). The converter maps these to `ROLE_<ROLENAME>` Spring Security authorities. Spring Security's default converter only reads `scope`/`scp` claims — always use `KeycloakJwtConverter` when adding new services.

- `KeycloakJwtConverter.reactive()` — for WebFlux (user-service SecurityConfig)
- `KeycloakJwtConverter.blocking()` / `KeycloakJwtConverter.create()` — for Servlet (gateway SecurityConfig)

### X-Gateway-Secret Header
The gateway adds an `X-Gateway-Secret` header to every downstream request. Each service validates it in `GatewaySecretFilter` and rejects direct calls missing the header. The secret is injected from a K8s Secret and must match across gateway and all services (`gateway.internal-secret` property). Any new service must wire this filter.

### Service-to-Service Communication (Feign)
Order-service calls user-service and product-service via `@FeignClient` (direct URLs, no Eureka). `FeignSecurityConfig` injects a `RequestInterceptor` that propagates both `Authorization: Bearer <jwt>` and `X-Gateway-Secret` on every Feign call. Feign clients: `ProductServiceClient` (`getProduct`, `deductStock`, `restoreStock`) and `UserServiceClient` (`getUser`).

### Circuit Breaker + Retry (Resilience4j)
Order-service Feign calls are wrapped in `@CircuitBreaker` + `@Retry`. Self-injection via `@Lazy OrderService self` is required for AOP to proxy the annotated methods.

- **user-service / product-service circuit breakers:** 10-call sliding window, 50% failure threshold, 30s wait in OPEN state
- **order-service circuit breaker:** tighter at 40% threshold to fail fast and prevent cascade during stock deduction
- **Retry:** 1s delay, max 3 attempts; product-service retry also covers `FeignException$Conflict` (HTTP 409 from optimistic lock collisions)
- Fallback methods throw `ResourceNotFoundException` — **never return null from fallbacks**, as null causes NPEs downstream

### Outbox Pattern (Order Service)
Order creation and the `OutboxEvent` row are written in a single transaction. `OutboxPoller` runs every 5 seconds, publishes pending events to Kafka, and marks them `SENT`. After 3 publish failures the record is marked `FAILED` for manual inspection — there is no automatic dead-letter queue replay.

**Kafka topics:**

| Topic            | Partitions | Partitioned by | Purpose                        |
|------------------|-----------|----------------|--------------------------------|
| `order-events`   | 3         | orderId        | Order status changes           |
| `product-events` | 3         | productId      | Stock-level alerts             |

Producer config: `acks=all`, `enable.idempotence=true`, `retries=3`, `max.block.ms=3000`.

### Idempotency (Order Service)
Clients send `X-Idempotency-Key` on `POST /orders`. The key and resulting `orderId` are stored in `idempotency_keys` (unique constraint). Duplicate requests return the cached order without re-processing.

### Optimistic Locking on Stock
`Product` entity has `@Version Long version`. `deductStock()` and `restoreStock()` use Spring Retry `@Retryable(ObjectOptimisticLockingFailureException, maxAttempts=3, backoff=50ms × 2× + jitter)` — this is local Spring Retry, distinct from the Resilience4j `@Retry` used on Feign calls.

### Stock Compensation
`PendingStockRestore` tracks orders awaiting stock restoration post-cancellation. `StockCompensationScheduler` periodically retries incomplete restorations.

### Caching (Hazelcast)
Hazelcast is configured via `hazelcast.xml` in each service (`user-service` and `product-service`). Embedded single-node mode (no cluster). Named cache maps follow a `<entity>` / `<entity>Page` split:

- **user-service:** `users` (10 min TTL, 1000 max), `usersPage` (5 min TTL, 500 max)
- **product-service:** `products` (10 min TTL, 2000 max), `productsPage` (5 min TTL, 500 max)

Write operations must evict the `*Page` cache (`allEntries=true`) in addition to the individual entry. Any new cached type must be added to the `<java-serialization-filter>` whitelist in `hazelcast.xml` or `HazelcastSerializationException` is thrown at runtime.

### DTO / MapStruct
All controller I/O uses DTOs (`UserDto`, `ProductDto`), never JPA entities directly. `UserMapper` and `ProductMapper` are MapStruct compile-time generated mappers (`componentModel = "spring"`). MapStruct and Lombok annotation processors are both wired in `maven-compiler-plugin`. **MapStruct must be listed after Lombok** in the processor list; if reversed, MapStruct-generated code won't see Lombok-generated getters/setters.

### Optimistic Locking (User)
`User` entity has `@Version Long version`. On updates, the client must send the current version. A mismatch returns HTTP 409. The `update()` method in `UserService` deliberately does not copy the version from the request — it relies on Hibernate to detect conflicts.

### Token Propagation
`TokenPropagationFilter` (in gateway) stores the raw JWT as a request attribute (`jwt_token` and `jwt_token_header`) for downstream forwarding. Downstream services validate the same JWT independently as OAuth2 resource servers.

### Custom AOP Security Annotations
`@RequiresAdmin` and `@RequiresUser` in `services/user-service/src/main/java/com/user/aspect/` are custom AOP annotations backed by `AuthorizationAspect`. They check Spring Security context directly. Prefer these for method-level security over `@PreAuthorize` when you need logging.

### Product Search (Specifications)
`ProductSpecification` implements `Specification<Product>` for dynamic filtering (name LIKE, category, brand, price range, in-stock flag, status). Status defaults to `ACTIVE` when not specified — admin must explicitly request `INACTIVE`/`DISCONTINUED` products. Backed by a MySQL FULLTEXT index (applied in `V4__fulltext_search.sql`).

### S3 / MinIO (Product Service)
`S3Config` builds an `S3Client` that supports both real AWS S3 and a local MinIO instance (`cloud.aws.s3.endpoint` override, `pathStyleAccessEnabled=true`). `S3Service` handles product image upload/retrieval.

### Database Migrations (Flyway)
All services use Flyway with `V#__description.sql` files in `src/main/resources/db/migration/`. JPA DDL is always `validate` — never use `update` or `create`. Key order-service migrations (V1–V8): orders → order_items → status_history → outbox_events → idempotency_keys → pending_stock_restores → dead_letter_events → coupons.

### Frontend Authentication Flow
1. Login posts `{ username, password }` to gateway `/auth/token` (avoids direct Keycloak CORS issues)
2. Response contains `access_token`, `refresh_token`, `expires_in`; stored in `localStorage[AUTH]`
3. Axios response interceptor detects 401 and calls `/auth/refresh` — concurrent 401s are queued so only one refresh is in flight
4. `authBridge` object decouples `AuthContext` from the Axios instance (can't import React context in plain JS modules)
5. `scheduleRefresh()` proactively refreshes before expiry
6. Logout calls `/auth/logout` on gateway, clears storage, redirects to `/login`

Vite dev proxy routes `/api` and `/auth` from `localhost:5173` → `localhost:2027`, making the browser see same-origin requests. Production builds must set the real gateway URL via `.env`.

## Service Ports and Endpoints

| Service         | Port | Notable Endpoints                                    |
|-----------------|------|------------------------------------------------------|
| user-service    | 2026 | `/api/v1/users/**`, `/api/v1/auth/**`                |
| product-service | 2028 | `/api/v1/products/**`, `/api/v1/categories/**`       |
| order-service   | 2029 | `/api/v1/orders/**`                                  |
| apigateway      | 2027 | `/auth/login`, `/auth/logout`, `/auth/refresh`, `/auth/health` |
| Keycloak (k8s)  | 30080| `/admin`, `/realms/microservices-realm/...`          |

**Swagger UI** is enabled on all services: `http://localhost:<port>/swagger-ui/index.html`  
**OpenAPI JSON**: `http://localhost:<port>/v3/api-docs`

## Keycloak Configuration

Realm: `microservices-realm`  
Token URL: `http://localhost:30080/realms/microservices-realm/protocol/openid-connect/token`  
JWKS URL: `http://localhost:30080/realms/microservices-realm/protocol/openid-connect/certs`

The `user-service` uses OAuth2 client registration `keycloak` with `client_credentials` grant for service-to-service calls. The client secret in `application.properties` (`your-service-client-secret`) is a placeholder — replace with the actual secret from Keycloak admin.

Roles expected in JWT: `USER`, `ADMIN` (mapped to `ROLE_USER`, `ROLE_ADMIN`).

## Observability (K8s)

The `deployment/k8s/templates/observability/` folder contains: **Prometheus** (metrics from `/actuator/prometheus`), **Grafana** (dashboards), **Loki** (logs via Promtail DaemonSet), **Jaeger + Tempo** (distributed tracing via OTel Collector). All services are pre-configured with:
```properties
management.tracing.sampling.probability=1.0
management.otlp.tracing.endpoint=http://localhost:30418/v1/traces
management.endpoints.web.exposure.include=health,prometheus,metrics,info
```

Services also configure `readinessProbe` (`/actuator/health/readiness`) and `livenessProbe` (`/actuator/health/liveness`) with a `startupProbe` that tolerates up to ~2 min startup time.

## Adding a New Service

1. Create a new Spring Boot project under `services/<new-service>/`.
2. Add `spring.application.name` to `application.properties`.
3. Add `spring-boot-starter-oauth2-resource-server` and configure `spring.security.oauth2.resourceserver.jwt.jwk-set-uri`.
4. Use `KeycloakJwtConverter` to map Keycloak roles — do not rely on default JWT converter.
5. Wire `GatewaySecretFilter` to validate `X-Gateway-Secret` on all `/api/**` routes.
6. Add a route entry in the API Gateway's `application.yaml` using the K8s DNS URI (e.g. `http://new-service:PORT`), including a `CircuitBreaker` filter with a fallback URI.
7. Add Flyway dependency; set `spring.jpa.hibernate.ddl-auto=validate`.
8. Create K8s Service + Deployment + ConfigMap + Secret under `deployment/k8s/templates/applications/<new-service>/`.
9. If caching with Hazelcast, add new types to the `<java-serialization-filter>` whitelist in `hazelcast.xml`.

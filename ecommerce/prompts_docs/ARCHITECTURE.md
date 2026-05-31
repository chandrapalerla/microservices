# E-Commerce Microservices — Architecture Guide

## Table of Contents

1. [Current Architecture](#1-current-architecture)
2. [Service Overview](#2-service-overview)
3. [Infrastructure Dependencies](#3-infrastructure-dependencies)
4. [Request Flow](#4-request-flow)
5. [Security Model](#5-security-model)
6. [Proposed Architecture — 3 New Services](#6-proposed-architecture--3-new-services)
7. [New Services — Responsibilities](#7-new-services--responsibilities)
8. [Service-to-Service Communication](#8-service-to-service-communication)
9. [Cross-Cutting Concerns](#9-cross-cutting-concerns)
10. [Observability Stack — LGTM](#10-observability-stack--lgtm)
    - [Why Not Zipkin](#why-not-zipkin)
    - [Tracing Technology Comparison](#tracing-technology-comparison)
11. [Kafka Event Design](#11-kafka-event-design)
12. [Startup Order](#12-startup-order)
13. [Port Reference](#13-port-reference)
14. [Build Roadmap](#14-build-roadmap)

---

## 1. Current Architecture

```
┌──────────────────────────────────────────────────────────────────────────┐
│                         CLIENT  :5173                                     │
│                    (React / Vue SPA)                                      │
└───────────────────────────┬──────────────────────────────────────────────┘
                            │  HTTP / HTTPS
                            ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                      API GATEWAY  :2027                                   │
│                  (Spring Cloud Gateway MVC)                               │
│                                                                           │
│  ┌──────────────────────────────────────────────────────────────────┐    │
│  │  Security Layer                                                   │    │
│  │  • Validates JWT via Keycloak JWKS                                │    │
│  │  • Enforces role-based route access                               │    │
│  │  • Owns CORS for all downstream services                          │    │
│  │  • Stamps X-Gateway-Secret on every forwarded request             │    │
│  └──────────────────────────────────────────────────────────────────┘    │
│  ┌──────────────────────────────────────────────────────────────────┐    │
│  │  Auth Proxy  (/auth/**)                                           │    │
│  │  POST /auth/token   → proxies ROPC to Keycloak token endpoint     │    │
│  │  POST /auth/refresh → proxies refresh_token exchange              │    │
│  │  POST /auth/logout  → clears server session                       │    │
│  │  GET  /auth/health  → liveness check                              │    │
│  └──────────────────────────────────────────────────────────────────┘    │
│  ┌──────────────────────────────────────────────────────────────────┐    │
│  │  Routes                                                           │    │
│  │  /api/v1/users/**  ──► user-service  :2026                        │    │
│  │  /api/v1/auth/**   ──► user-service  :2026                        │    │
│  └──────────────────────────────────────────────────────────────────┘    │
└───────────────────────────┬──────────────────────────────────────────────┘
                            │  HTTP + X-Gateway-Secret header
                            ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                      USER SERVICE  :2026                                  │
│                  (Spring MVC + Spring Data JPA)                           │
│                                                                           │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌─────────────┐  │
│  │  Controller  │  │   Service    │  │  Repository  │  │   Cache     │  │
│  │  UserCtrl    │  │  UserService │  │  UserRepo    │  │  Hazelcast  │  │
│  │  AuthCtrl    │  │  @Cacheable  │  │  JpaRepo     │  │  users      │  │
│  │              │  │  @CachePut   │  │              │  │  usersPage  │  │
│  └──────────────┘  └──────────────┘  └──────────────┘  └─────────────┘  │
│                                                                           │
│  • MySQL (K8s NodePort 30036)      database: user_db                     │
│  • Hazelcast cache    users: TTL 10m / 1000 entries                      │
│                       usersPage: TTL 5m / 500 entries                    │
│  • Optimistic locking @Version on User entity                            │
│  • MapStruct compile-time DTO mapper (UserDto ↔ User)                    │
│  • OAuth2 Resource Server — re-validates JWT (defence-in-depth)          │
│  • KeycloakJwtConverter — realm_access.roles → ROLE_USER / ROLE_ADMIN    │
│  • GatewaySecretFilter — rejects requests without X-Gateway-Secret       │
└──────────────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────────────┐
│               SERVICE REGISTRY (Eureka)  :8761                           │
│  All services register on startup.                                       │
│  Gateway resolves service addresses via direct URLs (not lb://).         │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Service Overview

| Service              | Port | Technology                          | Purpose                          |
|----------------------|------|-------------------------------------|----------------------------------|
| `serviceregistry`    | 8761 | Netflix Eureka Server               | Service discovery registry       |
| `apigateway`         | 2027 | Spring Cloud Gateway MVC            | Edge: auth, CORS, routing        |
| `user-service`       | 2026 | Spring MVC + JPA + Hazelcast        | User management                  |

### Key Architectural Decisions — Current

| Decision | Why |
|----------|-----|
| Gateway MVC (servlet) not WebFlux | Avoids Netty/Tomcat conflict; simpler routing DSL |
| Direct URLs in gateway (not `lb://`) | `HandlerFunctions.http()` creates its own `RestClient` unaware of LoadBalancer |
| `X-Gateway-Secret` header | Prevents clients from calling services directly, bypassing auth |
| Defence-in-depth JWT validation | Both gateway and user-service validate the JWT independently |
| `@Version` optimistic locking | Prevents lost updates on concurrent `PUT` requests |
| Hazelcast embedded cache | Low-latency lookups without a separate cache server in local dev |
| MapStruct over manual mapping | Compile-time safety; zero runtime reflection overhead |

---

## 3. Infrastructure Dependencies

| Service   | Local Port | K8s NodePort | Notes                              |
|-----------|------------|--------------|------------------------------------|
| MySQL     | —          | 30036        | Databases: `user_db`               |
| MongoDB   | —          | 30017        | User: `admin`, Pass: `admin123`    |
| Keycloak  | —          | 30080        | Realm: `microservices-realm`       |

```powershell
# Apply infrastructure manifests
kubectl apply -f deployment/k8s/databases.yml
kubectl apply -f deployment/k8s/keycloak.yaml
```

**Keycloak Configuration**

| Setting | Value |
|---------|-------|
| Realm | `microservices-realm` |
| Token URL | `http://localhost:30080/realms/microservices-realm/protocol/openid-connect/token` |
| JWKS URL | `http://localhost:30080/realms/microservices-realm/protocol/openid-connect/certs` |
| Roles | `USER`, `ADMIN` → mapped to `ROLE_USER`, `ROLE_ADMIN` |

---

## 4. Request Flow

```
Browser
  │
  │  POST /auth/token { username, password }
  ▼
API Gateway ──► Keycloak token endpoint (server-side proxy)
  │              Returns { access_token, refresh_token, ... }
  │
  │  GET /api/v1/users?page=0&size=10
  │  Authorization: Bearer <access_token>
  ▼
API Gateway
  1. Validate JWT signature against Keycloak JWKS
  2. Extract roles via KeycloakJwtConverter
  3. Check .requestMatchers("/api/v1/users/**").hasAnyRole("USER","ADMIN")
  4. Add X-Gateway-Secret header
  5. Forward to http://localhost:2026/api/v1/users?page=0&size=10
  │
  ▼
User Service
  1. GatewaySecretFilter — verify X-Gateway-Secret header (403 if missing)
  2. SecurityConfig — re-validate JWT (ROLE_USER or ROLE_ADMIN required)
  3. UserController.getAll(Pageable)
  4. UserService.getAll() — check Hazelcast cache first
  5. If cache miss → JPA → MySQL → cache result
  6. Return Page<UserDto>
```

---

## 5. Security Model

```
                    ┌─────────────────────────────┐
                    │         Keycloak             │
                    │   microservices-realm        │
                    │   Issues & signs JWTs        │
                    └──────────┬──────────────────┘
                               │ JWKS endpoint
              ┌────────────────┼────────────────┐
              ▼                                 ▼
       API Gateway                        User Service
  Validates JWT (Layer 1)           Re-validates JWT (Layer 2)
  Role-based route guard            GatewaySecretFilter
  CORS enforcement                  Role-based method guard
  Token proxy (no CORS issue)
```

**KeycloakJwtConverter** — used in every service:
- Reads `realm_access.roles` → `ROLE_ADMIN`, `ROLE_USER`
- Reads `resource_access.<clientId>.roles` → client-scoped roles
- Sets principal to `preferred_username` claim

---

## 6. Proposed Architecture — 3 New Services

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│                             CLIENT  :5173                                         │
└────────────────────────────────────┬─────────────────────────────────────────────┘
                                     │
                                     ▼
┌──────────────────────────────────────────────────────────────────────────────────┐
│                          API GATEWAY  :2027                                       │
│  + Micrometer Tracing (OTel) → injects W3C TraceContext headers on all requests    │
│  + /actuator/prometheus exposed for Prometheus scraping                           │
│  New routes:                                                                      │
│    /api/v1/products/** ──► product-service  :2028                                 │
│    /api/v1/orders/**   ──► order-service    :2029                                 │
└──────┬──────────────────────┬────────────────────┬────────────────────────────────┘
       │                      │                    │
       ▼                      ▼                    ▼
┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────────────────────┐
│  USER SERVICE   │  │ PRODUCT SERVICE │  │         ORDER SERVICE  :2029         │
│  :2026          │  │ :2028           │  │                                      │
│                 │  │                 │  │  • Owns order lifecycle:             │
│  MySQL          │  │  MySQL          │  │    PENDING → CONFIRMED → SHIPPED →   │
│  Hazelcast      │  │  Hazelcast      │  │    DELIVERED | CANCELLED             │
│  JWT validation │  │  JWT validation │  │  • MySQL  order_db                   │
│  Swagger UI     │  │  Swagger UI     │  │  • Calls User Service  (Feign)       │
│                 │◄─┼─────────────────┼──│  • Calls Product Service (Feign)    │
│                 │  │◄────────────────┼──│  • Resilience4j circuit breaker      │
└─────────────────┘  └─────────────────┘  │  • Publishes to Kafka topic:         │
                                          │    order-events                      │
                                          └──────────────┬───────────────────────┘
                                                         │  Kafka publish
                                                         ▼
                                          ┌─────────────────────────────────────┐
                             Kafka        │    NOTIFICATION SERVICE  :2030       │
                             :9092        │                                      │
                                          │  • Consumes order-events topic       │
                                          │  • Sends emails (Spring Mail/SMTP)   │
                                          │  • MongoDB — notification history    │
                                          │  • No HTTP API (fully event-driven)  │
                                          └─────────────────────────────────────┘

─────────────────────────  CROSS-CUTTING LAYER  ──────────────────────────────────

  All services expose:
  ┌─────────────────────────────────────────────────────────────────────────────┐
  │  Micrometer Tracing (OTel)   ──► OTel Collector ──► Tempo  :3200            │
  │  Micrometer + Prometheus     ──► Prometheus :9090  ──► Grafana :3000        │
  │  Logback JSON (Logstash enc.)──► Loki  :3100                                │
  │                                                    traceId in every line    │
  │  All three pillars queryable in ONE Grafana UI  (LGTM Stack)                │
  └─────────────────────────────────────────────────────────────────────────────┘
```

---

## 7. New Services — Responsibilities

### Product Service `:2028`

| Concern | Decision |
|---------|----------|
| Database | MySQL — `product_db` |
| Cache | Hazelcast — `products` (TTL 10m), `productsPage` (TTL 5m) |
| API | `GET/POST/PUT/DELETE /api/v1/products/**` |
| Domain | `Product(id, name, description, price, stock, category, version)` |
| Locking | `@Version` optimistic locking on stock field |
| Security | OAuth2 resource server + GatewaySecretFilter |
| Pattern | Same structure as user-service |

**Endpoints:**

| Method | Path | Role | Description |
|--------|------|------|-------------|
| `GET` | `/api/v1/products` | USER, ADMIN | Paginated product list |
| `GET` | `/api/v1/products/{id}` | USER, ADMIN | Get product by ID |
| `POST` | `/api/v1/products` | ADMIN | Create product |
| `PUT` | `/api/v1/products/{id}` | ADMIN | Update product |
| `DELETE` | `/api/v1/products/{id}` | ADMIN | Delete product |

---

### Order Service `:2029`

| Concern | Decision |
|---------|----------|
| Database | MySQL — `order_db` (`orders`, `order_items`, `order_status_history`, `addresses`) |
| Domain | `Order` + `OrderItem` + `OrderStatusHistory` + `Address` |
| API | 14 endpoints across order placement, lifecycle management, history, returns |
| Sync calls | **OpenFeign** → user-service (validate user + get email), product-service (price + stock check + deduct) |
| Async | **Kafka producer** → `order-events` topic on every status transition |
| Resilience | Feign retry (3 attempts, 500ms backoff) + Resilience4j CircuitBreaker + fallback |
| Security | OAuth2 resource server + GatewaySecretFilter |

---

#### Domain Model

```
┌─────────────────────────────────────────────────────────────────────────┐
│  Order                                                                   │
│  ─────────────────────────────────────────────────────────────────────  │
│  id              BIGINT PK AUTO_INCREMENT                               │
│  order_number    VARCHAR(20) UNIQUE  e.g. ORD-2026-00123                │
│  user_id         BIGINT NOT NULL     (FK → user-service, no DB FK)      │
│  user_email      VARCHAR(255)        snapshot at order time             │
│  status          ENUM  (see lifecycle below)                            │
│  payment_status  ENUM  PENDING | PAID | FAILED | REFUNDED              │
│  payment_method  ENUM  CREDIT_CARD | DEBIT_CARD | UPI | NET_BANKING    │
│  subtotal        DECIMAL(10,2)       sum of item totals before tax      │
│  tax_amount      DECIMAL(10,2)       GST / VAT                         │
│  shipping_amount DECIMAL(10,2)       delivery charge                   │
│  discount_amount DECIMAL(10,2)       coupon / promo deduction          │
│  total_amount    DECIMAL(10,2)       final amount charged               │
│  coupon_code     VARCHAR(50)         applied coupon (nullable)          │
│  tracking_number VARCHAR(100)        courier tracking (nullable)        │
│  notes           VARCHAR(500)        customer delivery notes            │
│  version         BIGINT              optimistic locking                 │
│  created_at      TIMESTAMP                                              │
│  confirmed_at    TIMESTAMP           nullable                           │
│  shipped_at      TIMESTAMP           nullable                           │
│  delivered_at    TIMESTAMP           nullable                           │
│  cancelled_at    TIMESTAMP           nullable                           │
└──────────────────────────────┬──────────────────────────────────────────┘
                               │ 1 : N
                               ▼
┌─────────────────────────────────────────────────────────────────────────┐
│  OrderItem                                                               │
│  ─────────────────────────────────────────────────────────────────────  │
│  id              BIGINT PK                                              │
│  order_id        BIGINT FK → orders.id                                  │
│  product_id      BIGINT     (FK → product-service, no DB FK)            │
│  product_name    VARCHAR(255)  snapshot at order time                   │
│  product_sku     VARCHAR(100)  snapshot at order time                   │
│  quantity        INT NOT NULL                                            │
│  unit_price      DECIMAL(10,2) snapshot at order time                  │
│  total_price     DECIMAL(10,2) quantity × unit_price                   │
└─────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│  OrderStatusHistory                                                      │
│  ─────────────────────────────────────────────────────────────────────  │
│  id              BIGINT PK                                              │
│  order_id        BIGINT FK → orders.id                                  │
│  from_status     ENUM                                                   │
│  to_status       ENUM                                                   │
│  reason          VARCHAR(500)  admin note / system reason               │
│  changed_by      VARCHAR(255)  username from JWT principal              │
│  changed_at      TIMESTAMP                                              │
└─────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│  Address  (embedded in Order as shipping_address_* + billing_address_*) │
│  ─────────────────────────────────────────────────────────────────────  │
│  full_name   VARCHAR(200)                                               │
│  phone       VARCHAR(20)                                                │
│  street      VARCHAR(300)                                               │
│  city        VARCHAR(100)                                               │
│  state       VARCHAR(100)                                               │
│  zip_code    VARCHAR(20)                                                │
│  country     VARCHAR(100)                                               │
└─────────────────────────────────────────────────────────────────────────┘
```

> **Why snapshot product name/price?** Products can be renamed or repriced after an order is placed.
> The order must always reflect what the customer actually paid for — never the current product state.

---

#### Order Lifecycle — Full State Machine

```
                        POST /api/v1/orders
                               │
                               ▼
                       ┌───────────────┐
                       │    PENDING     │  Order placed; payment not yet confirmed
                       └───────┬───────┘
                               │
              ┌────────────────┴────────────────┐
              │ payment success                  │ payment failed
              ▼                                  ▼
     ┌─────────────────┐               ┌──────────────────┐
     │   CONFIRMED      │               │  PAYMENT_FAILED   │  ──► User retries payment
     │  Payment done;   │               └──────────────────┘      or order auto-expires
     │  warehouse notif.│
     └────────┬─────────┘
              │  warehouse picks & packs
              ▼
     ┌─────────────────┐
     │   PROCESSING     │  Warehouse is preparing the shipment
     └────────┬─────────┘
              │  handed to courier
              ▼
     ┌─────────────────┐
     │    SHIPPED       │  Tracking number assigned; in transit
     └────────┬─────────┘
              │  last-mile delivery
              ▼
     ┌─────────────────┐
     │ OUT_FOR_DELIVERY │  Courier out for delivery today
     └────────┬─────────┘
              │  customer receives
              ▼
     ┌─────────────────┐
     │   DELIVERED      │  ✅ Terminal success state
     └────────┬─────────┘
              │  customer requests return (within 7 days)
              ▼
     ┌─────────────────┐
     │ RETURN_REQUESTED │
     └────────┬─────────┘
              │  warehouse receives returned goods
              ▼
     ┌─────────────────┐        ┌──────────────┐
     │    RETURNED      │──────►│   REFUNDED    │  ✅ Terminal state
     └─────────────────┘        └──────────────┘

  CANCELLATION rules:
  ┌──────────────────────────────────────────────────────────────────┐
  │ PENDING         ──► CANCELLED   USER or ADMIN (no stock restored) │
  │ CONFIRMED       ──► CANCELLED   USER or ADMIN (stock restored)    │
  │ PROCESSING      ──► CANCELLED   ADMIN only    (stock restored)    │
  │ SHIPPED         ──► ✗ Cannot cancel (must use RETURN flow)        │
  │ DELIVERED       ──► ✗ Cannot cancel (must use RETURN flow)        │
  └──────────────────────────────────────────────────────────────────┘
```

**Valid status transitions table:**

| Current Status | Allowed Next Status | Who Can Trigger |
|---------------|--------------------|--------------------|
| `PENDING` | `CONFIRMED`, `PAYMENT_FAILED`, `CANCELLED` | System / ADMIN / USER |
| `PAYMENT_FAILED` | `CONFIRMED` (retry), `CANCELLED` | USER / ADMIN |
| `CONFIRMED` | `PROCESSING`, `CANCELLED` | ADMIN / USER |
| `PROCESSING` | `SHIPPED`, `CANCELLED` | ADMIN only |
| `SHIPPED` | `OUT_FOR_DELIVERY`, `DELIVERED` | ADMIN only |
| `OUT_FOR_DELIVERY` | `DELIVERED` | ADMIN only |
| `DELIVERED` | `RETURN_REQUESTED` | USER (within return window) |
| `RETURN_REQUESTED` | `RETURNED` | ADMIN only |
| `RETURNED` | `REFUNDED` | ADMIN only |
| `CANCELLED` | — | Terminal |
| `REFUNDED` | — | Terminal |

---

#### Endpoints — Full Reference

---

##### `POST /api/v1/orders` — Place Order
**Role:** `USER`

**What happens internally:**
1. Call user-service via Feign → validate user exists, fetch email
2. For each item: call product-service → validate product exists, get current price, check stock ≥ qty
3. Calculate subtotal, tax (18% GST), shipping (free above ₹500, else ₹50), apply coupon discount
4. Deduct stock for each item via product-service Feign call
5. Persist Order + OrderItems with status `PENDING`
6. Publish `ORDER_CREATED` event to Kafka `order-events` topic
7. Return 201 Created with full order response

**Request body:**
```json
{
  "items": [
    { "productId": 1, "quantity": 2 },
    { "productId": 5, "quantity": 1 }
  ],
  "shippingAddress": {
    "fullName":  "Alice Smith",
    "phone":     "+91-9876543210",
    "street":    "42 MG Road, Apt 3B",
    "city":      "Bangalore",
    "state":     "Karnataka",
    "zipCode":   "560001",
    "country":   "India"
  },
  "paymentMethod": "CREDIT_CARD",
  "couponCode":    "SAVE10",
  "notes":         "Leave at door if not home"
}
```

**Response `201 Created`:**
```json
{
  "id":            123,
  "orderNumber":   "ORD-2026-00123",
  "userId":        45,
  "userEmail":     "alice@example.com",
  "status":        "PENDING",
  "paymentStatus": "PENDING",
  "paymentMethod": "CREDIT_CARD",
  "items": [
    {
      "id":           1,
      "productId":    1,
      "productName":  "Wireless Headphones",
      "productSku":   "WH-001",
      "quantity":     2,
      "unitPrice":    49.99,
      "totalPrice":   99.98
    },
    {
      "id":           2,
      "productId":    5,
      "productName":  "USB-C Cable 2m",
      "productSku":   "UC-005",
      "quantity":     1,
      "unitPrice":    12.99,
      "totalPrice":   12.99
    }
  ],
  "subtotal":        112.97,
  "taxAmount":       20.33,
  "shippingAmount":  0.00,
  "discountAmount":  11.30,
  "totalAmount":     122.00,
  "couponCode":      "SAVE10",
  "shippingAddress": { "fullName": "Alice Smith", "street": "42 MG Road, Apt 3B", "..." },
  "trackingNumber":  null,
  "notes":           "Leave at door if not home",
  "estimatedDelivery": "2026-05-30T00:00:00Z",
  "createdAt":       "2026-05-27T10:00:00Z"
}
```

**Error responses:**

| HTTP | Scenario |
|------|----------|
| `400` | Missing required fields, invalid quantity (≤ 0), empty items list |
| `404` | Product not found (product-service returns 404) |
| `409` | Insufficient stock for one or more items |
| `422` | Invalid coupon code |
| `503` | product-service or user-service circuit open |

---

##### `GET /api/v1/orders` — All Orders (Paginated)
**Role:** `ADMIN`  
**Query params:** `page`, `size`, `sort`, `status` (filter), `userId` (filter), `from` (date), `to` (date)

```
GET /api/v1/orders?page=0&size=20&sort=createdAt,desc&status=PENDING
```

**Response `200 OK`:**
```json
{
  "content": [ { "id": 123, "orderNumber": "ORD-2026-00123", "status": "PENDING", "totalAmount": 122.00, "..." } ],
  "pageNumber": 0,
  "pageSize":   20,
  "totalElements": 154,
  "totalPages":    8,
  "first": true,
  "last":  false
}
```

---

##### `GET /api/v1/orders/my` — Current User's Orders
**Role:** `USER`  
**Query params:** `page`, `size`, `sort`, `status` (filter)

> User ID extracted from JWT `sub` claim — no userId param allowed (prevents data leakage).

**Response `200 OK`:** Same paginated structure as above, scoped to authenticated user only.

---

##### `GET /api/v1/orders/{id}` — Order by ID
**Role:** `USER`, `ADMIN`

> **Authorization rule:** USER can only fetch their own orders.  
> If `order.userId ≠ JWT userId` AND role is not ADMIN → return `403 Forbidden`.

**Response `200 OK`:** Full order object (same as POST response above).

**Error responses:**

| HTTP | Scenario |
|------|----------|
| `403` | USER trying to view another user's order |
| `404` | Order not found |

---

##### `GET /api/v1/orders/number/{orderNumber}` — Order by Order Number
**Role:** `USER`, `ADMIN`

> Used when customer has the order number from a confirmation email but not the internal ID.  
> Same authorization rule as GET by ID.

```
GET /api/v1/orders/number/ORD-2026-00123
```

**Response `200 OK`:** Full order object.

---

##### `GET /api/v1/orders/{id}/items` — Order Items
**Role:** `USER`, `ADMIN`

> Returns only the line items — used by the frontend order detail page to show what was ordered.

**Response `200 OK`:**
```json
[
  {
    "id":          1,
    "productId":   1,
    "productName": "Wireless Headphones",
    "productSku":  "WH-001",
    "quantity":    2,
    "unitPrice":   49.99,
    "totalPrice":  99.98
  }
]
```

---

##### `GET /api/v1/orders/{id}/history` — Status Change History
**Role:** `USER`, `ADMIN`

> Full audit trail of every status transition — shown in the customer's order tracking timeline.

**Response `200 OK`:**
```json
[
  {
    "fromStatus":  null,
    "toStatus":    "PENDING",
    "reason":      "Order placed by customer",
    "changedBy":   "alice",
    "changedAt":   "2026-05-27T10:00:00Z"
  },
  {
    "fromStatus":  "PENDING",
    "toStatus":    "CONFIRMED",
    "reason":      "Payment captured successfully",
    "changedBy":   "system",
    "changedAt":   "2026-05-27T10:05:22Z"
  },
  {
    "fromStatus":  "CONFIRMED",
    "toStatus":    "PROCESSING",
    "reason":      "Assigned to warehouse team",
    "changedBy":   "admin-user",
    "changedAt":   "2026-05-27T11:30:00Z"
  }
]
```

---

##### `POST /api/v1/orders/{id}/confirm-payment` — Confirm Payment
**Role:** `ADMIN`

> Triggered by payment gateway webhook or manual admin confirmation.  
> Transitions order from `PENDING` / `PAYMENT_FAILED` → `CONFIRMED`.  
> Publishes `ORDER_CONFIRMED` Kafka event → Notification Service sends confirmation email.

**Request body:**
```json
{
  "paymentReference": "PAY-2026-XYZ987",
  "notes":            "Payment captured via Razorpay"
}
```

**Response `200 OK`:** Updated order object with `status: CONFIRMED`, `paymentStatus: PAID`.

**Error responses:**

| HTTP | Scenario |
|------|----------|
| `400` | Order not in PENDING or PAYMENT_FAILED state |
| `404` | Order not found |

---

##### `POST /api/v1/orders/{id}/process` — Mark as Processing
**Role:** `ADMIN`

> Warehouse has started picking and packing. Transitions `CONFIRMED` → `PROCESSING`.

**Request body:**
```json
{
  "notes": "Assigned to packer: John Doe, Bay 12"
}
```

**Response `200 OK`:** Updated order with `status: PROCESSING`.

---

##### `POST /api/v1/orders/{id}/ship` — Mark as Shipped
**Role:** `ADMIN`

> Courier has picked up the package. Transitions `PROCESSING` → `SHIPPED`.  
> Tracking number is mandatory. Publishes `ORDER_SHIPPED` Kafka event.

**Request body:**
```json
{
  "trackingNumber": "FEDEX-1234567890",
  "courierName":    "FedEx",
  "notes":          "Expected delivery in 3 business days"
}
```

**Response `200 OK`:** Updated order with `status: SHIPPED`, `trackingNumber` populated.

**Error responses:**

| HTTP | Scenario |
|------|----------|
| `400` | Order not in PROCESSING state, or trackingNumber blank |
| `404` | Order not found |

---

##### `POST /api/v1/orders/{id}/out-for-delivery` — Mark as Out for Delivery
**Role:** `ADMIN`

> Courier is on the last-mile route today. Transitions `SHIPPED` → `OUT_FOR_DELIVERY`.

**Request body:**
```json
{
  "notes": "Driver: Kumar, Vehicle: KA-01-AB-1234"
}
```

**Response `200 OK`:** Updated order with `status: OUT_FOR_DELIVERY`.

---

##### `POST /api/v1/orders/{id}/deliver` — Mark as Delivered
**Role:** `ADMIN`

> Package confirmed received. Transitions `OUT_FOR_DELIVERY` → `DELIVERED`.  
> Publishes `ORDER_DELIVERED` Kafka event → Notification Service sends delivery email.

**Request body:**
```json
{
  "notes":     "Signed by: Alice Smith",
  "signature": "base64-encoded-signature-image"
}
```

**Response `200 OK`:** Updated order with `status: DELIVERED`, `deliveredAt` timestamp set.

---

##### `POST /api/v1/orders/{id}/cancel` — Cancel Order
**Role:** `USER`, `ADMIN`

> Cancels an order. Cancellation rules enforced by state machine.  
> If stock was already deducted (status was CONFIRMED or beyond), stock is restored via product-service Feign call.  
> Publishes `ORDER_CANCELLED` Kafka event.

**Request body:**
```json
{
  "reason": "Changed my mind / Found a better deal / Wrong item ordered"
}
```

**Response `200 OK`:** Updated order with `status: CANCELLED`, `cancelledAt` timestamp set.

**Error responses:**

| HTTP | Scenario |
|------|----------|
| `400` | Order already SHIPPED, DELIVERED, or in terminal state |
| `403` | USER trying to cancel someone else's order |
| `404` | Order not found |
| `409` | ADMIN-only cancellation required (order in PROCESSING state) |

---

##### `POST /api/v1/orders/{id}/return` — Request Return
**Role:** `USER`

> Customer initiates return. Only allowed within 7 days of delivery.  
> Transitions `DELIVERED` → `RETURN_REQUESTED`.  
> Publishes `ORDER_RETURN_REQUESTED` Kafka event.

**Request body:**
```json
{
  "reason":      "DAMAGED | WRONG_ITEM | NOT_AS_DESCRIBED | CHANGED_MIND",
  "description": "The headphones arrived with a cracked left ear cup",
  "items": [
    { "orderItemId": 1, "quantity": 2, "reason": "DAMAGED" }
  ]
}
```

**Response `200 OK`:** Updated order with `status: RETURN_REQUESTED`.

**Error responses:**

| HTTP | Scenario |
|------|----------|
| `400` | Order not in DELIVERED state |
| `409` | Return window expired (> 7 days since delivery) |
| `403` | USER trying to return someone else's order |

---

##### `PUT /api/v1/orders/{id}/status` — Generic Status Update (Admin)
**Role:** `ADMIN`

> Emergency override endpoint for admins. Validates transition against the allowed transition table.  
> Use specific endpoints (ship, deliver, cancel) where possible — this is a fallback.

**Request body:**
```json
{
  "status": "RETURNED",
  "reason": "Warehouse confirmed receipt of returned goods"
}
```

**Response `200 OK`:** Updated order.

**Error responses:**

| HTTP | Scenario |
|------|----------|
| `400` | Invalid transition (e.g. DELIVERED → PENDING) |
| `404` | Order not found |

---

#### Complete Endpoint Summary

| Method | Path | Role | Status Transition | Kafka Event |
|--------|------|------|------------------|-------------|
| `POST` | `/api/v1/orders` | USER | → PENDING | `ORDER_CREATED` |
| `GET` | `/api/v1/orders` | ADMIN | — | — |
| `GET` | `/api/v1/orders/my` | USER | — | — |
| `GET` | `/api/v1/orders/{id}` | USER, ADMIN | — | — |
| `GET` | `/api/v1/orders/number/{orderNumber}` | USER, ADMIN | — | — |
| `GET` | `/api/v1/orders/{id}/items` | USER, ADMIN | — | — |
| `GET` | `/api/v1/orders/{id}/history` | USER, ADMIN | — | — |
| `POST` | `/api/v1/orders/{id}/confirm-payment` | ADMIN | PENDING → CONFIRMED | `ORDER_CONFIRMED` |
| `POST` | `/api/v1/orders/{id}/process` | ADMIN | CONFIRMED → PROCESSING | `ORDER_PROCESSING` |
| `POST` | `/api/v1/orders/{id}/ship` | ADMIN | PROCESSING → SHIPPED | `ORDER_SHIPPED` |
| `POST` | `/api/v1/orders/{id}/out-for-delivery` | ADMIN | SHIPPED → OUT_FOR_DELIVERY | `ORDER_OUT_FOR_DELIVERY` |
| `POST` | `/api/v1/orders/{id}/deliver` | ADMIN | OUT_FOR_DELIVERY → DELIVERED | `ORDER_DELIVERED` |
| `POST` | `/api/v1/orders/{id}/cancel` | USER, ADMIN | Any cancellable → CANCELLED | `ORDER_CANCELLED` |
| `POST` | `/api/v1/orders/{id}/return` | USER | DELIVERED → RETURN_REQUESTED | `ORDER_RETURN_REQUESTED` |
| `PUT` | `/api/v1/orders/{id}/status` | ADMIN | Any valid transition | Corresponding event |

---

#### Place Order — Internal Flow

```
CLIENT
  │  POST /api/v1/orders  { items, shippingAddress, paymentMethod, couponCode }
  ▼
API GATEWAY
  │  Validate JWT → extract userId, roles
  │  Add X-Gateway-Secret
  ▼
ORDER SERVICE
  │
  ├─ 1. Feign → USER SERVICE
  │         GET /api/v1/users/{userId}
  │         → validates user exists, fetches userEmail
  │         CircuitBreaker: if DOWN → 503 Service Unavailable
  │
  ├─ 2. For each item: Feign → PRODUCT SERVICE
  │         GET /api/v1/products/{productId}
  │         → validates product exists, snapshots name/sku/price
  │         → checks stock ≥ requested quantity (→ 409 if not)
  │         CircuitBreaker: if DOWN → 503 Service Unavailable
  │
  ├─ 3. Calculate totals
  │         subtotal       = Σ (quantity × unit_price)
  │         tax_amount     = subtotal × 0.18  (18% GST)
  │         shipping       = subtotal > 500 ? 0 : 50
  │         discount       = couponCode valid ? subtotal × coupon.rate : 0
  │         total          = subtotal + tax + shipping - discount
  │
  ├─ 4. For each item: Feign → PRODUCT SERVICE
  │         PUT /api/v1/products/{productId}/stock  { deduct: quantity }
  │         → atomically deducts stock (optimistic locking on product)
  │
  ├─ 5. Persist to MySQL (order_db)
  │         INSERT orders + order_items + order_status_history
  │         order_number = "ORD-" + YEAR + "-" + LPAD(sequence, 5, '0')
  │
  ├─ 6. Publish to Kafka
  │         topic: order-events
  │         key:   orderId
  │         payload: OrderEvent { eventType: ORDER_CREATED, ... }
  │
  └─ 7. Return 201 Created with full OrderResponse
```

---

### Notification Service `:2030`

| Concern | Decision |
|---------|----------|
| Database | MongoDB — `notification_db` (stores notification history) |
| Transport | Kafka consumer only — no HTTP endpoints exposed via gateway |
| Trigger | `order-events` topic |
| Email | Spring Mail (SMTP) |
| Tracing | Kafka consumer extracts W3C traceparent header → continues trace from Order Service |

**Events handled:**

| Event | Action |
|-------|--------|
| `ORDER_CREATED` | Email: "Your order #X has been placed successfully" |
| `ORDER_CONFIRMED` | Email: "Your order #X is confirmed and being prepared" |
| `ORDER_SHIPPED` | Email: "Your order #X has been shipped. Tracking: ..." |
| `ORDER_DELIVERED` | Email: "Your order #X has been delivered" |
| `ORDER_CANCELLED` | Email: "Your order #X has been cancelled" |

---

## 8. Service-to-Service Communication

### Synchronous — OpenFeign (HTTP)

```
Order Service
  │
  ├── UserServiceClient (Feign)
  │     @FeignClient(name = "user-service", url = "${services.user-service.url}")
  │     UserDto getUser(@PathVariable Long id);
  │       → GET http://localhost:2026/api/v1/users/{id}
  │       → Bearer token forwarded via RequestInterceptor
  │
  └── ProductServiceClient (Feign)
        @FeignClient(name = "product-service", url = "${services.product-service.url}")
        ProductDto getProduct(@PathVariable Long id);
        void deductStock(@PathVariable Long id, @RequestBody StockRequest req);
          → GET  http://localhost:2028/api/v1/products/{id}
          → PUT  http://localhost:2028/api/v1/products/{id}/stock
```

**Resilience4j CircuitBreaker on Feign calls:**
```
  Normal   ──► CLOSED   (requests pass through)
  5 failures  ──► OPEN    (fast-fail for 30s, return fallback)
  After 30s   ──► HALF-OPEN (1 probe request)
  Probe OK    ──► CLOSED again
```

**Feign configuration per call:**
```yaml
resilience4j:
  circuitbreaker:
    instances:
      user-service:
        slidingWindowSize: 10
        failureRateThreshold: 50
        waitDurationInOpenState: 30s
  retry:
    instances:
      user-service:
        maxAttempts: 3
        waitDuration: 500ms
```

---

### Asynchronous — Kafka

```
┌────────────────────────────────────────────────────────────────────┐
│                        Kafka Broker  :9092                          │
│                                                                     │
│  Topic: order-events                                                │
│  Partitions: 3   (allows 3 parallel consumers)                     │
│  Replication: 1  (single broker — local dev)                        │
│  Retention: 7 days                                                  │
└───────────────────┬───────────────────────────┬────────────────────┘
                    │  produce                  │  consume
                    │                           │
             Order Service             Notification Service
          (KafkaTemplate)              (group: notification-group)
```

**OrderEvent schema:**
```json
{
  "eventId":    "uuid",
  "eventType":  "ORDER_CREATED | ORDER_CONFIRMED | ORDER_SHIPPED | ORDER_DELIVERED | ORDER_CANCELLED",
  "orderId":    123,
  "userId":     45,
  "userEmail":  "alice@example.com",
  "items": [
    { "productId": 7, "productName": "Widget", "qty": 2, "unitPrice": 19.99 }
  ],
  "totalAmount": 39.98,
  "timestamp":  "2026-05-27T10:00:00Z",
  "traceId":    "abc123def456"
}
```

**Trace propagation through Kafka:**
```
Order Service
  → creates OrderEvent
  → OTel instrumentation injects W3C TraceContext into Kafka message headers:
        traceparent: 00-<traceId>-<spanId>-01
        tracestate:  (optional vendor data)
  → publishes to order-events topic

OTel Collector
  → receives spans from Order Service via OTLP
  → forwards to Tempo

Notification Service
  → receives Kafka message
  → OTel Kafka instrumentation extracts traceparent header automatically
  → creates a new child span linked to the same traceId
  → all log lines from notification processing show the same traceId
  → span sent to OTel Collector → Tempo
```

**Result in Grafana:** One end-to-end trace from gateway → order-service → user-service
→ product-service → Kafka → notification-service, all in a single waterfall view.

---

## 9. Cross-Cutting Concerns

### Dependencies added to every service (including existing ones)

```xml
<!-- ── Distributed Tracing — OpenTelemetry (modern, replaces Brave/Zipkin) ── -->

<!-- Micrometer bridge to OTel SDK — keeps @Observed, @NewSpan annotations working -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-otel</artifactId>
</dependency>
<!-- OTLP exporter — sends spans to OTel Collector (which forwards to Tempo) -->
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-otlp</artifactId>
</dependency>

<!-- ── Prometheus metrics ───────────────────────────────────────────────── -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>

<!-- ── Actuator (health + /actuator/prometheus endpoint) ───────────────── -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>

<!-- ── Structured JSON logging ─────────────────────────────────────────── -->
<dependency>
    <groupId>net.logstash.logback</groupId>
    <artifactId>logstash-logback-encoder</artifactId>
    <version>7.4</version>
</dependency>
```

> **Why `micrometer-tracing-bridge-otel` instead of `micrometer-tracing-bridge-brave`?**
> Brave is the Zipkin tracer library — it only exports to Zipkin. The OTel bridge exports
> via OTLP to any backend (Tempo, Jaeger, Datadog, etc.) and uses the W3C TraceContext
> standard instead of the older B3 format.

### `application.properties` additions for every service

```properties
# ── Actuator ─────────────────────────────────────────────────────────────────
management.endpoints.web.exposure.include=health,info,metrics,prometheus
management.endpoint.health.show-details=always

# ── Tracing — send spans via OTLP to OTel Collector ──────────────────────────
management.tracing.sampling.probability=1.0
management.otlp.tracing.endpoint=http://localhost:4318/v1/traces

# ── Micrometer — tag every metric with the service name ──────────────────────
management.metrics.tags.application=${spring.application.name}
```

### `logback-spring.xml` — structured JSON output

```xml
<configuration>
    <appender name="JSON_CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <!-- Micrometer auto-populates traceId and spanId into MDC -->
            <includeMdcKeyName>traceId</includeMdcKeyName>
            <includeMdcKeyName>spanId</includeMdcKeyName>
        </encoder>
    </appender>
    <root level="INFO">
        <appender-ref ref="JSON_CONSOLE"/>
    </root>
</configuration>
```

**Result — every log line looks like:**
```json
{
  "timestamp": "2026-05-27T10:00:00.123Z",
  "level":     "INFO",
  "service":   "order-service",
  "traceId":   "abc123def456abc1",
  "spanId":    "def456abc123def4",
  "logger":    "com.order.service.OrderService",
  "message":   "Order 123 created for user 45"
}
```

---

## 10. Observability Stack — LGTM

### Why Not Zipkin

| Problem | Detail |
|---------|--------|
| No active development | Last major release 2022; effectively in maintenance mode |
| Separate UI | Cannot correlate traces with your Grafana metrics or logs |
| No native OpenTelemetry | Only understands its own format or B3 headers |
| No log correlation | Cannot click a traceId in a log and jump to the trace |
| Expensive storage | Requires Cassandra or Elasticsearch for production scale |

---

### Tracing Technology Comparison

| | Zipkin | Jaeger | **Grafana Tempo** ✅ |
|---|---|---|---|
| Backed by | Twitter (unmaintained) | CNCF (graduated) | Grafana Labs |
| Standard | B3 (own) | OpenTelemetry | **OpenTelemetry native** |
| Storage | MySQL / Cassandra / ES | Cassandra / ES / Badger | **Object storage — S3 / MinIO / local disk** |
| Cost | Medium | Medium | **Very low** (no indexing, just compressed blobs) |
| Own UI | Yes | Yes | **No — lives inside Grafana** |
| Log correlation | No | No | **Yes — Loki datasource link** |
| Metrics correlation | No | No | **Yes — Prometheus exemplars** |
| Grafana integration | Plugin only | Plugin only | **Native datasource** |
| W3C TraceContext | No | Partial | **Yes** |

**Choice: Grafana Tempo** — single Grafana UI covers all three observability pillars.

---

### The LGTM Stack

```
L — Loki        logs
G — Grafana     unified dashboards
T — Tempo       distributed traces
M — Prometheus  metrics  (Mimir for production scale)
```

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                           LGTM  OBSERVABILITY  STACK                          │
│                                                                               │
│  Services                 Collectors              Backends        UI          │
│  ─────────                ──────────              ────────        ──          │
│                                                                               │
│  Micrometer OTel  ──OTLP──► OTel Collector ──────► Tempo  :3200  ──┐        │
│  (all services)   :4318     :4317 gRPC              (traces)        │        │
│                             :4318 HTTP                              │        │
│                                                                     ▼        │
│  Logback JSON     ──push──► Promtail / Alloy ──────► Loki  :3100 ──► Grafana │
│  (stdout)                                             (logs)        │ :3000  │
│                                                                     │        │
│  /actuator/       ──pull──► Prometheus ────────────────────────────┘        │
│  prometheus               :9090                                              │
│  (all services)            (metrics)                                         │
│                                                                               │
│  ─────────────────────────────────────────────────────────────────────────── │
│  Grafana Datasources:  Prometheus  |  Loki  |  Tempo                         │
│                                                                               │
│  Correlations (click-through):                                                │
│    Prometheus metric spike  ──exemplar──►  Tempo trace                       │
│    Loki log line traceId    ──link──►      Tempo trace                        │
│    Tempo trace              ──link──►      Loki logs for same traceId         │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

### OTel Collector — Why it sits in the middle

```
Without OTel Collector:          With OTel Collector (recommended):
                                 
Service ──OTLP──► Tempo          Service ──OTLP──► OTel Collector ──► Tempo
                                                        │
                                                        ├──► Loki   (log export)
                                                        └──► Any future backend
                                                             (Datadog, Jaeger…)
```

Benefits:
- Services are **decoupled** from the tracing backend
- Swap Tempo for Jaeger without touching any service
- Collector buffers, retries, batches, and samples spans before sending
- One place to add span processors (redact sensitive data, add env tags)

---

### Grafana Dashboards to configure

| Dashboard | Data Sources | What it shows |
|-----------|-------------|---------------|
| Service Health | Prometheus | HTTP 2xx/4xx/5xx rates, p50/p99 latency per service |
| JVM Metrics | Prometheus | Heap usage, GC pause time, thread pools per service |
| Kafka Lag | Prometheus | Consumer group lag — `notification-group` on `order-events` |
| Distributed Traces | Tempo | End-to-end waterfall: gateway → order → user → product → Kafka → notification |
| Log Explorer | Loki | Filter logs by service, level, traceId; drill into errors |
| Order Funnel | Prometheus | PENDING → CONFIRMED → SHIPPED → DELIVERED conversion rates |
| Infrastructure | Prometheus | CPU, memory, pod restarts per K8s namespace |

---

### Trace Propagation — W3C TraceContext (replaces B3)

```
HTTP requests:
  Header: traceparent: 00-<16-byte-traceId>-<8-byte-spanId>-01
  Example: traceparent: 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01

Kafka messages:
  Message header key:   traceparent
  Message header value: 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01
  (OTel Kafka instrumentation injects/extracts this automatically)
```

---

## 11. Kafka Event Design

### Topics

| Topic | Partitions | Producers | Consumers | Purpose |
|-------|-----------|-----------|-----------|---------|
| `order-events` | 3 | order-service | notification-service | All order lifecycle events |

### Consumer Groups

| Group | Service | Behaviour |
|-------|---------|-----------|
| `notification-group` | notification-service | Each partition consumed by one instance; auto-offset-reset=earliest |

### Kafka Infrastructure (K8s)

```yaml
# To be added to deployment/k8s/
# Kafka + Zookeeper NodePorts:
#   Kafka:     9092
#   Zookeeper: 2181
```

### Dead Letter Topic

```
order-events  ──►  notification-service
                   │  on 3 retry failures
                   ▼
             order-events.DLT   (Dead Letter Topic)
             → alert / manual reprocessing
```

---

## 12. Startup Order

```
Step  Service / Infrastructure         Why this order
────  ─────────────────────────────   ───────────────────────────────────────
 1.   MySQL + MongoDB                 Data stores must be up before JPA init
 2.   Keycloak  :30080                Services fetch JWKS on startup
 3.   Kafka + Zookeeper  :9092        Order + Notification need Kafka ready
 4.   serviceregistry  :8761          Services register with Eureka on boot
 5.   OTel Collector  :4317/:4318     Receives spans before services start sending
 6.   Tempo  :3200                    OTel Collector forwards spans here
 7.   Loki  :3100                     Promtail/Alloy ships logs here
 8.   Prometheus  :9090               Scrapes /actuator/prometheus on all services
 9.   Grafana  :3000                  Datasources: Prometheus + Loki + Tempo
10.   user-service  :2026             No inter-service calls; simplest
11.   product-service  :2028          No inter-service calls
12.   notification-service  :2030     Kafka consumer only; no HTTP deps
13.   order-service  :2029            Depends on user + product via Feign
14.   apigateway  :2027               Routes to all services
```

---

## 13. Port Reference

| Service | Port | Protocol | Notes |
|---------|------|----------|-------|
| Service Registry (Eureka) | 8761 | HTTP | Dashboard at `/` |
| API Gateway | 2027 | HTTP | Public edge |
| User Service | 2026 | HTTP | Internal only |
| Product Service | 2028 | HTTP | Internal only |
| Order Service | 2029 | HTTP | Internal only |
| Notification Service | 2030 | HTTP | Actuator only; no business HTTP API |
| MySQL | 30036 | TCP | K8s NodePort |
| MongoDB | 30017 | TCP | K8s NodePort |
| Keycloak | 30080 | HTTP | K8s NodePort |
| Kafka | 9092 | TCP | K8s NodePort |
| Zookeeper | 2181 | TCP | K8s NodePort |
| OTel Collector | 4317 | gRPC | OTLP span ingest (gRPC) |
| OTel Collector | 4318 | HTTP | OTLP span ingest (HTTP) |
| Grafana Tempo | 3200 | HTTP | Trace storage + query API |
| Grafana Loki | 3100 | HTTP | Log storage + query API |
| Prometheus | 9090 | HTTP | Metrics scrape + PromQL UI |
| Grafana | 3000 | HTTP | Unified dashboards (LGTM) |

---

## 14. Build Roadmap

### Phase 1 — Observability Baseline (existing services)
> Add tracing + metrics + structured logging to `user-service` and `apigateway` **before** building new services, so every new service inherits a working observability stack.

- [ ] Add `micrometer-tracing-bridge-otel`, `opentelemetry-exporter-otlp`, `micrometer-registry-prometheus`, `spring-boot-starter-actuator`, `logstash-logback-encoder` to `user-service` and `apigateway`
- [ ] Add `logback-spring.xml` with Logstash encoder + traceId/spanId MDC fields
- [ ] Add actuator + OTLP tracing properties to `application.properties`
- [ ] Deploy LGTM stack via `deployment/k8s/observability.yaml`:
  - OTel Collector (receives OTLP → forwards to Tempo)
  - Grafana Tempo (trace storage)
  - Grafana Loki (log storage) + Promtail (log shipper)
  - Prometheus (metric scraper)
  - Grafana (dashboards — configure Tempo, Loki, Prometheus datasources)
- [ ] Verify end-to-end trace appears in Grafana Tempo for `GET /api/v1/users`
- [ ] Verify log line in Loki contains `traceId` matching Tempo trace

### Phase 2 — Product Service
> Standalone service, no inter-service calls — ideal second service to establish the pattern.

- [ ] Create `product-service` Spring Boot project (same structure as `user-service`)
- [ ] MySQL schema: `products` table
- [ ] CRUD with `@Version` optimistic locking + Hazelcast cache
- [ ] Add route `/api/v1/products/**` to `apigateway/GatewayConfig`
- [ ] Add role rules to `apigateway/SecurityConfig`

### Phase 3 — Order Service
> Introduces Feign, Resilience4j, and Kafka producer.

- [ ] Create `order-service` Spring Boot project
- [ ] MySQL schema: `orders` + `order_items` tables
- [ ] `UserServiceClient` Feign client with CircuitBreaker
- [ ] `ProductServiceClient` Feign client with CircuitBreaker
- [ ] `OrderEventPublisher` — KafkaTemplate to `order-events`
- [ ] Order state machine
- [ ] Add route `/api/v1/orders/**` to gateway

### Phase 4 — Notification Service
> Introduces Kafka consumer, MongoDB, Spring Mail.

- [ ] Create `notification-service` Spring Boot project
- [ ] MongoDB for notification history
- [ ] `OrderEventConsumer` — `@KafkaListener(topics = "order-events", groupId = "notification-group")`
- [ ] W3C TraceContext (traceparent header) extraction from Kafka message headers via OTel instrumentation
- [ ] Spring Mail integration (email templates per event type)
- [ ] Dead Letter Topic handler

### Phase 5 — Infrastructure as Code
> Add new K8s manifests and wire Prometheus scraping.

- [ ] `deployment/k8s/kafka.yaml` — Kafka + Zookeeper
- [ ] `deployment/k8s/observability.yaml` — OTel Collector + Tempo + Loki + Prometheus + Grafana
- [ ] `deployment/k8s/product-service.yaml`
- [ ] `deployment/k8s/order-service.yaml`
- [ ] `deployment/k8s/notification-service.yaml`
- [ ] Prometheus `scrape_configs` for all `/actuator/prometheus` endpoints
- [ ] Grafana datasources + dashboard provisioning

---

## Adding a New Service — Checklist

When adding any future microservice, follow this checklist:

```
□ 1.  Create Spring Boot project with:
         spring-boot-starter-web
         spring-boot-starter-data-jpa  (or data-mongodb)
         spring-cloud-starter-netflix-eureka-client
         spring-boot-starter-oauth2-resource-server
         spring-boot-starter-security
         spring-boot-starter-actuator
         micrometer-tracing-bridge-otel      ← OTel bridge (NOT brave)
         opentelemetry-exporter-otlp         ← sends spans to OTel Collector
         micrometer-registry-prometheus
         logstash-logback-encoder

□ 2.  Copy KeycloakJwtConverter.java — do NOT use default JWT converter

□ 3.  Copy SecurityConfig pattern:
         GatewaySecretFilter (rejects direct calls)
         JWT resource server with KeycloakJwtConverter
         STATELESS session

□ 4.  Add to application.properties:
         spring.application.name=<service-name>
         eureka.client.service-url.defaultZone=http://localhost:8761/eureka
         spring.security.oauth2.resourceserver.jwt.jwk-set-uri=...
         management.otlp.tracing.endpoint=http://localhost:4318/v1/traces
         management.tracing.sampling.probability=1.0
         management.endpoints.web.exposure.include=health,info,metrics,prometheus
         management.metrics.tags.application=${spring.application.name}
         gateway.internal-secret=gw-secret-change-in-prod

□ 5.  Add logback-spring.xml with Logstash encoder

□ 6.  Add route to apigateway/GatewayConfig.java

□ 7.  Add role rules to apigateway/SecurityConfig.java

□ 8.  Add K8s manifest to deployment/k8s/

□ 9.  Add Prometheus scrape target for /actuator/prometheus
```


127.0.0.1 ecommerce.local add in C:\Windows\System32\drivers\etc

  Complete Deployment Steps for Rancher Desktop

  # Step 1 — Build images
  nerdctl build -t ecommerce/apigateway:latest ./services/apigateway
  nerdctl build -t ecommerce/user-service:latest ./services/user-service
  nerdctl build -t ecommerce/product-service:latest ./services/product-service
  nerdctl build -t ecommerce/order-service:latest ./services/order-service

  # Step 2 — Install NGINX ingress controller
  kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.10.1/deploy/static/provider/cloud/deploy.yaml
  kubectl wait --namespace ingress-nginx --for=condition=ready pod --selector=app.kubernetes.io/component=controller --timeout=120s

  # Step 3 — Deploy everything (from ecommerce-platform/ directory)
  cd deployment/k8s/ecommerce-platform
  bash scripts/deploy-all.sh

  # Step 4 — Monitor pods coming up
  kubectl get pods -n ecommerce -w

  # Step 5 — After Keycloak is Running, set up realm + clients
  # Then update secrets and restart gateway + user-service

  Expected startup time: ~5-7 minutes for all pods to reach Running state (MySQL + Kafka + Keycloak are slow to initialize).
  
  
  
    After deploy-all.sh finishes and Keycloak is running:
  1. Go to http://localhost:30080 → Admin console (admin / admin123)
  2. Create realm microservices-realm
  3. Create clients apigateway-client and user-service-client
  4. Copy the generated secrets, encode them:
  
  
    5. Update secret.yaml with real values and re-apply:

  kubectl apply -n ecommerce -f templates/applications/api-gateway/secret.yaml
  kubectl rollout restart deployment/apigateway -n ecommerce
  kubectl rollout restart deployment/user-service -n ecommerce

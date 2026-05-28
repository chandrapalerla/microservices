# Security & Observability — Ecommerce Microservices

## Table of Contents

1. [Security Flow](#security-flow)
   - [OAuth 2.0 Overview](#oauth-20-overview)
   - [Keycloak as Authorization Server](#keycloak-as-authorization-server)
   - [Authorization Code Flow (React App)](#authorization-code-flow-react-app)
   - [Client Credentials Flow (Service-to-Service)](#client-credentials-flow-service-to-service)
   - [JWT Structure and Verification](#jwt-structure-and-verification)
   - [KeycloakJwtConverter — Role Mapping](#keycloakjwtconverter--role-mapping)
   - [X-Gateway-Secret — Two-Layer Protection](#x-gateway-secret--two-layer-protection)
   - [Complete Security Request Flow](#complete-security-request-flow)
2. [Observability Flow](#observability-flow)
   - [The Three Signals](#the-three-signals)
   - [Distributed Tracing — traceId and spanId](#distributed-tracing--traceid-and-spanid)
   - [W3C TraceContext Propagation](#w3c-tracecontext-propagation)
   - [Logs Flow — ELK Stack](#logs-flow--elk-stack)
   - [Metrics Flow — Prometheus and Grafana](#metrics-flow--prometheus-and-grafana)
   - [Traces Flow — OTel Collector and Jaeger](#traces-flow--otel-collector-and-jaeger)
   - [Custom Business Metrics](#custom-business-metrics)
   - [Grafana — Single Pane of Glass](#grafana--single-pane-of-glass)
   - [Observability Access URLs](#observability-access-urls)

---

## Security Flow

### OAuth 2.0 Overview

OAuth 2.0 is an **authorization** framework. It answers: *"What is this user/service allowed to do?"*

Authentication (who you are) is handled by **OpenID Connect (OIDC)**, which is a thin layer on top of OAuth 2.0.

| Concept | Role |
|---|---|
| **Resource Owner** | The user logged into the React app |
| **Authorization Server** | Keycloak — issues tokens after verifying identity |
| **Resource Server** | user-service, order-service, product-service — protect APIs |
| **Client** | React app (public client) or order-service calling user-service (confidential client) |

There are two flows used in this project:

- **Authorization Code Flow** — React app authenticating a human user
- **Client Credentials Flow** — order-service calling user-service machine-to-machine

---

### Keycloak as Authorization Server

Keycloak runs in Kubernetes and is exposed on NodePort `30080`.

```
Realm:       microservices-realm
Token URL:   http://localhost:30080/realms/microservices-realm/protocol/openid-connect/token
JWKS URL:    http://localhost:30080/realms/microservices-realm/protocol/openid-connect/certs
```

Keycloak issues **JWTs (JSON Web Tokens)** that contain:
- User identity claims (`sub`, `email`, `preferred_username`)
- Roles in `realm_access.roles` (e.g., `["USER", "ADMIN"]`)
- Expiry (`exp`), Issuer (`iss`), Audience (`aud`)

Every microservice validates tokens independently by fetching Keycloak's public keys from the **JWKS endpoint** — no shared secret, no database call.

---

### Authorization Code Flow (React App)

This is the standard browser-based login flow for human users.

```
React App                 Keycloak                  API Gateway          Microservice
    |                         |                           |                    |
    |  1. User clicks Login    |                           |                    |
    |------------------------>|                           |                    |
    |                         |                           |                    |
    |  2. Keycloak shows       |                           |                    |
    |     login page           |                           |                    |
    |<------------------------|                           |                    |
    |                         |                           |                    |
    |  3. User enters          |                           |                    |
    |     credentials          |                           |                    |
    |------------------------>|                           |                    |
    |                         |                           |                    |
    |  4. Keycloak returns     |                           |                    |
    |     authorization code   |                           |                    |
    |<------------------------|                           |                    |
    |                         |                           |                    |
    |  5. App exchanges code   |                           |                    |
    |     for tokens           |                           |                    |
    |------------------------>|                           |                    |
    |                         |                           |                    |
    |  6. Keycloak returns     |                           |                    |
    |     access_token (JWT)   |                           |                    |
    |     + refresh_token      |                           |                    |
    |<------------------------|                           |                    |
    |                         |                           |                    |
    |  7. API call with        |                           |                    |
    |     Authorization: Bearer {JWT}                      |                    |
    |-------------------------------------------------------->                  |
    |                         |                           |                    |
    |                         |          8. Gateway validates JWT             |
    |                         |             adds X-Gateway-Secret             |
    |                         |             forwards request                  |
    |                         |                           |------------------->|
    |                         |                           |                    |
    |                         |              9. Service validates JWT         |
    |                         |                 checks X-Gateway-Secret       |
    |                         |                 extracts ROLE_USER/ROLE_ADMIN |
    |                         |                           |                    |
    |  10. API Response        |                           |                    |
    |<--------------------------------------------------------                  |
```

**Why the authorization code?**
The browser never directly receives tokens in the URL. The short-lived code is exchanged server-side (or via PKCE), preventing token leakage in browser history.

---

### Client Credentials Flow (Service-to-Service)

When **order-service** needs to fetch user details from **user-service**, there is no human user involved.

```
order-service                 Keycloak              user-service
     |                           |                       |
     |  POST /token               |                       |
     |  grant_type=client_credentials                     |
     |  client_id=order-service-client                    |
     |  client_secret=***         |                       |
     |-------------------------->|                       |
     |                           |                       |
     |  access_token (JWT)        |                       |
     |<--------------------------|                       |
     |                           |                       |
     |  GET /api/v1/users/{id}    |                       |
     |  Authorization: Bearer {JWT}                       |
     |------------------------------------------------------->
     |                           |                       |
     |                           |   user-service validates JWT via JWKS     |
     |                           |   checks scope/roles                      |
     |                           |                       |
     |  User data response        |                       |
     |<-------------------------------------------------------
```

The service acts as both the **client** (requesting token) and the caller. Keycloak verifies the `client_secret` and issues a token scoped to the service identity.

---

### JWT Structure and Verification

A JWT has three Base64-encoded parts separated by dots:

```
eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ1c2VyMTIzIiwicmVhbG1fYWNjZXNzIjp7InJvbGVzIjpbIlVTRVIiXX19.SIGNATURE
     HEADER                              PAYLOAD                                    SIGNATURE
```

**Header:**
```json
{
  "alg": "RS256",
  "typ": "JWT",
  "kid": "key-id-from-keycloak"
}
```

**Payload (decoded):**
```json
{
  "sub": "user-uuid-from-keycloak",
  "iss": "http://localhost:30080/realms/microservices-realm",
  "aud": ["account"],
  "exp": 1748000000,
  "iat": 1747996400,
  "preferred_username": "john.doe",
  "email": "john.doe@example.com",
  "realm_access": {
    "roles": ["USER", "ADMIN"]
  },
  "resource_access": {
    "my-client": {
      "roles": ["manage-users"]
    }
  }
}
```

**Signature:**
RSA-256 signature using Keycloak's private key. Services verify this using Keycloak's public key fetched from the JWKS endpoint.

**Verification process (per service):**
1. Extract `kid` from JWT header
2. Fetch matching public key from `{JWKS_URL}` (cached)
3. Verify RSA-256 signature
4. Check `exp` (not expired), `iss` (trusted issuer), `aud`
5. Extract `realm_access.roles` → map to Spring Security authorities

No Keycloak network call is needed per request — only the initial public key fetch is cached.

---

### KeycloakJwtConverter — Role Mapping

Spring Security's default JWT converter reads only `scope` and `scp` claims. Keycloak stores roles in `realm_access.roles` — a different structure.

`KeycloakJwtConverter` bridges this gap by extracting roles from the Keycloak-specific claim structure and mapping them to `ROLE_` prefixed `GrantedAuthority` objects that Spring Security understands.

```
JWT payload:                          Spring Security GrantedAuthority:
realm_access.roles: ["USER", "ADMIN"] → ["ROLE_USER", "ROLE_ADMIN"]
```

Two variants are used in this project:

| Variant | Used In | Why |
|---|---|---|
| `KeycloakJwtConverter.reactive()` | user-service SecurityConfig | Runs on WebFlux/reactive stack |
| `KeycloakJwtConverter.blocking()` / `.create()` | apigateway SecurityConfig | Spring Cloud Gateway uses servlet/blocking |

**Without this converter**, `hasRole("USER")` would always return false even with valid tokens because Spring would not find any roles in the JWT.

---

### X-Gateway-Secret — Two-Layer Protection

Every microservice (user-service, order-service, product-service) validates two things:

1. The **JWT** — proves the request comes from an authenticated user
2. The **X-Gateway-Secret header** — proves the request was routed through the API Gateway

```
Without X-Gateway-Secret:            With X-Gateway-Secret:
                                      
User → direct HTTP → user-service:2026  ← BLOCKED
                                      
User → Gateway:2027 → user-service:2026 ← ALLOWED
       (Gateway adds header)
```

**Why this matters:**
Even if a JWT is valid, if a client calls `user-service:2026` directly (bypassing the gateway), it could bypass:
- Gateway-level rate limiting
- Gateway-level request logging
- Centralized auth policies
- IP filtering at the gateway level

The X-Gateway-Secret is a shared secret configured identically in:
- `apigateway/application.yaml` — gateway stamps it on all forwarded requests
- Each microservice's `SecurityConfig` — microservice validates it

```java
// Gateway — stamps the header on every forwarded request
.filter(FilterFunctions.addRequestHeader("X-Gateway-Secret", gatewaySecret))

// Microservice — rejects requests missing the header
.requestMatchers(r -> !gatewaySecret.equals(r.getHeader("X-Gateway-Secret")))
.denyAll()
```

---

### Complete Security Request Flow

```
Browser/React App
      |
      | GET /api/products  (Authorization: Bearer <JWT>)
      |
      v
API Gateway :2027
  ┌─────────────────────────────────────────────────────┐
  │ 1. Verify JWT signature via Keycloak JWKS           │
  │ 2. Check JWT expiry and issuer                      │
  │ 3. Extract roles via KeycloakJwtConverter           │
  │ 4. Check route-level role requirement               │
  │    e.g., /api/admin/** → ROLE_ADMIN                 │
  │ 5. Add X-Gateway-Secret header                      │
  │ 6. Forward request to product-service via Eureka    │
  └─────────────────────────────────────────────────────┘
      |
      | GET /api/products  (Authorization: Bearer <JWT>
      |                     X-Gateway-Secret: <secret>)
      |
      v
product-service :2028 (or user-service :2026, order-service :2029)
  ┌─────────────────────────────────────────────────────┐
  │ 1. Check X-Gateway-Secret header — deny if missing  │
  │ 2. Verify JWT signature again (independent)         │
  │ 3. Extract roles via KeycloakJwtConverter           │
  │ 4. Apply method-level security                      │
  │    e.g., @RequiresAdmin, @PreAuthorize              │
  │ 5. Process business logic                           │
  └─────────────────────────────────────────────────────┘
      |
      v
Response back to client
```

**Defense in depth:** Both layers independently verify the JWT. Compromising one layer does not grant access.

---

## Observability Flow

### The Three Signals

Observability answers: *"What is happening inside your system right now, and why?"*

The three signals each answer a different question:

| Signal | Tool | Question Answered |
|---|---|---|
| **Logs** | ELK Stack (Elasticsearch + Logstash + Kibana) | What happened and when? Full event detail. |
| **Metrics** | Prometheus + Grafana | How is the system performing over time? Trends and thresholds. |
| **Traces** | OpenTelemetry + Jaeger | Which services were involved in this request? Where was time spent? |

These three signals are correlated via the **traceId** — the same ID appears in logs, is tagged on metrics, and is the trace identifier in Jaeger.

---

### Distributed Tracing — traceId and spanId

When a request enters the system, **micrometer-tracing-bridge-otel** automatically generates two identifiers:

| ID | What it represents | Example |
|---|---|---|
| `traceId` | The entire request journey across all services | `4bf92f3577b34da6a3ce929d0e0e4736` |
| `spanId` | One unit of work within one service | `00f067aa0ba902b7` |

A **trace** is a tree of **spans**. Each service call creates a new span, but all spans share the same traceId.

```
traceId: 4bf92f3577b34da6a3ce929d0e0e4736
│
├── span: apigateway          (00f067aa0ba902b7)  duration: 45ms
│   ├── span: user-service    (a2fb4a1d1a96d312)  duration: 12ms
│   └── span: order-service   (b9c7b548de8702a1)  duration: 28ms
│       └── span: user-service (c3d4e5f6a7b8c9d0)  duration: 8ms  ← nested call
```

These IDs are automatically injected into **SLF4J MDC (Mapped Diagnostic Context)** by the OTel bridge. Every log statement from that thread automatically includes them:

```json
{
  "timestamp": "2026-05-28T10:23:45.123Z",
  "level": "INFO",
  "service": "order-service",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "spanId": "b9c7b548de8702a1",
  "message": "Created order id=1001 userId=42 total=2499.00"
}
```

This means you can take a `traceId` from Jaeger and paste it into Kibana to see every log line from every service that handled that specific request.

---

### W3C TraceContext Propagation

When **order-service** calls **user-service** via Feign (HTTP), the traceId must be carried forward so both services appear in the same trace.

micrometer-tracing-bridge-otel automatically injects the **W3C `traceparent` header** into all outgoing HTTP calls:

```
traceparent: 00-4bf92f3577b34da6a3ce929d0e0e4736-b9c7b548de8702a1-01
             ^  ^                                ^                  ^
             version  traceId (128-bit hex)      spanId (64-bit)    flags (sampled)
```

When user-service receives this header, OTel automatically:
1. Extracts the `traceId` from `traceparent`
2. Creates a new child span with a new `spanId`
3. Records that this span's parent is the sender's `spanId`
4. Injects the same `traceId` into its own MDC

No manual code required — the bridge handles this for all services automatically.

```
Order-Service outbound Feign call:
  Headers added automatically:
    traceparent: 00-4bf92f3577b34da6a3ce929d0e0e4736-b9c7b548de8702a1-01

User-Service receives request:
  Extracts traceId: 4bf92f3577b34da6a3ce929d0e0e4736
  Creates new spanId: c3d4e5f6a7b8c9d0
  All logs from user-service for this request now include:
    "traceId": "4bf92f3577b34da6a3ce929d0e0e4736"
    "spanId":  "c3d4e5f6a7b8c9d0"
```

---

### Logs Flow — ELK Stack

#### Stack Components

| Component | Role | NodePort |
|---|---|---|
| **Logstash** | Receives TCP log streams, parses, forwards | 30504 |
| **Elasticsearch** | Stores and indexes JSON log documents | 30920 |
| **Kibana** | Web UI for searching and visualizing logs | 30560 |

#### Log Flow Diagram

```
Microservice (any of 5 services)
     |
     | JSON log via TCP (LogstashTcpSocketAppender)
     | {"timestamp":"...","level":"INFO","service":"order-service",
     |  "traceId":"4bf92f3577b34da6a3ce929d0e0e4736","message":"..."}
     |
     v
Logstash :30504
  ┌──────────────────────────────────────────────────────┐
  │ input { tcp { port => 5044, codec => json_lines } }  │
  │ filter { mutate, grok, date transforms }             │
  │ output { elasticsearch { index => "microservices-%{+YYYY.MM.dd}" } } │
  └──────────────────────────────────────────────────────┘
     |
     v
Elasticsearch :30920
  index: microservices-2026.05.28
  Documents are full JSON objects, indexed by all fields
     |
     v
Kibana :30560
  Discover → filter by service, traceId, level, time range
```

#### Logback Configuration (all services)

Each service has `logback-spring.xml` with:

```xml
<appender name="LOGSTASH" class="net.logstash.logback.appender.LogstashTcpSocketAppender">
    <destination>localhost:30504</destination>
    <encoder class="net.logstash.logback.encoder.LogstashEncoder">
        <includeMdcKeyName>traceId</includeMdcKeyName>
        <includeMdcKeyName>spanId</includeMdcKeyName>
        <customFields>{"service":"order-service"}</customFields>
    </encoder>
    <reconnectionDelay>10 seconds</reconnectionDelay>
</appender>

<appender name="ASYNC_LOGSTASH" class="ch.qos.logback.classic.AsyncAppender">
    <appender-ref ref="LOGSTASH"/>
    <queueSize>512</queueSize>
    <neverBlock>true</neverBlock>    <!-- never slow down the app thread -->
</appender>
```

`LogstashEncoder` formats every log as JSON. `ASYNC_LOGSTASH` wraps it so TCP I/O never blocks the request thread. If Logstash is down, logs queue up to 512 entries then are discarded (not the app).

#### Querying in Kibana

1. Open `http://localhost:30560`
2. Create index pattern `microservices-*`
3. Use Discover with filters:
   - `service: order-service` — logs from one service
   - `traceId: 4bf92f3577b34da6a3ce929d0e0e4736` — all logs for one request
   - `level: ERROR` — all errors across all services
   - Time range: last 15 minutes

---

### Metrics Flow — Prometheus and Grafana

#### Stack Components

| Component | Role | NodePort |
|---|---|---|
| **Actuator `/actuator/prometheus`** | Exposes metrics in Prometheus text format | Per service |
| **Prometheus** | Scrapes metrics on schedule, stores time-series | 30900 |
| **Grafana** | Queries Prometheus, builds dashboards | 30300 |

#### Metrics Flow Diagram

```
Microservices (every 15s, Prometheus scrapes)
  user-service:2026/actuator/prometheus
  order-service:2029/actuator/prometheus
  product-service:2028/actuator/prometheus
  apigateway:2027/actuator/prometheus
  serviceregistry:8761/actuator/prometheus
     |
     | HTTP GET (pull-based)
     v
Prometheus :30900
  Stores time-series: metric_name{label=value} value timestamp
  Examples:
    http_server_requests_seconds_count{uri="/api/v1/orders",method="POST"} 142
    order_created_total 89
    jvm_memory_used_bytes{area="heap"} 234567890
     |
     | PromQL queries
     v
Grafana :30300
  Dashboard panels query Prometheus:
    rate(http_server_requests_seconds_count[5m])  → requests per second
    histogram_quantile(0.95, http_server_requests_seconds_bucket)  → p95 latency
    order_created_total  → total orders placed
```

#### What Prometheus Collects Automatically

Spring Boot Actuator + Micrometer auto-instruments:

| Metric | Description |
|---|---|
| `http_server_requests_seconds` | Latency histogram per HTTP endpoint |
| `jvm_memory_used_bytes` | JVM heap and non-heap usage |
| `jvm_gc_pause_seconds` | GC pause durations |
| `hikaricp_connections_active` | DB connection pool utilization |
| `process_cpu_usage` | CPU usage of the JVM process |
| `logback_events_total` | Log events by level (INFO/WARN/ERROR count) |

These require zero code — just the Actuator + Micrometer dependency.

---

### Traces Flow — OTel Collector and Jaeger

#### Stack Components

| Component | Role | NodePort |
|---|---|---|
| **OTel SDK** (`micrometer-tracing-bridge-otel`) | Auto-instruments HTTP requests, generates spans | — |
| **OTel Collector** | Receives OTLP spans, forwards to Jaeger | 30418 (HTTP), 30417 (gRPC) |
| **Jaeger** | Stores spans, shows flame graphs | 30686 |

#### Trace Flow Diagram

```
Microservice
  OTel SDK auto-creates span for every HTTP request/response
  Span contains: traceId, spanId, parentSpanId, service, operation, duration, status
     |
     | OTLP/HTTP POST to http://localhost:30418/v1/traces
     v
OTel Collector :30418
  Receives OTLP spans
  Can apply sampling, filtering, attribute enrichment
  Exports to backend:
     |
     | otlp → Jaeger
     v
Jaeger :30686
  Stores spans in memory (or Elasticsearch for production)
  UI shows:
    - Trace list: all traces by service and operation
    - Flame graph: timeline of spans within one trace
    - Dependency graph: which services call which
```

#### Application Configuration

Each service sends spans to OTel Collector:

```properties
management.tracing.sampling.probability=1.0
management.otlp.tracing.endpoint=http://localhost:30418/v1/traces
```

`sampling.probability=1.0` means 100% of requests are traced. In production, use `0.1` (10%) to reduce volume.

#### Reading a Trace in Jaeger

1. Open `http://localhost:30686`
2. Select Service (e.g., `order-service`)
3. Click **Find Traces**
4. Click any trace to see the flame graph
5. Filter by `span.kind: server` to see only inbound API calls (not Eureka heartbeats)
6. Use **Operation** dropdown to filter by endpoint (e.g., `POST /api/v1/orders`)

---

### Custom Business Metrics

Automatic metrics tell you about infrastructure health. Custom metrics tell you about business health.

#### UserMetrics (user-service)

```java
Counter userCreated    → "user.created"      — total users registered
Counter userUpdated    → "user.updated"      — total profile updates
Counter userDeleted    → "user.deleted"      — total users deleted
Timer   userFetchTimer → "user.fetch.duration" — latency of user fetch by ID
```

#### OrderMetrics (order-service)

```java
Counter orderCreated    → "order.created"        — total orders placed
Counter orderCancelled  → "order.cancelled"      — total orders cancelled
DistributionSummary orderAmount → "order.total.amount" — INR value distribution
Counter statusTransition → "order.status.transition"  — tagged {from, to} for each state change
```

`DistributionSummary` tracks min/max/mean/percentiles of order amounts — useful for revenue analysis.
`statusTransition` with `{from, to}` tags lets you answer: *"How many orders moved from PENDING to CANCELLED today?"*

#### ProductMetrics (product-service)

```java
Counter productCreated  → "product.created"        — total products added
Counter stockDeducted   → "product.stock.deducted"  — total stock deduction events
Counter stockRestored   → "product.stock.restored"  — total stock restore events
Counter outOfStock      → "product.out.of.stock"    — times a product hit zero stock
```

#### Querying Custom Metrics in Grafana

```promql
# Orders placed per minute
rate(order_created_total[1m])

# Average order value over the last hour
rate(order_total_amount_sum[1h]) / rate(order_total_amount_count[1h])

# Products that went out of stock today
increase(product_out_of_stock_total[24h])

# Order cancellation rate
rate(order_cancelled_total[5m]) / rate(order_created_total[5m])
```

---

### Grafana — Single Pane of Glass

Grafana is the single UI that connects all three observability signals.

#### Auto-Provisioned Datasources

Grafana is configured with three datasources in `deployment/k8s/observability.yml`:

| Datasource | URL | Used For |
|---|---|---|
| **Prometheus** | `http://prometheus:9090` | Metrics and dashboards |
| **Elasticsearch** | `http://elasticsearch:9200` index `microservices-*` | Log search and aggregation |
| **Jaeger** | `http://jaeger:16686` | Trace search and flame graphs |

#### Typical Debugging Workflow Using All Three Signals

**Scenario:** Users report slow checkout on order creation.

```
Step 1 — Metrics (Grafana → Prometheus)
  Look at: http_server_requests_seconds{uri="/api/v1/orders", method="POST"}
  Observation: p95 latency spiked from 120ms to 1800ms at 14:32

Step 2 — Traces (Grafana → Jaeger)
  Filter traces for order-service, POST /api/v1/orders, around 14:32
  Find a slow trace — flame graph shows:
    order-service: 1800ms total
      └─ Feign call to user-service: 1650ms  ← the bottleneck

Step 3 — Logs (Grafana → Elasticsearch)
  Copy traceId from that slow trace: 4bf92f3577b34da6a3ce929d0e0e4736
  Search Kibana: traceId: 4bf92f3577b34da6a3ce929d0e0e4736
  Find: user-service log shows "HikariPool timeout - connection not available"
  
Root cause: DB connection pool exhausted in user-service.
Fix: Increase hikari.maximum-pool-size
```

This cross-signal correlation is only possible because the **same traceId** appears in all three systems.

---

### Observability Access URLs

| Tool | URL | Purpose |
|---|---|---|
| **Kibana** | `http://localhost:30560` | Search and analyze logs |
| **Elasticsearch** | `http://localhost:30920` | Direct REST API (index health, counts) |
| **Prometheus** | `http://localhost:30900` | Raw metrics browser, ad-hoc PromQL |
| **Grafana** | `http://localhost:30300` | Dashboards (admin / admin) |
| **Jaeger** | `http://localhost:30686` | Distributed trace viewer |
| **OTel Collector** | `http://localhost:30418/v1/traces` | OTLP ingest (services send here) |

**Actuator endpoints per service:**

| Service | Port | Prometheus | Health |
|---|---|---|---|
| serviceregistry | 8761 | `:8761/actuator/prometheus` | `:8761/actuator/health` |
| apigateway | 2027 | `:2027/actuator/prometheus` | `:2027/actuator/health` |
| user-service | 2026 | `:2026/actuator/prometheus` | `:2026/actuator/health` |
| order-service | 2029 | `:2029/actuator/prometheus` | `:2029/actuator/health` |
| product-service | 2028 | `:2028/actuator/prometheus` | `:2028/actuator/health` |

**Apply observability infrastructure:**
```powershell
kubectl apply -f deployment/k8s/observability.yml
```

**Verify pods are running:**
```powershell
kubectl get pods -n ecommerce | Select-String -Pattern "elastic|logstash|kibana|prometheus|grafana|jaeger|otel"
```

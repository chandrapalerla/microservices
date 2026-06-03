# Ecommerce Platform — Current State, Gap Analysis & Proposed Architecture

---

## 1. Current Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           CLIENT LAYER                                       │
│                                                                              │
│   ┌──────────────────────────────────────────────────────────────────────┐  │
│   │  React 18 + TypeScript (Vite 6)                                      │  │
│   │  TanStack Query · Axios · React Router v6 · Tailwind CSS v4         │  │
│   │                                                                      │  │
│   │  Public      Shop · ProductDetail · Cart · Login                    │  │
│   │  User        Checkout · Orders · OrderDetail · Profile              │  │
│   │  Admin       Dashboard · Orders · Products · Categories · Users     │  │
│   │                                                                      │  │
│   │  Cart: localStorage only (no persistent server-side cart)           │  │
│   └──────────────────────────────────────────────────────────────────────┘  │
└───────────────────────────────────────┬─────────────────────────────────────┘
                                        │ HTTPS
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         API GATEWAY  :2027                                   │
│                                                                              │
│   Spring Cloud Gateway MVC (Spring Boot 3.5 / Spring Cloud 2025.0.2)       │
│                                                                              │
│   ┌──────────────┐  ┌─────────────────┐  ┌─────────────────────────────┐   │
│   │ Keycloak JWT │  │ Resilience4j CB │  │ Bucket4j Rate Limiter       │   │
│   │ Validation   │  │ per-route (5)   │  │ (in-memory, not distributed)│   │
│   └──────────────┘  └─────────────────┘  └─────────────────────────────┘   │
│                                                                              │
│   Routes:                                                                    │
│   /api/v1/users/**    → user-service:2026                                   │
│   /api/v1/orders/**   → order-service:2029                                  │
│   /api/v1/products/** → product-service:2028                                │
│   /api/v1/categories/**→ product-service:2028                               │
│   /auth/**            → handled locally (Keycloak ROPC bridge)             │
│                                                                              │
│   Filters: GatewaySecretFilter · TokenPropagationFilter · CORS             │
└───────────┬────────────────────────────────────────────────────────────────┘
            │  X-Gateway-Secret  +  Bearer JWT  (Kubernetes ClusterIP DNS)
            │
   ┌─────────┴───────────────────────────────────────────────────┐
   │                                                             │
   ▼                        ▼                                   ▼
┌──────────────┐   ┌──────────────────┐              ┌─────────────────────┐
│ user-service │   │ product-service  │              │  order-service      │
│    :2026     │   │    :2028         │              │    :2029            │
│              │   │                  │              │                     │
│ Spring MVC + │   │ Spring MVC + JPA │              │ Spring MVC + JPA    │
│ WebFlux Sec  │   │ Hazelcast Cache  │              │ Hazelcast Cache     │
│ JPA          │   │ S3/MinIO images  │              │ Feign → product,user│
│ Hazelcast    │   │ FULLTEXT search  │              │ Resilience4j CB+Retry│
│ Kafka prod   │   │ Kafka prod+cons  │              │ Kafka prod+cons     │
│ Keycloak SDK │   │ @Retryable stock │              │ Idempotency keys    │
│              │   │ @Version optlock │              │ @Version optlock    │
│ MySQL        │   │ MySQL            │              │ MySQL               │
│ user_db      │   │ product_db       │              │ order_db            │
│              │   │                  │              │                     │
│ Users        │   │ Products         │              │ Orders              │
│ Roles        │   │ Categories       │              │ OrderItems          │
│ Statuses     │   │ Stock            │              │ StatusHistory       │
│ Sessions     │   │ Images           │              │ Coupons             │
└──────┬───────┘   └────────┬─────────┘              │ OutboxEvents (stub) │
       │                    │                        │ DeadLetter          │
       │                    │                        │ PendingStockRestore │
       │                    │                        └──────────┬──────────┘
       │                    │                                   │
       └──────────────┬─────┴───────────────────────────────────┘
                      │  Kafka (Apache Kafka 3.9 / 1 broker / 3 partitions)
                      ▼
        ┌─────────────────────────────────────────┐
        │  Topics                                  │
        │  user-events    (USER_CREATED, etc.)     │
        │  product-events (STOCK_LOW, etc.)        │
        │  order-events   (ORDER_PLACED, etc.)     │
        │                                          │
        │  ⚠ No consumer for order-events outside  │
        │    order-service (self-consume only)     │
        └─────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│                      IDENTITY  (Keycloak :30080)                             │
│  Realm: microservices-realm  · Roles: USER, ADMIN                           │
│  Client: ecommerce-client (PKCE) · user-service-client (client_credentials)│
└─────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│                         OBSERVABILITY STACK                                  │
│                                                                              │
│  Prometheus ──scrapes /actuator/prometheus──► Grafana                       │
│  Promtail (DaemonSet) ──► Loki ──► Grafana (LogQL + traceId links)         │
│  OTel Collector ──► Tempo + Jaeger ──► Grafana (trace explorer)            │
│  logstash-logback-encoder ──► structured JSON logs with traceId            │
└─────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│                     KUBERNETES CLUSTER (ecommerce ns)                        │
│                                                                              │
│  NGINX Ingress ──► apigateway:2027                                          │
│  HPA on all 4 app services (not on infra)                                   │
│  MySQL PVC 10Gi · Kafka PVC                                                 │
│  Helm chart: ecommerce-platform v1.0.0                                      │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Gap Analysis

### 2.1 Missing Services

| # | Missing Service | Impact | Priority |
|---|---|---|---|
| 1 | **Payment Service** | Payment is only a status enum — no actual gateway (Razorpay, Stripe, etc.), no webhook handling, no refund flow | Critical |
| 2 | **Notification Service** | order-events published to Kafka but no consumer sends emails/SMS/push. Customers get no confirmation. | Critical |
| 3 | **Cart Service** | Cart lives only in browser localStorage — lost on logout, not synced across devices, no abandoned cart recovery | High |
| 4 | **Search Service** | MySQL FULLTEXT is limited — no faceted search, no fuzzy/typo-tolerant search, no relevance ranking | High |
| 5 | **Review & Rating Service** | No product reviews. Core to ecommerce conversion. | Medium |
| 6 | **Recommendation Engine** | No "you may also like" or "frequently bought together". Pure catalogue browsing only. | Medium |
| 7 | **Analytics / Reporting Service** | Admin dashboard page exists but has no real metrics — no sales reports, revenue tracking, conversion funnel | Medium |
| 8 | **Wishlist Service** | No wishlist/save-for-later functionality | Low |

### 2.2 Missing Infrastructure

| # | Missing Component | Impact | Priority |
|---|---|---|---|
| 1 | **Redis** | Rate limiter (Bucket4j) is in-memory per pod — limits don't aggregate across replicas. Session store not shared. | High |
| 2 | **CDN / Object Storage** | MinIO used locally. No CDN for product images — cold loads on every request globally. | High |
| 3 | **Email Provider** | No SMTP / SES / SendGrid integration | High |
| 4 | **MongoDB** | Referenced in CLAUDE.md and cluster config but not actually used by any service | Low |
| 5 | **Elasticsearch / OpenSearch** | Needed for scalable product search with facets, autocomplete, typo tolerance | Medium |
| 6 | **Database Read Replicas** | Single MySQL instance — all reads and writes on one node | Medium |
| 7 | **Distributed Secrets** | K8s Secrets (base64) — no HashiCorp Vault or AWS Secrets Manager | Medium |
| 8 | **Network Policies** | No K8s NetworkPolicy — pods in `ecommerce` ns can reach each other freely | Medium |
| 9 | **DB Backup CronJob** | No automated MySQL backup | Medium |
| 10 | **Kafka > 1 broker** | Single Kafka broker is a SPOF for all async communication | High |

### 2.3 Missing Features (Within Existing Services)

| # | Gap | Service | Priority |
|---|---|---|---|
| 1 | **Transactional Outbox not wired** | order-service | `OutboxEvent` entity exists but the poller is not connected — Kafka publish can silently fail | High |
| 2 | **Return / Refund workflow incomplete** | order-service | `RETURN_REQUESTED → RETURNED → REFUNDED` status path exists but no refund trigger to payment service | High |
| 3 | **Address Book** | user-service | Shipping address hard-coded per order — no saved addresses, no default address | Medium |
| 4 | **Coupon usage tracking** | order-service | Coupon `usageCount` / `maxUsage` fields may not be enforced atomically under concurrency | Medium |
| 5 | **Real-time order tracking** | client + order-service | OrderDetail polls via React Query — no WebSocket/SSE for live status push | Low |
| 6 | **Payment UI** | client | CheckoutPage has payment method selector but no Razorpay/Stripe embedded widget | Critical |
| 7 | **Admin dashboard metrics** | client + services | DashboardPage is a placeholder — no real sales / revenue / traffic charts | Medium |
| 8 | **Distributed rate limiting** | apigateway | Bucket4j is per-pod — with multiple gateway replicas, limits are not enforced globally | High |
| 9 | **CI/CD pipeline** | DevOps | No GitHub Actions / Jenkinsfile — builds and deploys are manual | Medium |

### 2.4 Security Gaps

| # | Gap | Priority |
|---|---|---|
| 1 | Passwords / secrets in plain text in `application.properties` committed to git | Critical |
| 2 | No K8s NetworkPolicy — services can call each other without going through gateway | High |
| 3 | No HTTPS termination config for production (Ingress TLS not configured) | High |
| 4 | No fraud detection on order placement | Medium |
| 5 | No brute-force protection on `/auth/token` beyond rate limiter | Medium |
| 6 | CORS origin in gateway allows all (`*`) in dev — must be locked for prod | Medium |

---

## 3. Proposed Architecture

```
┌──────────────────────────────────────────────────────────────────────────────────────┐
│                                   CLIENT LAYER                                        │
│                                                                                       │
│  ┌──────────────────────────────────────────────────────────────────────────────┐    │
│  │  React 18 + TypeScript (Vite)                                                │    │
│  │                                                                              │    │
│  │  + Wishlist page      + Product Reviews    + Real-time order tracking (SSE) │    │
│  │  + Payment widget     + Address book       + Admin analytics dashboard      │    │
│  │  + Persistent cart    + Recommendation bar + Abandoned cart banner          │    │
│  └──────────────────────────────────────────────────────────────────────────────┘    │
└───────────────────────────────────────────┬──────────────────────────────────────────┘
                                            │ HTTPS / TLS (cert-manager + Let's Encrypt)
                                            ▼
┌──────────────────────────────────────────────────────────────────────────────────────┐
│                              CDN LAYER  (CloudFront / Cloudflare)                     │
│  Static assets (JS/CSS/images) cached at edge                                        │
│  Product images served from S3 via CDN URL                                           │
└───────────────────────────────────────────┬──────────────────────────────────────────┘
                                            │
                                            ▼
┌──────────────────────────────────────────────────────────────────────────────────────┐
│                         API GATEWAY  :2027   (2+ replicas)                            │
│                                                                                       │
│  Spring Cloud Gateway MVC                                                            │
│                                                                                       │
│  ┌─────────────────┐  ┌────────────────────┐  ┌─────────────────────────────────┐   │
│  │  Keycloak JWT   │  │  Resilience4j CB   │  │  Bucket4j Rate Limiter          │   │
│  │  Validation     │  │  per-route         │  │  ► backed by Redis (distributed)│   │
│  └─────────────────┘  └────────────────────┘  └─────────────────────────────────┘   │
│                                                                                       │
│  Routes:                                                                             │
│  /api/v1/users/**       → user-service         /api/v1/cart/**    → cart-service    │
│  /api/v1/orders/**      → order-service        /api/v1/reviews/** → review-service  │
│  /api/v1/products/**    → product-service      /api/v1/wishlist/**→ wishlist-service│
│  /api/v1/categories/**  → product-service      /api/v1/payments/**→ payment-service │
│  /api/v1/search/**      → search-service       /api/v1/notify/**  → notification-svc│
│  /auth/**               → handled locally                                           │
└──────────────────────────────┬───────────────────────────────────────────────────────┘
                               │  X-Gateway-Secret + Bearer JWT
      ┌────────────────────────┼──────────────────────────────────────┐
      │                        │                                      │
      ▼                        ▼                                      ▼
┌─────────────┐   ┌──────────────────┐   ┌────────────────┐   ┌──────────────────────┐
│user-service │   │ product-service  │   │ order-service  │   │  payment-service     │
│   :2026     │   │    :2028         │   │    :2029       │   │    :2030  (NEW)      │
│             │   │                  │   │                │   │                      │
│ + Address   │   │ Delegates heavy  │   │ Outbox wired   │   │ Razorpay / Stripe    │
│   Book      │   │ search queries   │   │ Return/Refund  │   │ COD auto-confirm     │
│             │   │ to search-service│   │ wired to pay   │   │ Webhook handler      │
│ MySQL       │   │ MySQL            │   │ MySQL          │   │ MySQL payment_db     │
│ user_db     │   │ product_db       │   │ order_db       │   │ payments table       │
└──────┬──────┘   └────────┬─────────┘   └───────┬────────┘   └──────────┬───────────┘
       │                   │                      │                       │
       │            ┌──────┴──────┐               │                       │
       │            ▼             │               │                       │
       │   ┌──────────────────┐   │               │                       │
       │   │ search-service   │   │               │                       │
       │   │    :2031  (NEW)  │   │               │                       │
       │   │                  │   │               │                       │
       │   │ Elasticsearch /  │   │               │                       │
       │   │ OpenSearch       │   │               │                       │
       │   │ Facets, fuzzy,   │   │               │                       │
       │   │ autocomplete     │   │               │                       │
       │   └──────────────────┘   │               │                       │
       │                          │               │                       │
       ▼           ──────────▼────┴───────────────┴───────────▼──────────┘
  ┌─────────────────────────────────────────────────────────────────────────────────┐
  │                     Apache Kafka  (3 brokers / 3 partitions each)               │
  │                                                                                  │
  │  user-events       USER_CREATED, USER_UPDATED, USER_DEACTIVATED                │
  │  product-events    PRODUCT_CREATED, STOCK_LOW, STOCK_OUT, STOCK_DEDUCTED       │
  │  order-events      ORDER_PLACED, ORDER_CONFIRMED, ORDER_SHIPPED, etc.          │
  │  payment-events    PAYMENT_SUCCESS, PAYMENT_FAILED, REFUND_INITIATED  (NEW)    │
  │  notification-events EMAIL_SEND, SMS_SEND, PUSH_SEND                  (NEW)    │
  │                                                                                  │
  │  Consumers:                                                                     │
  │   notification-service ◄── order-events + payment-events + user-events        │
  │   payment-service      ◄── order-events  (ORDER_PLACED)                        │
  │   order-service        ◄── payment-events (PAYMENT_SUCCESS / FAILED)           │
  │   search-service       ◄── product-events (index sync)                         │
  │   recommendation-svc   ◄── order-events  (purchase history)          (NEW)    │
  └──────────────────────────────────────────────────┬──────────────────────────────┘
                                                     │
                         ┌───────────────────────────┼────────────────────────────┐
                         ▼                           ▼                            ▼
              ┌──────────────────┐     ┌─────────────────────┐      ┌─────────────────────┐
              │notification-svc  │     │  cart-service (NEW) │      │ review-service (NEW)│
              │    :2032  (NEW)  │     │     :2033           │      │     :2034           │
              │                  │     │                     │      │                     │
              │ Email → SendGrid │     │ Redis (cart store)  │      │ MongoDB reviews_db  │
              │ SMS  → Twilio    │     │ Persistent cart     │      │ Star ratings        │
              │ Push → Firebase  │     │ Sync across devices │      │ Text reviews        │
              │                  │     │ Abandoned cart logic│      │ Verified purchase   │
              └──────────────────┘     └─────────────────────┘      └─────────────────────┘

              ┌──────────────────┐     ┌─────────────────────┐      ┌─────────────────────┐
              │ wishlist-service │     │recommendation-svc   │      │ analytics-service   │
              │  :2035  (NEW)    │     │    :2036  (NEW)     │      │   :2037  (NEW)      │
              │                  │     │                     │      │                     │
              │ Redis or MongoDB │     │ Collab. filtering   │      │ Sales reports       │
              │ Save for later   │     │ "You may also like" │      │ Revenue tracking    │
              │ Price drop alert │     │ Event-driven model  │      │ Conversion funnel   │
              └──────────────────┘     └─────────────────────┘      └─────────────────────┘

┌──────────────────────────────────────────────────────────────────────────────────────┐
│                              DATA LAYER                                               │
│                                                                                       │
│  ┌─────────────────────────────────────────────────────────────────────────────┐    │
│  │  MySQL 8 (per-service DB isolation)                                         │    │
│  │  Primary + Read Replica per service (user_db, product_db, order_db,        │    │
│  │                                       payment_db)                           │    │
│  │  Flyway migrations on every service startup                                 │    │
│  │  Daily automated backup CronJob → S3                                       │    │
│  └─────────────────────────────────────────────────────────────────────────────┘    │
│                                                                                       │
│  ┌──────────────────────────────┐   ┌────────────────────────────────────────────┐  │
│  │  Redis (shared cluster)       │   │  MongoDB (reviews_db, wishlist if needed)  │  │
│  │  - Distributed rate limiting  │   │  - Flexible document store for reviews     │  │
│  │  - Cart service store         │   │  - High write throughput                   │  │
│  │  - Session cache              │   └────────────────────────────────────────────┘  │
│  │  - Wishlist store             │                                                    │
│  └──────────────────────────────┘   ┌────────────────────────────────────────────┐  │
│                                      │  Elasticsearch / OpenSearch                  │  │
│                                      │  - Product search index                    │  │
│                                      │  - Faceted search, autocomplete, fuzzy     │  │
│                                      └────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────────────────────────┐
│                         IDENTITY & SECURITY                                           │
│                                                                                       │
│  Keycloak :30080  ─────────────────────────────────────────────────────────────     │
│  Realm: microservices-realm · Clients: ecommerce-client, per-service clients        │
│                                                                                       │
│  HashiCorp Vault (NEW) ──► K8s External Secrets Operator                            │
│  Inject secrets into pods at runtime (no plaintext in application.properties)       │
│                                                                                       │
│  K8s NetworkPolicy (NEW)                                                             │
│  - Services only accept traffic from gateway (X-Gateway-Secret)                    │
│  - Infra pods (MySQL, Kafka) only accept traffic from app pods in same ns          │
│                                                                                       │
│  TLS (NEW): cert-manager + Let's Encrypt wildcard cert on Ingress                   │
└──────────────────────────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────────────────────────┐
│                             OBSERVABILITY STACK  (unchanged)                          │
│                                                                                       │
│  Prometheus ──► Grafana (dashboards per service)                                    │
│  Promtail ──► Loki ──► Grafana (LogQL + traceId links)                             │
│  OTel Collector ──► Tempo + Jaeger ──► Grafana (distributed traces)                │
│                                                                                       │
│  NEW: Kafka consumer lag dashboard (Kafka Exporter → Prometheus → Grafana)          │
│  NEW: Per-service SLO dashboards (error budget, p99 latency)                        │
└──────────────────────────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────────────────────────┐
│                             CI/CD PIPELINE  (NEW)                                     │
│                                                                                       │
│  GitHub Actions                                                                      │
│  ┌──────────┐   ┌──────────┐   ┌───────────────┐   ┌───────────────────────────┐   │
│  │ PR check │──►│ mvn test │──►│ Docker build  │──►│ kubectl rollout restart   │   │
│  │ lint     │   │ + verify │   │ + push to ECR │   │ (staging → prod with gate)│   │
│  └──────────┘   └──────────┘   └───────────────┘   └───────────────────────────┘   │
└──────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 4. Implementation Roadmap

### Phase 1 — Critical Gaps (Fix First)

| Task | Service / File | Effort |
|---|---|---|
| Wire Transactional Outbox poller in order-service | `OutboxPoller.java` (exists, needs activating) | 1 day |
| Implement payment-service (COD + Razorpay) | New service per `prompts_docs/payment-service.md` | 1 week |
| Add payment webhook endpoint + order status update | payment-service + order-service | 2 days |
| Build notification-service (email via SendGrid) | New service consuming order/payment events | 3 days |
| Externalize secrets (move from `application.properties`) | K8s Secrets + External Secrets Operator | 1 day |
| Add HTTPS / TLS to Ingress | cert-manager + Ingress annotation | 0.5 days |

### Phase 2 — High Priority

| Task | Service / File | Effort |
|---|---|---|
| Cart service (Redis-backed, REST API) | New service | 3 days |
| Distributed rate limiting (Redis + Bucket4j) | apigateway `RateLimiterConfig` | 1 day |
| Fix refund workflow (RETURNED → REFUNDED triggers payment-service) | order-service + payment-service | 2 days |
| Add S3 real bucket + CloudFront CDN for product images | product-service + CDN config | 1 day |
| Add MySQL read replicas for product-service (read-heavy) | K8s StatefulSet, JPA routing | 2 days |
| K8s NetworkPolicy for infra isolation | deployment/k8s/network-policies/ | 0.5 days |
| Kafka: add 2 more brokers (remove SPOF) | K8s StatefulSet for Kafka | 1 day |

### Phase 3 — Medium Priority

| Task | Service / File | Effort |
|---|---|---|
| Search service (Elasticsearch / OpenSearch) | New service + K8s StatefulSet | 1 week |
| Review and rating service | New service with MongoDB | 3 days |
| Address book in user-service | New `Address` entity + endpoints | 2 days |
| Payment UI in CheckoutPage (Razorpay widget) | client/src/pages/CheckoutPage.tsx | 2 days |
| Admin analytics dashboard (real charts) | client/src/pages/DashboardPage.tsx + analytics-service | 1 week |
| DB backup CronJob (mysqldump → S3) | deployment/k8s/jobs/db-backup-cronjob.yaml | 0.5 days |
| CI/CD pipeline | .github/workflows/ | 2 days |
| Wishlist service | New service | 2 days |

### Phase 4 — Nice to Have

| Task | Effort |
|---|---|
| Recommendation engine (collaborative filtering on order history) | 2 weeks |
| Real-time order tracking via SSE or WebSocket | 3 days |
| Service mesh (Istio) for mTLS and fine-grained traffic control | 1 week |
| A/B testing infrastructure | 1 week |
| Multi-region deployment | 2 weeks |

---

## 5. Service Inventory — Current vs Proposed

```
Current (4 services)                    Proposed (12 services)
───────────────────────────────────     ───────────────────────────────────────
apigateway          :2027         ──►   apigateway          :2027  (+ Redis CB)
user-service        :2026         ──►   user-service        :2026  (+ addresses)
product-service     :2028         ──►   product-service     :2028  (+ search delegation)
order-service       :2029         ──►   order-service       :2029  (+ outbox wired)
                                        payment-service     :2030  ◄─ NEW (Phase 1)
                                        notification-service:2032  ◄─ NEW (Phase 1)
                                        cart-service        :2033  ◄─ NEW (Phase 2)
                                        search-service      :2031  ◄─ NEW (Phase 3)
                                        review-service      :2034  ◄─ NEW (Phase 3)
                                        wishlist-service    :2035  ◄─ NEW (Phase 4)
                                        recommendation-svc  :2036  ◄─ NEW (Phase 4)
                                        analytics-service   :2037  ◄─ NEW (Phase 4)
```

---

## 6. Data Store Assignments (Proposed)

| Service | Primary DB | Cache / Secondary |
|---|---|---|
| user-service | MySQL `user_db` | Hazelcast |
| product-service | MySQL `product_db` | Hazelcast + Elasticsearch (search index) |
| order-service | MySQL `order_db` | Hazelcast |
| payment-service | MySQL `payment_db` | — |
| cart-service | Redis | — |
| search-service | Elasticsearch / OpenSearch | — |
| review-service | MongoDB `reviews_db` | — |
| wishlist-service | Redis | — |
| notification-service | MySQL `notification_db` (delivery log) | — |
| analytics-service | ClickHouse / BigQuery (OLAP) | — |
| recommendation-svc | In-memory model + Redis | — |
| apigateway | — | Redis (rate-limit counters) |

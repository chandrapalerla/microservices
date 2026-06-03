# Skills & Technology Reference

This document lists every technology, framework, and architectural pattern used in this project.
Use it to assess onboarding readiness or to identify gaps before contributing to a specific service.

---

## Technology Stack at a Glance

| Layer | Technology | Version |
|---|---|---|
| Language (backend) | Java | 25 |
| Framework | Spring Boot | 3.5.x |
| Cloud libraries | Spring Cloud | 2025.0.2 |
| Language (frontend) | TypeScript | 5.7 |
| UI framework | React | 18.3 |
| Build tool (backend) | Maven | 3.9+ |
| Build tool (frontend) | Vite | 6.x |
| Container runtime | Docker | 24+ |
| Orchestration | Kubernetes | 1.28+ |
| Identity provider | Keycloak | 24+ |
| Message broker | Apache Kafka | 3.x |
| Relational DB | MySQL | 8.x |
| Document DB | MongoDB | 7.x |
| Distributed cache | Hazelcast | 5.7 |
| API Gateway | Spring Cloud Gateway Server MVC | 2025.0.2 |

---

## Backend Skills

### Java & Spring Boot
- Java 25 features: records, sealed classes, text blocks, pattern matching
- Spring Boot 3.x auto-configuration, profiles (`application.properties` / `application.yaml`)
- Spring Boot Actuator: `/actuator/health`, `/actuator/prometheus`, `/actuator/info`
- Bean Validation (`@Valid`, `@NotNull`, `@Size`, custom constraints)
- `@ControllerAdvice` / `@RestControllerAdvice` with RFC 7807 `ProblemDetail`
- `@Transactional` semantics — propagation, isolation, rollback rules
- AOP (`@Aspect`, `@Around`, `@Before`) — used for custom security annotations

### Spring Data JPA & Hibernate
- Entity mapping (`@Entity`, `@Table`, `@Column`, `@Enumerated`)
- Relationships (`@OneToMany`, `@ManyToOne`, `@ElementCollection`)
- Repository pattern (`JpaRepository`, `@Query`, derived query methods)
- Optimistic locking — `@Version Long version`, handling `ObjectOptimisticLockingFailureException`
- `Pageable` / `Page<T>` for paginated queries
- Flyway database migrations (versioned scripts, `ddl-auto=validate`)

### Spring Data MongoDB
- `@Document`, `@Field`, `MongoRepository`
- Used in product-service for catalogue data

### Spring Security & OAuth2
- OAuth2 Resource Server — JWT validation via JWKS endpoint
- `KeycloakJwtConverter` — extracts `realm_access.roles` → `ROLE_*` Spring authorities
  - `reactive()` variant for WebFlux (user-service)
  - `blocking()` / `create()` variant for Servlet (gateway, order-service, product-service)
- `@PreAuthorize("hasRole('ADMIN')")` method-level security
- Custom AOP annotations — `@RequiresAdmin`, `@RequiresUser` backed by `AuthorizationAspect`
- `SecurityContextHolder` / `Authentication` extraction in service layer
- JJWT library 0.12.x (gateway only — for internal `X-Gateway-Secret` validation)

### Spring Cloud OpenFeign
- Declarative HTTP clients (`@FeignClient`, `@GetMapping`, `@PostMapping`)
- JWT relay interceptor — forwards `Authorization: Bearer <token>` to downstream services
- Error decoder / `FeignException` hierarchy — mapping downstream HTTP errors
- Known issue: `SortJsonComponent` conflict with `PageImpl` — solved via `JacksonConfig`

### Resilience4j
- `@CircuitBreaker(name, fallbackMethod)` — annotation-driven on Spring MVC services
- `@Retry(name)` — with self-injection via `@Lazy @Autowired` to honour proxy
- Circuit breaker states: CLOSED → OPEN → HALF_OPEN
- Config: `slidingWindowSize`, `failureRateThreshold`, `waitDurationInOpenState`, `statusCodes`
- Important: only 5xx and connection errors should be counted as failures — 4xx must be excluded

### Apache Kafka
- Producer: `KafkaTemplate<String, String>` with JSON serialization
- Consumer: `@KafkaListener(topics, groupId)`
- Transactional Outbox pattern — events written to DB in same transaction, relayed by `OutboxPoller`
- `max.block.ms` tuning to prevent producer blocking on cold start
- Topics used: `user.events`, `order.events`, `payment.events`

### Caching (Hazelcast 5.7)
- Embedded cluster mode configured via `hazelcast.xml`
- Spring Cache annotations: `@Cacheable`, `@CachePut`, `@CacheEvict`
- Named cache maps: `users`, `usersPage`, `orders`, `ordersPage`
- K8s RBAC note: must set `<auto-detection enabled="false"/>` in `hazelcast.xml` to prevent unwanted K8s discovery

### MapStruct 1.5
- Compile-time DTO ↔ Entity mappers (`@Mapper(componentModel = "spring")`)
- Annotation processor order matters in `maven-compiler-plugin`: Lombok first, then MapStruct

### Lombok
- `@Data`, `@Builder`, `@RequiredArgsConstructor`, `@Slf4j`, `@NoArgsConstructor`
- Must be listed before MapStruct in annotation processor paths

### API Documentation
- SpringDoc OpenAPI 2.8 (`springdoc-openapi-starter-webmvc-ui`)
- Swagger UI at `/swagger-ui/index.html`
- OpenAPI JSON at `/v3/api-docs`
- Annotations: `@Tag`, `@Operation`, `@ApiResponse`, `@Schema`

---

## API Gateway Skills

### Spring Cloud Gateway Server MVC (2025.0.2)
- **MVC variant** (not reactive WebFlux) — filter args use `id:` not `name:`
- Route configuration in `application.yaml`: `predicates`, `filters`, `uri`
- Per-route filters: `AddRequestHeader`, `CircuitBreaker`, `StripPrefix`, `RewritePath`
- `default-filters` does NOT reliably apply to routes with their own `filters:` block — always add to each route
- `GatewaySecretFilter` — validates `X-Gateway-Secret` header on every inbound request
- `TokenPropagationFilter` — stores raw JWT for downstream forwarding
- Resilience4j circuit breakers wired at gateway level with `fallbackUri: forward:/fallback`

---

## Frontend Skills

### React 18 + TypeScript
- Functional components with hooks (`useState`, `useEffect`, `useCallback`, `useRef`)
- React Router v6 — `<Routes>`, `<Route>`, `useNavigate`, `useParams`, `useSearchParams`
- Context API — `CartProvider` wrapping the entire app
- Protected route pattern using `PrivateRoute` component

### TanStack React Query v5
- `useQuery` — data fetching with caching, background refetch, retry
- `useMutation` — mutations with `onSuccess` / `onError` callbacks
- Query key design — proper key structure for cache invalidation
- `QueryClient` configuration — `staleTime`, `gcTime`
- DevTools integration (`@tanstack/react-query-devtools`)

### Axios
- Instance configuration with `baseURL` and interceptors
- JWT injection via request interceptor
- 401 handling / token refresh in response interceptor

### UI & Styling
- Tailwind CSS v4 (Vite plugin — no `tailwind.config.js` needed)
- Lucide React icons
- `react-hot-toast` for notifications

### Authentication Flow (Frontend)
- Keycloak Authorization Code + PKCE flow
- JWT decoded client-side with `jwt-decode` v4 (`jwtDecode()`)
- Tokens stored in `localStorage` (access token + refresh token)
- Role-based UI — hide admin routes unless `ROLE_ADMIN` present in JWT

---

## Infrastructure & DevOps Skills

### Docker
- Multi-stage `Dockerfile` — builder stage (`maven:3.9-eclipse-temurin-21`) + runtime stage (`eclipse-temurin:21-jre`)
- Exposing service ports, environment variable injection
- `.dockerignore` to exclude `target/` and dev files

### Kubernetes
- `Deployment` — `replicas`, `image`, `env` from `ConfigMap` / `Secret`, `readinessProbe`, `livenessProbe`
- `Service` — `ClusterIP` (internal), `NodePort` (external access in dev)
- `ConfigMap` — externalised `application.properties` values
- `Secret` — base64-encoded credentials (DB password, Keycloak secret)
- `kustomization.yaml` — composing multiple manifests
- `kubectl apply`, `kubectl rollout restart`, `kubectl logs`, `kubectl exec`
- Namespace isolation — all services deployed to `ecommerce` namespace

### Keycloak
- Realm creation and configuration (`microservices-realm`)
- Client setup: public client (frontend), confidential client (services)
- Roles: `USER`, `ADMIN` at realm level
- User federation or manual user creation
- Service account `client_credentials` grant for service-to-service auth
- Extracting `realm_access.roles` for Spring Security role mapping
- Admin API — revoking user sessions programmatically

---

## Architectural Patterns

### Microservices Decomposition
- Services split by bounded context: users, products, orders, payments
- No shared database — each service owns its schema
- Inter-service communication via Feign (sync) or Kafka (async)

### API Gateway Pattern
- Single entry point for all client traffic
- Handles cross-cutting concerns: auth validation, rate limiting, circuit breaking, correlation IDs
- Routes requests to downstream services via K8s DNS

### JWT-Based Service Authentication
- Every request carries a Keycloak-issued JWT
- Gateway validates JWT signature and forwards it downstream
- Each service independently validates the JWT as an OAuth2 resource server
- Internal calls also carry `X-Gateway-Secret` header as an extra trust boundary

### DTO Pattern with MapStruct
- JPA entities never exposed in API responses
- `UserDto`, `OrderDto`, `ProductDto` used for all controller I/O
- MapStruct generates compile-time mappers — no reflection overhead at runtime

### Transactional Outbox Pattern
- Kafka events written to an `outbox` table in the same DB transaction as the business record
- A poller (`OutboxPoller`) reads the outbox and publishes to Kafka, then marks events as sent
- Guarantees at-least-once delivery even if Kafka is temporarily unreachable

### Choreography-Based Saga
- Used for distributed transactions spanning multiple services (e.g., order → payment)
- Each service publishes domain events and reacts to others' events
- Compensation (rollback) via dedicated events (`PAYMENT_FAILED` → order reverts to `PAYMENT_FAILED`)
- No central orchestrator — loosely coupled, services evolve independently

### Optimistic Locking
- `@Version` field on JPA entities — Hibernate increments on every update
- Concurrent updates detected at DB level; Spring throws `ObjectOptimisticLockingFailureException`
- API returns HTTP 409 — client must re-fetch and retry with latest version

### Circuit Breaker Pattern
- Resilience4j CBs at both gateway and service level
- Only 5xx / connection errors trip the breaker — 4xx (client errors) must not count as failures
- Fallback methods return graceful error responses while the circuit is open

---

## Observability Skills

### Metrics (Prometheus + Grafana)
- Micrometer auto-instruments Spring Boot: JVM, HTTP requests, DB pool, cache hits
- Custom metrics via `MeterRegistry`
- Prometheus scrapes `/actuator/prometheus` on each service
- Key PromQL queries for service health, latency percentiles, error rates

### Distributed Tracing (OpenTelemetry)
- `micrometer-tracing-bridge-otel` propagates `traceId` / `spanId` through HTTP and Kafka
- OTLP exporter → OpenTelemetry Collector → Grafana Tempo (and optionally Jaeger)
- `traceId` injected into every log line via Logback MDC
- Grafana: use "Explore → Tempo" and paste a `traceId` to see the full request trace

### Log Aggregation (Loki + Promtail)
- Logback + `logstash-logback-encoder` outputs structured JSON logs
- Promtail tails container log files and ships to Loki
- LogQL queries to filter by service, severity, or `traceId`
- Grafana derived field: `traceId` in log lines links directly to the Tempo trace

---

## Testing Skills

### Spring Boot Test
- `@SpringBootTest` — full application context integration tests
- `@WebMvcTest` — slice test for controllers (no JPA / Kafka)
- `@DataJpaTest` — slice test for repository layer with in-memory H2
- `MockMvc` — HTTP-level controller testing without a real server

### Kafka Testing
- `@EmbeddedKafka` for integration tests that publish / consume real messages
- `KafkaTestUtils` for producing and consuming test records

---

## Development Workflow

### Maven Commands
```bash
mvn clean package           # Build JAR
mvn clean package -DskipTests  # Build without running tests
mvn spring-boot:run         # Run locally
mvn test                    # Run all tests
mvn clean compile           # Compile-only check
```

### Startup Order
```
Kubernetes infra (MySQL, MongoDB, Kafka, Keycloak)
  → apigateway
  → user-service / product-service / order-service  (any order)
```

### Service URLs (local K8s NodePort)
| Service | URL |
|---|---|
| API Gateway | `http://localhost:30500` |
| Keycloak | `http://localhost:30080` |
| Swagger UI (user-service) | `http://localhost:2026/swagger-ui/index.html` |
| React client | `http://localhost:5173` |
| Grafana | `http://localhost:3000` |
| Prometheus | `http://localhost:9090` |

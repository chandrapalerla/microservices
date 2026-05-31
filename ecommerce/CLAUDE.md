# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Architecture Overview

This is a Spring Boot microservices project with independent services under `services/`, a React frontend under `client/`, and Kubernetes manifests under `deployment/k8s/`.

```
ecommerce/
├── services/
│   ├── apigateway/      — Spring Cloud Gateway MVC (port 2027)
│   ├── user-service/    — User management service (port 2026)
│   ├── product-service/ — Product catalogue service (port 2028)
│   └── order-service/   — Order management service (port 2029)
├── client/              — React TypeScript frontend
└── deployment/k8s/      — Kubernetes manifests for all infra
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

Startup order matters: **apigateway → user-service / product-service / order-service** (no service registry required — K8s DNS handles discovery).

## Infrastructure Dependencies

The services expect the following infrastructure (configured via K8s NodePorts):

| Service   | Local Port | K8s NodePort | Notes                        |
|-----------|------------|--------------|------------------------------|
| MySQL     | —          | 30036        | Database: `user_db`          |
| MongoDB   | —          | 30017        | User: `admin`, Pass: `admin123` |
| Keycloak  | —          | 30080        | Realm: `microservices-realm` |

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

### Caching (Hazelcast)
Hazelcast is configured via `services/user-service/src/main/resources/hazelcast.xml`. Four named cache maps:
- `users` — individual user lookups, TTL 10 min, max 1000 entries
- `usersPage` — paginated results, TTL 5 min, max 500 entries
- `orders` / `ordersPage` — same pattern for orders (future use)

Cache annotations on `UserService`: `@Cacheable`, `@CachePut`, `@CacheEvict`. Write operations must evict `usersPage` (`allEntries=true`) in addition to the individual `users` entry.

### DTO / MapStruct
All controller I/O uses `UserDto`, never the JPA `User` entity directly. The `UserMapper` interface is a MapStruct compile-time generated mapper (`componentModel = "spring"`). MapStruct and Lombok annotation processors are both wired in the `maven-compiler-plugin` in `services/user-service/pom.xml`.

### Optimistic Locking
`User` entity has `@Version Long version`. On updates, the client must send the current version. A mismatch returns HTTP 409. The `update()` method in `UserService` deliberately does not copy the version from the request — it relies on Hibernate to detect conflicts.

### Token Propagation
`TokenPropagationFilter` (in gateway) stores the raw JWT as a request attribute (`jwt_token` and `jwt_token_header`) for downstream forwarding. Downstream services validate the same JWT independently as OAuth2 resource servers.

### Custom AOP Security Annotations
`@RequiresAdmin` and `@RequiresUser` in `services/user-service/src/main/java/com/user/aspect/` are custom AOP annotations backed by `AuthorizationAspect`. They check Spring Security context directly. Prefer these for method-level security over `@PreAuthorize` when you need logging.

## Service Ports and Endpoints

| Service         | Port | Notable Endpoints                                |
|-----------------|------|--------------------------------------------------|
| user-service    | 2026 | `/api/v1/users/**`, `/api/v1/auth/**`            |
| product-service | 2028 | `/api/v1/products/**`, `/api/v1/categories/**`   |
| order-service   | 2029 | `/api/v1/orders/**`                              |
| apigateway      | 2027 | `/auth/login`, `/auth/logout`, `/auth/health`    |
| Keycloak (k8s)  | 30080| `/admin`, `/realms/microservices-realm/...`      |

**Swagger UI** (user-service): `http://localhost:2026/swagger-ui/index.html`  
**OpenAPI JSON**: `http://localhost:2026/v3/api-docs`

## Keycloak Configuration

Realm: `microservices-realm`  
Token URL: `http://localhost:30080/realms/microservices-realm/protocol/openid-connect/token`  
JWKS URL: `http://localhost:30080/realms/microservices-realm/protocol/openid-connect/certs`

The `user-service` uses OAuth2 client registration `keycloak` with `client_credentials` grant for service-to-service calls. The client secret in `application.properties` (`your-service-client-secret`) is a placeholder — replace with the actual secret from Keycloak admin.

Roles expected in JWT: `USER`, `ADMIN` (mapped to `ROLE_USER`, `ROLE_ADMIN`).

## Adding a New Service

1. Create a new Spring Boot project under `services/<new-service>/`.
2. Add `spring.application.name` to `application.properties`.
3. Add `spring-boot-starter-oauth2-resource-server` and configure `spring.security.oauth2.resourceserver.jwt.jwk-set-uri`.
4. Use `KeycloakJwtConverter` to map Keycloak roles — do not rely on default JWT converter.
5. Add a route entry in the API Gateway's `application.yaml` using the K8s DNS URI (e.g. `http://new-service:PORT`).
6. Create a K8s Service + Deployment + ConfigMap under `deployment/k8s/services/new-service/` and add it to `deployment/k8s/services/kustomization.yaml`.

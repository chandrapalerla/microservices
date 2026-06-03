# Ecommerce Microservices Platform — Local Setup Guide

## Table of Contents
1. [Prerequisites](#prerequisites)
2. [Clone the Repository](#clone-the-repository)
3. [Project Structure](#project-structure)
4. [Infrastructure Setup (Kubernetes)](#infrastructure-setup-kubernetes)
5. [Keycloak Setup](#keycloak-setup)
6. [Update Secret Keys](#update-secret-keys)
7. [Build and Run Services Locally](#build-and-run-services-locally)
8. [Frontend (React Client)](#frontend-react-client)
9. [Full Kubernetes Deployment](#full-kubernetes-deployment)
10. [Access URLs](#access-urls)
11. [Service Ports & Endpoints](#service-ports--endpoints)
12. [Observability](#observability)
13. [Troubleshooting](#troubleshooting)

---

## Prerequisites

Install the following tools before starting:

| Tool | Version | Purpose |
|---|---|---|
| [Java JDK](https://adoptium.net/) | 21+ | Backend services |
| [Maven](https://maven.apache.org/) | 3.9+ | Build tool |
| [Node.js](https://nodejs.org/) | 20+ | React frontend |
| [Rancher Desktop](https://rancherdesktop.io/) or Docker Desktop | latest | Container runtime + Kubernetes |
| `kubectl` | 1.28+ | Kubernetes CLI (bundled with Rancher/Docker Desktop) |
| [Git](https://git-scm.com/) | any | Source control |

> **Rancher Desktop note:** Set the container runtime to `containerd` and enable Kubernetes in Preferences.

---

## Clone the Repository

```bash
git clone https://github.com/<your-org>/ecommerce.git
cd ecommerce
```

---

## Project Structure

```
ecommerce/
├── services/
│   ├── apigateway/       # Spring Cloud Gateway MVC  (port 2027)
│   ├── user-service/     # User management           (port 2026)
│   ├── product-service/  # Product catalogue         (port 2028)
│   └── order-service/    # Order management          (port 2029)
├── client/               # React + TypeScript + Vite frontend
└── deployment/
    └── k8s/
        └── ecommerce-platform/   # Helm-style Kubernetes manifests
            ├── templates/
            │   ├── namespace/
            │   ├── infrastructure/   # MySQL, Kafka, Keycloak, MinIO
            │   ├── applications/     # All Spring Boot services + client
            │   ├── observability/    # Prometheus, Grafana, Loki, Tempo, Jaeger
            │   └── ingress/
            └── scripts/
                ├── deploy-all.sh
                ├── restart-services.sh
                └── undeploy-all.sh
```

**Request flow:**
```
Browser → API Gateway (2027) → user-service / product-service / order-service
                                        ↓
                               Keycloak (JWT validation)
```

---

## Infrastructure Setup (Kubernetes)

All infrastructure runs in the `ecommerce` Kubernetes namespace.

### 1. Start Kubernetes

Make sure Rancher Desktop (or Docker Desktop with Kubernetes enabled) is running and `kubectl` can reach the cluster:

```bash
kubectl cluster-info
```

### 2. Deploy infrastructure

```bash
# From the project root
cd deployment/k8s/ecommerce-platform

# Create namespace
kubectl apply -f templates/namespace/namespace.yaml

# MySQL
kubectl apply -n ecommerce -f templates/infrastructure/mysql/

# Kafka + Kafka UI
kubectl apply -n ecommerce -f templates/infrastructure/kafka/
kubectl apply -n ecommerce -f templates/infrastructure/ui/

# Keycloak
kubectl apply -n ecommerce -f templates/infrastructure/keycloak/
kubectl rollout status deployment/keycloak -n ecommerce --timeout=120s
```

### 3. Verify pods are running

```bash
kubectl get pods -n ecommerce
```

Expected: `mysql-*`, `kafka-*`, `keycloak-*` pods in `Running` state.

---

## Keycloak Setup

Keycloak runs at **http://localhost:30080**. After the pod is `Running`, complete the one-time realm and client configuration.

### 1. Log in to Keycloak Admin Console

- URL: http://localhost:30080/admin
- Username: `admin`
- Password: `admin123`

### 2. Create Realm

1. Click **Create Realm** (top-left dropdown).
2. Set **Realm name** = `microservices-realm`.
3. Click **Create**.

### 3. Create Clients

Create two clients inside `microservices-realm`:

#### Client: `apigateway-client`

| Field | Value |
|---|---|
| Client ID | `apigateway-client` |
| Client Protocol | `openid-connect` |
| Client Authentication | On |
| Authorization | Off |
| Valid Redirect URIs | `http://localhost:2027/login/oauth2/code/keycloak` |
| Web Origins | `http://localhost:2027` |

After saving, go to **Credentials** tab and copy the **Client Secret** — you will need it in the next section.

#### Client: `user-service-client`

| Field | Value |
|---|---|
| Client ID | `user-service-client` |
| Client Protocol | `openid-connect` |
| Client Authentication | On |
| Service Account Roles | Enabled (for `client_credentials` grant) |

After saving, go to **Credentials** tab and copy the **Client Secret**.

**Grant `manage-users` to the service account:**
- Go to **Clients** → `user-service-client` → **Service account roles** tab.
- Click **Assign role** → filter by `realm-management` → assign `manage-users`.

### 4. Create Roles

Inside `microservices-realm`, go to **Realm roles** and create:
- `USER`
- `ADMIN`

### 5. Create a Test User

1. Go to **Users** → **Add user**.
2. Set username, email, and enable the account.
3. Under **Credentials**, set a password (disable "Temporary").
4. Under **Role mappings**, assign `USER` (and optionally `ADMIN`).

---

## Update Secret Keys

After completing the Keycloak setup, update the secrets in three places:

### A. Local development (application.properties)

**`services/apigateway/src/main/resources/application.yaml`**
```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          keycloak:
            client-secret: <paste apigateway-client secret here>
```

**`services/user-service/src/main/resources/application.properties`**
```properties
spring.security.oauth2.client.registration.keycloak.client-secret=<paste user-service-client secret here>
```

> The `gateway.internal-secret` value (`gw-secret-change-in-prod`) must be **identical** across all services — apigateway, user-service, product-service, and order-service. Change it to any shared secret string and update all four files if you want to harden it locally.

### B. Kubernetes secrets (for K8s deployment)

Re-encode your secrets with base64:

```bash
# On Linux/macOS
echo -n "your-actual-secret" | base64

# On Windows (PowerShell)
[Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes("your-actual-secret"))
```

Update `deployment/k8s/ecommerce-platform/templates/applications/api-gateway/secret.yaml`:

```yaml
data:
  internal-secret: <base64 of your gateway internal secret>
  apigateway-client-secret: <base64 of apigateway-client secret from Keycloak>
  user-service-client-secret: <base64 of user-service-client secret from Keycloak>
```

### C. Summary of all secrets to replace

| File | Key | Default (dev only) | Replace with |
|---|---|---|---|
| `services/apigateway/src/main/resources/application.yaml` | `client-secret` | `your-client-secret` | Keycloak `apigateway-client` secret |
| `services/user-service/src/main/resources/application.properties` | `client-secret` | `your-service-client-secret` | Keycloak `user-service-client` secret |
| All `application.properties` | `gateway.internal-secret` | `gw-secret-change-in-prod` | Any shared string (same across all services) |
| `deployment/.../api-gateway/secret.yaml` | `apigateway-client-secret` | base64 placeholder | base64 of Keycloak `apigateway-client` secret |
| `deployment/.../api-gateway/secret.yaml` | `user-service-client-secret` | base64 placeholder | base64 of Keycloak `user-service-client` secret |
| `deployment/.../mysql/secret.yaml` | `root-password` / `password` | `root123` | Strong password in prod |
| `deployment/.../keycloak/secret.yaml` | `admin-password` | `admin123` | Strong password in prod |

---

## Build and Run Services Locally

Run services directly on your machine (no Kubernetes required) — useful for development and debugging.

### Startup order

```
1. apigateway
2. user-service, product-service, order-service  (any order)
```

### Build all services

```bash
cd services/apigateway      && mvn clean package -DskipTests && cd ../..
cd services/user-service    && mvn clean package -DskipTests && cd ../..
cd services/product-service && mvn clean package -DskipTests && cd ../..
cd services/order-service   && mvn clean package -DskipTests && cd ../..
```

### Run each service (separate terminals)

```bash
# Terminal 1
cd services/apigateway
mvn spring-boot:run

# Terminal 2
cd services/user-service
mvn spring-boot:run

# Terminal 3
cd services/product-service
mvn spring-boot:run

# Terminal 4
cd services/order-service
mvn spring-boot:run
```

> The services connect to MySQL (NodePort 30036), Kafka (NodePort 30092), and Keycloak (NodePort 30080) — these must be running in Kubernetes. Only run services locally if those K8s pods are healthy.

---

## Frontend (React Client)

```bash
cd client
npm install
npm run dev
```

The dev server starts at **http://localhost:5173** and proxies API calls to the gateway at `http://localhost:2027`.

To build for production:

```bash
npm run build
```

---

## Full Kubernetes Deployment

This deploys everything — infra, services, observability — in one shot.

### 1. Build Docker images

Each service has a `Dockerfile`. Build and tag them:

```bash
# Rancher Desktop (nerdctl into k8s.io namespace)
nerdctl --namespace k8s.io build -t ecommerce/apigateway:latest      services/apigateway
nerdctl --namespace k8s.io build -t ecommerce/user-service:latest    services/user-service
nerdctl --namespace k8s.io build -t ecommerce/product-service:latest services/product-service
nerdctl --namespace k8s.io build -t ecommerce/order-service:latest   services/order-service
nerdctl --namespace k8s.io build -t ecommerce/client:latest          client

# Docker Desktop
docker build -t ecommerce/apigateway:latest      services/apigateway
docker build -t ecommerce/user-service:latest    services/user-service
docker build -t ecommerce/product-service:latest services/product-service
docker build -t ecommerce/order-service:latest   services/order-service
docker build -t ecommerce/client:latest          client
```

### 2. Run deploy-all.sh

```bash
cd deployment/k8s/ecommerce-platform
bash scripts/deploy-all.sh
```

The script:
- Builds any missing images
- Installs NGINX Ingress Controller
- Creates the `ecommerce` namespace
- Deploys MySQL, Kafka, Keycloak, all services, and the full observability stack

### 3. Add hosts entry (for Ingress)

```bash
# Linux/macOS
echo "127.0.0.1 ecommerce.local" | sudo tee -a /etc/hosts

# Windows (run PowerShell as Administrator)
Add-Content -Path "C:\Windows\System32\drivers\etc\hosts" -Value "127.0.0.1 ecommerce.local"
```

### 4. Tear down

```bash
bash scripts/undeploy-all.sh
```

---

## Access URLs

| Service | URL | Credentials |
|---|---|---|
| React Client | http://localhost:30500 | — |
| API Gateway | http://localhost:30027 | — |
| Keycloak Admin | http://localhost:30080/admin | admin / admin123 |
| Prometheus | http://localhost:30900 | — |
| Grafana | http://localhost:30300 | admin / admin |
| Jaeger UI | http://localhost:30686 | — |
| Kafka UI | http://localhost:30808 | — |
| MySQL | localhost:30036 | root / root123 |
| Kafka broker | localhost:30092 | — |
| Ingress | http://ecommerce.local | (add hosts entry above) |

**Swagger UI (user-service):** http://localhost:2026/swagger-ui/index.html  
**OpenAPI JSON:** http://localhost:2026/v3/api-docs

---

## Service Ports & Endpoints

| Service | Local Port | K8s NodePort | Key Endpoints |
|---|---|---|---|
| apigateway | 2027 | 30027 | `/auth/login`, `/auth/logout`, `/auth/health` |
| user-service | 2026 | — | `/api/v1/users/**`, `/api/v1/auth/**` |
| product-service | 2028 | — | `/api/v1/products/**`, `/api/v1/categories/**` |
| order-service | 2029 | — | `/api/v1/orders/**` |
| MySQL | — | 30036 | databases: `user_db`, `product_db`, `order_db` |
| Keycloak | — | 30080 | `/realms/microservices-realm/...` |

---

## Observability

The platform ships a full observability stack deployed into the `ecommerce` namespace:

| Component | Role | NodePort |
|---|---|---|
| **Prometheus** | Scrapes `/actuator/prometheus` from every service every 15 s | 30900 |
| **Grafana** | Dashboards for metrics, logs, and traces | 30300 |
| **Loki** | Log aggregation backend | 30100 |
| **Promtail** | DaemonSet that tails pod logs and ships them to Loki | — |
| **Tempo** | Distributed trace storage (OTLP → local filesystem) | 30320 |
| **Jaeger** | Distributed trace UI and storage (in-memory, 50k traces) | 30686 |
| **OTel Collector** | Receives OTLP traces from services, fans out to Tempo + Jaeger | 30417 (gRPC) / 30418 (HTTP) |

### How it all fits together

```
Services (Spring Boot)
  │
  ├─── /actuator/prometheus ──────────► Prometheus (scrape pull, 15s)
  │                                          │
  │                                          ▼
  │                                       Grafana (Prometheus datasource)
  │
  ├─── stdout JSON logs ──────────────► Promtail (DaemonSet, tails /var/log/pods)
  │                                          │
  │                                          ▼
  │                                        Loki ◄──────── Grafana (Loki datasource)
  │
  └─── OTLP traces (HTTP :30418) ─────► OTel Collector
                                              │
                                    ┌─────────┴──────────┐
                                    ▼                     ▼
                                  Tempo               Jaeger
                                    │                     │
                                    └────────┬────────────┘
                                             ▼
                                   Grafana (Tempo / Jaeger datasource)
```

Log lines include `traceId` and `spanId` in every JSON log entry. Grafana is pre-configured to let you click a `traceId` in a log line and jump straight to the trace in Tempo — and from a trace span you can jump back to the correlated logs in Loki.

---

### View Logs

#### Option 1 — Grafana / Loki (recommended)

1. Open **http://localhost:30300** and log in (`admin` / `admin`).
2. Go to **Explore** (compass icon on the left sidebar).
3. Select **Loki** from the datasource dropdown.
4. Use LogQL to query:

```logql
# All logs from user-service
{app="user-service"}

# ERROR and WARN level only
{app="user-service"} | json | level =~ "ERROR|WARN"

# Logs for a specific trace ID (copy from Jaeger/Tempo)
{namespace="ecommerce"} | json | traceId="<your-trace-id>"

# All logs across all ecommerce services in the last 15 min
{namespace="ecommerce"} | json

# Logs containing a keyword
{namespace="ecommerce"} |= "NullPointerException"
```

5. Use the **time range picker** (top right) to narrow to the window you care about.

#### Option 2 — kubectl (quick raw tail)

```bash
# Tail a specific pod
kubectl logs -f deployment/user-service -n ecommerce

# Last 100 lines
kubectl logs --tail=100 deployment/order-service -n ecommerce

# All pods matching a label (e.g. all instances of product-service)
kubectl logs -l app=product-service -n ecommerce --prefix=true

# Follow all pods in namespace simultaneously (requires stern)
stern -n ecommerce .
```

#### Log format

Every service emits structured JSON to stdout via `logback-spring.xml`. Each line includes:

```json
{
  "@timestamp": "2026-06-02T10:15:30.123Z",
  "level": "INFO",
  "logger_name": "com.user.service.UserService",
  "message": "User 42 fetched from cache",
  "service": "user-service",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "spanId": "00f067aa0ba902b7"
}
```

The `traceId` / `spanId` fields are injected automatically by Spring Boot's Micrometer tracing integration — no manual MDC required.

---

### Trace a Request End-to-End

Every inbound HTTP request generates a trace that flows through the gateway into downstream services via propagated `traceparent` headers.

#### Option 1 — Jaeger UI

1. Open **http://localhost:30686**.
2. In the **Search** panel, select a service (e.g. `apigateway`) and click **Find Traces**.
3. Click any trace row to expand the full waterfall view showing spans across all services.
4. Use **Compare** to diff two traces side-by-side.

#### Option 2 — Grafana / Tempo

1. Open Grafana → **Explore** → select **Tempo**.
2. Search by **TraceID** (paste a trace ID from a log line or Jaeger).
3. Or use **Search** tab: filter by `service.name`, `http.method`, `http.status_code`, duration range.
4. Click any span to see its attributes, events, and a **"Logs for this span"** link that jumps to the correlated Loki logs.

#### Option 3 — Follow a trace from a log line

1. Grafana → **Explore** → **Loki**.
2. Query `{namespace="ecommerce"} | json`.
3. Find a log line of interest and expand it — the `traceId` field will appear as a **clickable link** that opens the trace in Tempo automatically (configured via derived fields in the Loki datasource).

#### Tracing configuration in services

All services send traces to the OTel Collector at NodePort `30418` (HTTP):

```properties
# in application.properties of each service
management.tracing.sampling.probability=1.0
management.otlp.tracing.endpoint=http://localhost:30418/v1/traces
```

Sampling is set to `1.0` (100%) for local development. In production, lower this to `0.1` (10%) to reduce overhead.

---

### Prometheus Metrics

#### Verify scraping is working

Open **http://localhost:30900/targets** — all four service jobs should show `State: UP`:

| Job | Target |
|---|---|
| `apigateway` | `apigateway.ecommerce.svc.cluster.local:2027` |
| `user-service` | `user-service.ecommerce.svc.cluster.local:2026` |
| `order-service` | `order-service.ecommerce.svc.cluster.local:2029` |
| `product-service` | `product-service.ecommerce.svc.cluster.local:2028` |

If a target shows `DOWN`, check that the pod is running and the actuator endpoint is reachable:

```bash
kubectl exec -n ecommerce deployment/user-service -- \
  curl -s http://localhost:2026/actuator/prometheus | head -20
```

#### Useful PromQL queries

Paste these in Prometheus (**http://localhost:30900**) or in a Grafana panel with Prometheus datasource:

```promql
# HTTP request rate per service (requests/sec, 2m window)
rate(http_server_requests_seconds_count{namespace="ecommerce"}[2m])

# 95th-percentile response time per service
histogram_quantile(0.95,
  sum by (service, le) (
    rate(http_server_requests_seconds_bucket[5m])
  )
)

# Error rate (4xx + 5xx) per service
sum by (service) (
  rate(http_server_requests_seconds_count{outcome=~"CLIENT_ERROR|SERVER_ERROR"}[2m])
)

# JVM heap used
jvm_memory_used_bytes{area="heap", service="user-service"}

# Active Hikari DB connections
hikaricp_connections_active{service="user-service"}

# Circuit breaker state (0=CLOSED, 1=OPEN, 2=HALF_OPEN)
resilience4j_circuitbreaker_state{namespace="ecommerce"}

# Kafka producer send rate
rate(kafka_producer_record_send_total[1m])

# Cache hit ratio (Hazelcast)
cache_gets_total{result="hit", service="user-service"}
  /
cache_gets_total{service="user-service"}
```

---

### Grafana Dashboards

Grafana starts with the four datasources (Prometheus, Loki, Tempo, Jaeger) pre-provisioned via ConfigMap. You only need to import or build dashboards.

#### Import community dashboards

1. Open **http://localhost:30300** → **Dashboards** → **New** → **Import**.
2. Enter a Grafana dashboard ID and click **Load**:

| Dashboard | ID | What it shows |
|---|---|---|
| Spring Boot Statistics | `12900` | HTTP rate, errors, JVM, Hikari pool |
| JVM Micrometer | `4701` | GC pauses, heap, threads, class loading |
| Kafka Overview | `7589` | Producer/consumer throughput, lag |
| Kubernetes Pod Metrics | `6417` | CPU, memory, restarts per pod |

3. On the import screen, select **Prometheus** as the datasource and click **Import**.

#### Build a service health dashboard (manual steps)

1. **Dashboards** → **New** → **New Dashboard** → **Add visualization**.
2. Select **Prometheus** datasource.
3. Add panels using the PromQL queries from the section above.
4. Suggested panel layout:

```
Row: Request Traffic
  [Time series] HTTP request rate by service
  [Time series] p95 latency by service
  [Stat]        Error rate %

Row: JVM Health
  [Gauge]       Heap used %
  [Time series] Active DB connections (Hikari)
  [Time series] GC pause duration

Row: Resilience
  [Stat]        Circuit breaker states
  [Time series] Retry attempts by service
```

5. Click **Save dashboard** (top right) and give it a name.

#### Build a logs dashboard

1. **New Dashboard** → **Add visualization** → select **Loki** datasource.
2. Use a **Logs** panel type with this query to show all errors across the platform:

```logql
{namespace="ecommerce"} | json | level="ERROR"
```

3. Add a second **Logs** panel per service for scoped views:

```logql
{app="order-service"} | json | level=~"ERROR|WARN"
```

#### Correlate logs ↔ traces from a dashboard

Grafana's Loki datasource is configured with a derived field that detects `"traceId":"..."` in log lines and renders it as a link to Tempo. This works out-of-the-box with no extra configuration — click any log line that contains a `traceId` and select **Tempo** to open the full distributed trace.

Similarly, the Tempo datasource has **tracesToLogsV2** configured to show a **"Logs for this span"** button at the bottom of any trace view, which opens the correlated Loki logs for that exact time window.

---

### Observability Quick-Reference

| I want to… | Go to |
|---|---|
| See live logs for a service | Grafana → Explore → Loki → `{app="<service>"}` |
| Find all errors in last 1h | Grafana → Explore → Loki → `{namespace="ecommerce"} \| json \| level="ERROR"` |
| Trace a slow request | Jaeger UI (http://localhost:30686) → Search by service + min duration |
| Jump from a log line to its trace | Grafana Loki → click `traceId` link → Tempo |
| Jump from a trace span to logs | Grafana Tempo → open trace → "Logs for this span" |
| Check HTTP error rate | Prometheus → `rate(http_server_requests_seconds_count{outcome="SERVER_ERROR"}[2m])` |
| Check circuit breaker status | Prometheus → `resilience4j_circuitbreaker_state` |
| Check DB connection pool | Prometheus → `hikaricp_connections_active` |
| See all Prometheus targets | http://localhost:30900/targets |
| Stream raw pod logs | `kubectl logs -f deployment/<service> -n ecommerce` |

---

## Troubleshooting

**Services fail to start — `Could not obtain connection from datasource`**
- MySQL pod is not ready yet. Check: `kubectl get pods -n ecommerce`
- Verify the NodePort is reachable: `kubectl port-forward svc/mysql 3306:3306 -n ecommerce`

**`401 Unauthorized` on all requests**
- Keycloak is not reachable or the realm/client is not configured.
- Confirm Keycloak is running: `kubectl logs deployment/keycloak -n ecommerce`
- Verify the JWK URI is accessible: http://localhost:30080/realms/microservices-realm/protocol/openid-connect/certs

**`403 Forbidden` — user has no roles**
- The test user in Keycloak does not have `USER` or `ADMIN` role assigned.
- Check **Users → Role mappings** in the Keycloak admin console.

**Gateway returns `503 Service Unavailable`**
- A downstream service's circuit breaker has tripped. Check actuator: http://localhost:2027/actuator/health
- Wait 30 seconds for the circuit breaker to transition to half-open, or restart the failing service.

**`client-secret` errors on startup**
- The placeholder `your-client-secret` / `your-service-client-secret` is still in `application.yaml` / `application.properties`.
- Follow [Update Secret Keys](#update-secret-keys) and restart the affected service.

**Kafka producer blocks on startup**
- Kafka pod is not yet ready. The producer has `max.block.ms=3000` so it will fail fast rather than hang.
- Check: `kubectl get pods -n ecommerce | grep kafka`

**Image not found in Kubernetes (`ErrImageNeverPull`)**
- With Rancher Desktop, images must be built into the `k8s.io` namespace using `nerdctl --namespace k8s.io build ...`
- Rebuild the affected image and redeploy the pod.

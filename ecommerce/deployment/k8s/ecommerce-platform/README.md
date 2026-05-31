# Ecommerce Platform — Kubernetes Deployment Guide

This document explains **every file** in this Helm-structured Kubernetes deployment for the ecommerce microservices platform running on **Rancher Desktop** (local development).

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Prerequisites](#2-prerequisites)
3. [Folder Structure](#3-folder-structure)
4. [Helm Chart Root Files](#4-helm-chart-root-files)
5. [Namespace](#5-namespace)
6. [Infrastructure Layer](#6-infrastructure-layer)
   - [MySQL](#61-mysql)
   - [Kafka](#62-kafka)
   - [Kafka UI](#63-kafka-ui)
   - [Keycloak](#64-keycloak)
7. [Application Layer](#7-application-layer)
   - [API Gateway](#71-api-gateway)
   - [User Service](#72-user-service)
   - [Product Service](#73-product-service)
   - [Order Service](#74-order-service)
8. [Ingress](#8-ingress)
9. [Observability Stack](#9-observability-stack)
   - [Prometheus](#91-prometheus)
   - [Grafana](#92-grafana)
   - [Tempo](#93-tempo)
   - [Jaeger](#94-jaeger)
   - [OpenTelemetry Collector](#95-opentelemetry-collector)
10. [Deployment Scripts](#10-deployment-scripts)
11. [Step-by-Step Deployment](#11-step-by-step-deployment)
12. [Build Docker Images](#12-build-docker-images)
13. [Service Access URLs](#13-service-access-urls)
14. [Configuration Reference](#14-configuration-reference)
15. [Troubleshooting](#15-troubleshooting)

---

## 1. Architecture Overview

```
                         ┌─────────────────────────────────────────────┐
                         │            ecommerce namespace               │
                         │                                              │
  Browser / Client ─────►│  Ingress (ecommerce.local)                  │
                         │      │                                       │
                         │      ▼                                       │
                         │  API Gateway (:2027)                         │
                         │      │                                       │
              ┌──────────┤      ├──────────────────────────────┐       │
              │          │      │                              │       │
              ▼          │      ▼                              ▼       │
     User Service        │  Product Service           Order Service    │
       (:2026)           │    (:2028)                   (:2029)        │
          │              │      │                          │           │
          │              │      └──────────┬───────────────┘           │
          │              │                 │                            │
          ▼              │                 ▼                            │
        MySQL            │          Kafka (:9092)                      │
        (:3306)          │                                              │
                         │   Keycloak (:8080) — JWT / OAuth2           │
                         │                                              │
                         │   Observability: Prometheus / Grafana /      │
                         │   OTel Collector → Tempo + Jaeger            │
                         └─────────────────────────────────────────────┘
```

**Request flow:**
1. External traffic hits the Nginx Ingress at `ecommerce.local`
2. Ingress routes all `/api/**` and `/auth/**` paths to the **API Gateway**
3. The API Gateway validates JWT tokens (via Keycloak) and routes to backend services
4. Backend services communicate directly via Kubernetes ClusterIP DNS

**Technology Stack:**
- Java 21 + Spring Boot 3.x
- Spring Cloud Gateway (API Gateway)
- Spring Security OAuth2 + Keycloak (authentication)
- Spring Data JPA + Flyway + MySQL (persistence)
- Apache Kafka (event streaming)
- Hazelcast (distributed caching)
- Kubernetes on Rancher Desktop

---

## 2. Prerequisites

### Required Tools

| Tool | Version | Purpose |
|------|---------|---------|
| Rancher Desktop | ≥ 1.9 | Local Kubernetes cluster |
| kubectl | ≥ 1.27 | Kubernetes CLI |
| Docker | ≥ 24 | Build container images |
| Helm | ≥ 3.12 | (optional) Chart management |

### Verify your cluster is running

```bash
kubectl cluster-info
kubectl get nodes
```

### Install NGINX Ingress Controller (required for Ingress)

```bash
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.10.1/deploy/static/provider/cloud/deploy.yaml

# Wait for it to be ready
kubectl wait --namespace ingress-nginx \
  --for=condition=ready pod \
  --selector=app.kubernetes.io/component=controller \
  --timeout=120s
```

### Add local DNS entry

Add this line to your hosts file (`C:\Windows\System32\drivers\etc\hosts` on Windows, `/etc/hosts` on Mac/Linux):

```
127.0.0.1  ecommerce.local
```

### Install Metrics Server (required for HPA)

```bash
kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml
```

---

## 3. Folder Structure

```
ecommerce-platform/
│
├── Chart.yaml                    ← Helm chart metadata
├── values.yaml                   ← Helm values (empty; fill per environment)
├── charts/                       ← Sub-charts directory (empty)
│
├── templates/
│   │
│   ├── namespace/
│   │   └── namespace.yaml        ← Creates the 'ecommerce' namespace
│   │
│   ├── infrastructure/           ← Stateful backing services
│   │   ├── mysql/
│   │   │   ├── secret.yaml       ← MySQL root password
│   │   │   ├── pvc.yaml          ← Persistent volume claim (2Gi)
│   │   │   ├── deployment.yaml   ← MySQL 8.0 pod
│   │   │   └── service.yaml      ← NodePort 30036
│   │   │
│   │   ├── kafka/
│   │   │   ├── configmap.yaml    ← KRaft broker configuration
│   │   │   ├── pvc.yaml          ← Persistent volume claim (5Gi)
│   │   │   ├── deployment.yaml   ← Kafka 3.9 (KRaft mode, no ZooKeeper)
│   │   │   └── service.yaml      ← NodePort 30092
│   │   │
│   │   ├── ui/
│   │   │   ├── ui-deployment.yaml ← Kafka UI (provectuslabs/kafka-ui)
│   │   │   └── ui-service.yaml   ← NodePort 30808
│   │   │
│   │   └── keycloak/
│   │       ├── secret.yaml       ← Keycloak admin password
│   │       ├── configmap.yaml    ← Keycloak environment config
│   │       ├── deployment.yaml   ← Keycloak 26.0.7 + PVC (1Gi)
│   │       └── service.yaml      ← NodePort 30080
│   │
│   ├── applications/             ← Microservices
│   │   ├── api-gateway/
│   │   │   ├── secret.yaml       ← Gateway + Keycloak client secrets
│   │   │   ├── configmap.yaml    ← Gateway non-sensitive config
│   │   │   ├── deployment.yaml   ← API Gateway pod
│   │   │   └── service.yaml      ← NodePort 30027
│   │   │
│   │   ├── user-service/
│   │   │   ├── secret.yaml       ← S3/MinIO credentials
│   │   │   ├── configmap.yaml    ← User service config
│   │   │   ├── deployment.yaml   ← User service pod
│   │   │   ├── service.yaml      ← ClusterIP :2026
│   │   │   └── hpa.yaml          ← HPA: 1–5 replicas at 80% CPU
│   │   │
│   │   ├── product-service/
│   │   │   ├── secret.yaml       ← S3/MinIO credentials
│   │   │   ├── configmap.yaml    ← Product service config
│   │   │   ├── deployment.yaml   ← Product service pod
│   │   │   ├── service.yaml      ← ClusterIP :2028
│   │   │   └── hpa.yaml          ← HPA: 1–5 replicas at 80% CPU
│   │   │
│   │   └── order-service/
│   │       ├── secret.yaml       ← (placeholder for future secrets)
│   │       ├── configmap.yaml    ← Order service config
│   │       ├── deployment.yaml   ← Order service pod
│   │       ├── service.yaml      ← ClusterIP :2029
│   │       └── hpa.yaml          ← HPA: 1–5 replicas at 80% CPU
│   │
│   ├── ingress/
│   │   └── ingress.yaml          ← NGINX Ingress for ecommerce.local
│   │
│   └── observability/
│       ├── prometheus/
│       │   ├── configmap.yaml    ← Scrape config (pods + static targets)
│       │   ├── deployment.yaml   ← Prometheus + RBAC + PVC (5Gi)
│       │   └── service.yaml      ← NodePort 30900
│       │
│       ├── grafana/
│       │   ├── pvc.yaml          ← Persistent volume (1Gi)
│       │   ├── deployment.yaml   ← Grafana + datasource provisioning
│       │   └── service.yaml      ← NodePort 30300
│       │
│       ├── tempo/
│       │   ├── configmap.yaml    ← Tempo tracing backend config
│       │   ├── deployment.yaml   ← Grafana Tempo 2.4.1
│       │   └── service.yaml      ← NodePort 30320
│       │
│       ├── jaeger/
│       │   ├── deployment.yaml   ← Jaeger all-in-one 1.57
│       │   └── service.yaml      ← NodePort 30686
│       │
│       └── collector/
│           ├── configmap.yaml    ← OTel Collector pipeline config
│           ├── deployment.yaml   ← OpenTelemetry Collector
│           └── service.yaml      ← NodePort 30417 (gRPC) / 30418 (HTTP)
│
└── scripts/
    ├── deploy-all.sh             ← Deploy everything in order
    ├── undeploy-all.sh           ← Tear down everything
    └── restart-services.sh       ← Rolling restart of microservices
```

---

## 4. Helm Chart Root Files

### `Chart.yaml`

Declares this directory as a Helm chart. Contains the chart name, description, and version.

```yaml
apiVersion: v2
name: ecommerce-platform
description: E-commerce microservices platform on Kubernetes
type: application
version: 1.0.0
appVersion: "1.0.0"
```

- `apiVersion: v2` — required for Helm 3
- `type: application` — a deployable chart (as opposed to a `library` chart)
- `version` — the chart's own version (increment when chart structure changes)
- `appVersion` — the application version being packaged

### `values.yaml`

Currently empty. This file is where you define default values that can be overridden at deploy time with `helm install -f custom-values.yaml`. Populate it when you want to parameterize image tags, replica counts, or resource limits across environments.

### `charts/` directory

Empty directory reserved for Helm sub-chart dependencies. For example, you could add a community MySQL chart here instead of maintaining your own MySQL manifests.

---

## 5. Namespace

### `templates/namespace/namespace.yaml`

```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: ecommerce
  labels:
    name: ecommerce
```

**What it does:** Creates the `ecommerce` Kubernetes namespace that isolates all platform resources from other workloads on the cluster.

**Why it matters:** Every resource in this chart has `namespace: ecommerce` set explicitly in its metadata. Kubernetes rejects resources that reference a namespace that doesn't yet exist, so this file must be applied first.

**Key point:** The label `name: ecommerce` is used by network policies (if you add them later) and can be used with `kubectl get pods -l name=ecommerce`.

---

## 6. Infrastructure Layer

### 6.1 MySQL

MySQL is the relational database used by **user-service**, **product-service**, and **order-service**. Each service uses its own database schema (`user_db`, `product_db`, `order_db`) within the same MySQL instance.

---

#### `templates/infrastructure/mysql/secret.yaml`

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: mysql-secret
  namespace: ecommerce
type: Opaque
data:
  root-password: cm9vdDEyMw==    # root123
  username: cm9vdA==             # root
  password: cm9vdDEyMw==        # root123
```

**What it does:** Stores MySQL credentials as base64-encoded values in a Kubernetes Secret. Pods reference individual keys via `secretKeyRef` so plain-text passwords never appear in ConfigMaps or environment variables in cleartext.

**Encoding:** Values are NOT encrypted — base64 is only obfuscation. Use [SealedSecrets](https://github.com/bitnami-labs/sealed-secrets) or [External Secrets Operator](https://external-secrets.io/) before deploying to a shared or production cluster.

**How to re-encode a new password:**
```bash
echo -n "my-new-password" | base64
```

**Keys:**
- `root-password` — used by the MySQL container to initialize `MYSQL_ROOT_PASSWORD`
- `username` — injected into microservices as `SPRING_DATASOURCE_USERNAME`
- `password` — injected into microservices as `SPRING_DATASOURCE_PASSWORD`

---

#### `templates/infrastructure/mysql/pvc.yaml`

```yaml
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: mysql-pvc
  namespace: ecommerce
spec:
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 2Gi
```

**What it does:** Requests a 2 GiB persistent disk from the cluster's storage provider. Rancher Desktop uses `local-path` provisioner, which allocates a directory on your local machine.

**Why `ReadWriteOnce`:** MySQL is a single-instance deployment. `ReadWriteOnce` means only one pod can mount the volume at a time, which is appropriate for a single-replica stateful workload.

**Data persistence:** Even if the MySQL pod crashes and restarts, all data survives because it's stored in the PVC on your host, not inside the ephemeral container filesystem.

---

#### `templates/infrastructure/mysql/deployment.yaml`

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: mysql
  namespace: ecommerce
spec:
  replicas: 1
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0
  template:
    spec:
      containers:
        - name: mysql
          image: mysql:8.0
          args:
            - --default-authentication-plugin=mysql_native_password
          env:
            - name: MYSQL_ROOT_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: mysql-secret
                  key: root-password
            - name: MYSQL_DATABASE
              value: user_db
          ports:
            - containerPort: 3306
          volumeMounts:
            - name: mysql-data
              mountPath: /var/lib/mysql
          readinessProbe:
            exec:
              command: ["mysqladmin", "ping", "-h", "localhost"]
          livenessProbe:
            exec:
              command: ["mysqladmin", "ping", "-h", "localhost"]
          resources:
            requests:
              memory: "256Mi"
              cpu: "250m"
            limits:
              memory: "512Mi"
              cpu: "500m"
```

**What it does:** Runs a single MySQL 8.0 instance with persistent storage.

**Key configuration details:**

| Setting | Value | Reason |
|---------|-------|--------|
| `--default-authentication-plugin=mysql_native_password` | CLI arg | Fixes "Public Key Retrieval is not allowed" errors with older JDBC drivers |
| `MYSQL_DATABASE: user_db` | Environment | Auto-creates `user_db` on first start; other DBs (`product_db`, `order_db`) are created by Flyway when each service starts with `createDatabaseIfNotExist=true` |
| `readinessProbe: mysqladmin ping` | Health check | Pod only receives traffic once MySQL is accepting connections |
| `livenessProbe: mysqladmin ping` | Health check | Pod is restarted if MySQL stops accepting connections |
| `resources.limits.memory: 512Mi` | Resource limit | Prevents MySQL from consuming all node memory |

**Rolling Update:** `maxSurge: 1, maxUnavailable: 0` means during updates, Kubernetes brings up the new pod before terminating the old one, ensuring zero downtime.

---

#### `templates/infrastructure/mysql/service.yaml`

```yaml
apiVersion: v1
kind: Service
metadata:
  name: mysql-service
  namespace: ecommerce
spec:
  type: NodePort
  selector:
    app: mysql
  ports:
    - name: mysql
      port: 3306
      targetPort: 3306
      nodePort: 30036
```

**What it does:** Exposes MySQL within the cluster as `mysql-service:3306` and externally on the host machine at `localhost:30036`.

**Why NodePort?** For local development, exposing MySQL on port `30036` allows you to connect from your host using any MySQL client (e.g., DBeaver, MySQL Workbench) without going through `kubectl port-forward`.

**In-cluster DNS:** Services reference MySQL with JDBC URL `jdbc:mysql://mysql-service:3306/...` — Kubernetes DNS resolves `mysql-service` to the ClusterIP automatically.

---

### 6.2 Kafka

Apache Kafka is the event streaming backbone used by all three business services (user-service, product-service, order-service) for publishing and consuming domain events.

---

#### `templates/infrastructure/kafka/configmap.yaml`

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: kafka-config
  namespace: ecommerce
data:
  KAFKA_NODE_ID: "1"
  KAFKA_PROCESS_ROLES: "broker,controller"
  KAFKA_CONTROLLER_QUORUM_VOTERS: "1@localhost:9093"
  CLUSTER_ID: "MkU3OEVBNTcwNTJENDM2Qk"
  KAFKA_LISTENERS: "PLAINTEXT://:9092,CONTROLLER://:9093,EXTERNAL://:9094"
  KAFKA_ADVERTISED_LISTENERS: "PLAINTEXT://kafka-service.ecommerce.svc.cluster.local:9092,EXTERNAL://localhost:30092"
  ...
```

**What it does:** Configures Kafka in **KRaft mode** (no ZooKeeper). All Kafka configuration is separated into a ConfigMap so the Deployment YAML stays clean.

**KRaft mode explained:** KRaft replaces ZooKeeper with a built-in Raft consensus protocol. The same broker process acts as both broker and controller (`KAFKA_PROCESS_ROLES: broker,controller`).

**Listener types:**

| Listener | Port | Purpose |
|----------|------|---------|
| `PLAINTEXT` | 9092 | In-cluster pod-to-pod traffic (microservices → Kafka) |
| `CONTROLLER` | 9093 | KRaft internal consensus (not exposed outside the pod) |
| `EXTERNAL` | 9094 → NodePort 30092 | Host machine developer access |

**Advertised listeners:** This is what clients use to reconnect after the initial bootstrap.
- `PLAINTEXT://kafka-service.ecommerce.svc.cluster.local:9092` — used by pods inside the cluster
- `EXTERNAL://localhost:30092` — used by tools on your development machine (Kafka CLI, Offset Explorer, etc.)

---

#### `templates/infrastructure/kafka/pvc.yaml`

Claims 5 GiB of persistent storage for Kafka log segments. Kafka stores all messages on disk, so if the pod restarts, all unconsumed messages remain available for consumers to process.

---

#### `templates/infrastructure/kafka/deployment.yaml`

Deploys a single Kafka 3.9.0 broker in KRaft mode. It loads all configuration from the ConfigMap via `envFrom: configMapRef`. Health probes use TCP socket checks against port 9092 — if Kafka stops accepting connections, the pod is restarted.

---

#### `templates/infrastructure/kafka/service.yaml`

```yaml
ports:
  - name: plaintext
    port: 9092
    targetPort: 9092
  - name: external
    port: 9094
    targetPort: 9094
    nodePort: 30092
```

The `plaintext` port has no `nodePort` — it is intentionally cluster-internal only. Microservices connect via `kafka-service:9092`. The `external` port (`30092`) is only for developer tools on the host machine.

---

### 6.3 Kafka UI

Kafka UI provides a web interface to browse topics, consumer groups, and messages.

---

#### `templates/infrastructure/ui/ui-deployment.yaml`

```yaml
image: provectuslabs/kafka-ui:latest
env:
  - name: KAFKA_CLUSTERS_0_NAME
    value: ecommerce-kafka
  - name: KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS
    value: kafka-service:9092
```

**What it does:** Runs the [Kafka UI](https://github.com/provectus/kafka-ui) web application, pre-configured to connect to your in-cluster Kafka broker at `kafka-service:9092`.

**Access:** `http://localhost:30808` (via NodePort defined in `ui-service.yaml`)

---

#### `templates/infrastructure/ui/ui-service.yaml`

Exposes Kafka UI on NodePort `30808`. This is a development convenience — do not expose this in production without authentication.

---

### 6.4 Keycloak

Keycloak is the Identity and Access Management (IAM) server responsible for:
- Issuing JWT access tokens
- Managing users and roles
- Serving JWKS endpoints that microservices use to validate tokens

---

#### `templates/infrastructure/keycloak/secret.yaml`

```yaml
data:
  admin-password: YWRtaW4xMjM=    # admin123
```

Stores the Keycloak admin console password. After first deployment, log in at `http://localhost:30080/admin` with `admin` / `admin123` and immediately change this password.

---

#### `templates/infrastructure/keycloak/configmap.yaml`

```yaml
data:
  KEYCLOAK_ADMIN: admin
  KC_DB: dev-file
  KC_HOSTNAME_STRICT: "false"
  KC_HTTP_ENABLED: "true"
  KC_PROXY: edge
```

**Key settings:**

| Setting | Value | Reason |
|---------|-------|--------|
| `KC_DB: dev-file` | H2 file-based | Persistent H2 DB (no external DB needed for dev) — stored in the PVC |
| `KC_HOSTNAME_STRICT: false` | Disabled | Allows access via `localhost:30080` and cluster DNS without a fixed hostname |
| `KC_HTTP_ENABLED: true` | Enabled | Local dev has no TLS certificate; Keycloak runs on plain HTTP |
| `KC_PROXY: edge` | Edge proxy mode | Tells Keycloak that a reverse proxy (Ingress) sits in front; it trusts `X-Forwarded-*` headers |

---

#### `templates/infrastructure/keycloak/deployment.yaml`

Contains both the PVC (1 GiB for Keycloak's H2 file database) and the Deployment. Keycloak starts with `args: ["start-dev"]` which enables development mode — fast startup, dev-friendly defaults.

**Health probes:** Both readiness and liveness probes check `/realms/master` — this endpoint is only available once Keycloak has fully initialized the master realm, making it a reliable indicator of readiness.

**Important after first start:** You must import the `microservices-realm` into Keycloak manually or via an init container. The realm contains:
- `microservices-realm` realm
- Clients: `apigateway-client`, `user-service-client`
- Roles: `USER`, `ADMIN`

---

#### `templates/infrastructure/keycloak/service.yaml`

Exposes Keycloak on NodePort `30080`. Microservices inside the cluster access Keycloak at `http://keycloak:8080` (ClusterIP DNS). Your browser accesses it at `http://localhost:30080`.

---

## 7. Application Layer

All microservices follow the same pattern:
1. **ConfigMap** — all non-sensitive Spring Boot properties as environment variables
2. **Secret** — sensitive values (passwords, client secrets)
3. **Deployment** — pod specification with health probes and resource limits
4. **Service** — network endpoint (ClusterIP for internal-only, NodePort for gateway)
5. **HPA** — Horizontal Pod Autoscaler (user/product/order services only)

Spring Boot automatically maps environment variables to properties: `SPRING_DATASOURCE_URL` → `spring.datasource.url`.

---

### 7.1 API Gateway

The API Gateway is the **single entry point** for all external traffic. It handles:
- JWT validation via Keycloak JWKS endpoint
- Route matching and load balancing to downstream services
- Rate limiting (Bucket4j)
- Circuit breaking (Resilience4j)
- OAuth2 login flow (authorization_code grant)

---

#### `templates/applications/api-gateway/secret.yaml`

Contains two Secrets:

**`gateway-secret`:**
```yaml
data:
  internal-secret: Z3ctc2VjcmV0LWNoYW5nZS1pbi1wcm9k   # gw-secret-change-in-prod
```
This shared secret is injected into all microservices as `GATEWAY_INTERNAL_SECRET`. Every incoming request to a microservice validates the `X-Gateway-Secret` header against this value, ensuring no request bypasses the gateway. **Change this in production.**

**`keycloak-clients-secret`:**
```yaml
data:
  apigateway-client-secret: ...       # OAuth2 client secret for browser login
  user-service-client-secret: ...     # OAuth2 client secret for service-to-service calls
```
Obtained from Keycloak Admin → Clients → Credentials tab after importing the realm. **Must be replaced with real values before first deployment.**

---

#### `templates/applications/api-gateway/configmap.yaml`

Key configuration values:

| Variable | Value | Purpose |
|----------|-------|---------|
| `SERVER_PORT` | `2027` | Gateway listens on port 2027 |
| `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE` | `http://serviceregistry:8761/eureka` | Service discovery registration |
| `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI` | `http://keycloak:8080/realms/...` | Gateway validates incoming JWTs against Keycloak's public keys |
| `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_KEYCLOAK_REDIRECT_URI` | `http://ecommerce.local/login/oauth2/code/keycloak` | Browser OAuth2 callback URL after login |
| `MANAGEMENT_OTLP_TRACING_ENDPOINT` | `http://otel-collector:4318/v1/traces` | Sends distributed traces to OTel Collector |

---

#### `templates/applications/api-gateway/deployment.yaml`

```yaml
strategy:
  type: RollingUpdate
  rollingUpdate:
    maxSurge: 1
    maxUnavailable: 0
```

**Rolling update strategy** ensures zero-downtime deployments. `maxSurge: 1` means one extra pod can be temporarily created during an update. `maxUnavailable: 0` means existing pods are never terminated until a healthy replacement is ready.

**Health probes:**

| Probe | Endpoint | Purpose |
|-------|----------|---------|
| `startupProbe` | `/actuator/health` | Waits up to 110 seconds for the JVM + Spring context to start before the liveness probe kicks in |
| `readinessProbe` | `/actuator/health/readiness` | Removes pod from load balancer if it can't serve traffic (e.g., Keycloak is down) |
| `livenessProbe` | `/actuator/health/liveness` | Restarts pod if it deadlocks or enters an unrecoverable state |

**Resource limits:**
```yaml
resources:
  requests:
    memory: "256Mi"
    cpu: "250m"
  limits:
    memory: "512Mi"
    cpu: "500m"
```
`requests` are what Kubernetes uses for scheduling (which node has enough capacity). `limits` are the hard ceiling — the container is killed if it exceeds the memory limit.

**Prometheus scraping annotation:**
```yaml
annotations:
  prometheus.io/scrape: "true"
  prometheus.io/path: "/actuator/prometheus"
  prometheus.io/port: "2027"
```
These annotations are read by the Prometheus pod-discovery scrape config, automatically adding the gateway as a scrape target without any manual Prometheus configuration.

---

#### `templates/applications/api-gateway/service.yaml`

```yaml
type: NodePort
ports:
  - port: 2027
    nodePort: 30027
```

The gateway is the only application service exposed as a NodePort, allowing direct access at `http://localhost:30027` for development without going through Ingress.

---

### 7.2 User Service

Manages user accounts, authentication endpoints, and session management. Connects to MySQL (`user_db`), Kafka (publishes user events), and Keycloak (token validation + session revocation).

---

#### `templates/applications/user-service/secret.yaml`

Stores S3/MinIO credentials (for future user avatar uploads). The MySQL credentials come from `mysql-secret` (infrastructure layer), and the Keycloak client secret comes from `keycloak-clients-secret` (api-gateway layer).

---

#### `templates/applications/user-service/configmap.yaml`

Key entries beyond the standard MySQL/Kafka/Keycloak config:

| Variable | Value | Purpose |
|----------|-------|---------|
| `SPRING_FLYWAY_BASELINE_ON_MIGRATE` | `true` | Allows Flyway to run on an existing schema (required for first-time setup if `user_db` already has tables) |
| `SPRING_FLYWAY_BASELINE_VERSION` | `0` | Flyway treats V0 as the baseline; applies all migrations from V1 onward |
| `KEYCLOAK_ADMIN_SERVER_URL` | `http://keycloak:8080` | Used by the service account to call Keycloak Admin API for session revocation |
| `SPRING_CACHE_TYPE` | `hazelcast` | Activates Hazelcast distributed caching (configured via `hazelcast.xml` in the jar) |

---

#### `templates/applications/user-service/deployment.yaml`

**Startup probe detail:**
```yaml
startupProbe:
  initialDelaySeconds: 30
  periodSeconds: 10
  failureThreshold: 12   # 30 + 12*10 = 150s max startup time
```
The service needs time for: JVM startup (~15s) + Spring context initialization (~10s) + Flyway migration (~5-30s depending on schema state). The startup probe gives up to 150 seconds total before declaring failure.

**Secret injection pattern:**
```yaml
env:
  - name: SPRING_DATASOURCE_USERNAME
    valueFrom:
      secretKeyRef:
        name: mysql-secret
        key: username
```
Each sensitive value is injected individually from its source Secret. This is more granular than mounting the entire Secret as a volume and lets you see exactly which secrets a pod needs.

---

#### `templates/applications/user-service/service.yaml`

```yaml
type: ClusterIP
ports:
  - port: 2026
```

`ClusterIP` means this service is **only reachable from within the cluster**. External traffic must go through the API Gateway. This is a security boundary — no external client can bypass the gateway to hit the user service directly.

**Order-service calls user-service** at `http://user-service:2026` (set in order-service's ConfigMap as `SERVICES_USER_SERVICE_URL`).

---

#### `templates/applications/user-service/hpa.yaml`

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: user-service-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: user-service
  minReplicas: 1
  maxReplicas: 5
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 80
  behavior:
    scaleDown:
      stabilizationWindowSeconds: 120
```

**How HPA works:**
1. Metrics Server continuously collects CPU usage from pods
2. HPA evaluates the metric every 15 seconds
3. If average CPU across all pods exceeds 80%, HPA scales up (adds pods)
4. `stabilizationWindowSeconds: 120` prevents flapping — Kubernetes waits 2 minutes of sustained low CPU before scaling down

**Prerequisites:** Metrics Server must be installed (`kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml`).

---

### 7.3 Product Service

Manages product catalog, inventory, and image uploads. Connects to MySQL (`product_db`), Kafka (publishes product and stock events), and MinIO/S3 (product image storage).

---

#### `templates/applications/product-service/secret.yaml`

Stores MinIO/S3 credentials (`access-key`, `secret-key`). For local development, MinIO defaults are `minioadmin`/`minioadmin`. These are injected as `CLOUD_AWS_CREDENTIALS_ACCESS_KEY` and `CLOUD_AWS_CREDENTIALS_SECRET_KEY`.

---

#### `templates/applications/product-service/configmap.yaml`

Additional product-specific config:

| Variable | Value | Purpose |
|----------|-------|---------|
| `CLOUD_AWS_S3_BUCKET` | `product-images` | S3/MinIO bucket for product images |
| `CLOUD_AWS_S3_ENDPOINT` | `http://minio:9000` | MinIO in-cluster endpoint (remove for AWS S3) |
| `SPRING_KAFKA_CONSUMER_GROUP_ID` | `product-service-alerts` | Unique consumer group for stock alert events |

---

#### `templates/applications/product-service/deployment.yaml`

Identical structure to user-service. The product service exposes port `2028` and is reachable within the cluster at `http://product-service:2028`. Order-service uses this URL to check and deduct stock.

---

#### `templates/applications/product-service/service.yaml` and `hpa.yaml`

ClusterIP service on port 2028. HPA configured identically to user-service (1–5 replicas, 80% CPU threshold).

---

### 7.4 Order Service

Orchestrates order creation and lifecycle. Calls user-service (validate user) and product-service (deduct stock) via Feign HTTP clients with Resilience4j circuit breakers and retries. Publishes and consumes Kafka events for payment status updates.

---

#### `templates/applications/order-service/secret.yaml`

Placeholder secret (no order-specific secrets currently). Order service uses `mysql-secret` and `gateway-secret` which are declared in other files.

---

#### `templates/applications/order-service/configmap.yaml`

Key order-specific configuration:

| Variable | Value | Purpose |
|----------|-------|---------|
| `SERVICES_USER_SERVICE_URL` | `http://user-service:2026` | Feign client base URL — Kubernetes DNS resolves this |
| `SERVICES_PRODUCT_SERVICE_URL` | `http://product-service:2028` | Feign client base URL |
| `SPRING_KAFKA_PRODUCER_ACKS` | `all` | Strongest durability guarantee — leader waits for all in-sync replicas |
| `SPRING_KAFKA_PRODUCER_PROPERTIES_ENABLE_IDEMPOTENCE` | `true` | Prevents duplicate messages on producer retry |
| `RESILIENCE4J_CIRCUITBREAKER_INSTANCES_USER_SERVICE_FAILURE_RATE_THRESHOLD` | `50` | Circuit opens if 50% of calls in the window fail |
| `RESILIENCE4J_CIRCUITBREAKER_INSTANCES_USER_SERVICE_WAIT_DURATION_IN_OPEN_STATE` | `30s` | Circuit stays open 30 seconds before trying half-open |
| `RESILIENCE4J_RETRY_INSTANCES_PRODUCT_SERVICE_MAX_ATTEMPTS` | `3` | Retry failed product-service calls up to 3 times |

**Internal service communication pattern:**
```
Order Service
  → (Feign HTTP)   user-service:2026   → MySQL user_db
  → (Feign HTTP)   product-service:2028 → MySQL product_db
  → (Kafka publish) kafka-service:9092
```

---

#### `templates/applications/order-service/hpa.yaml`

Same HPA configuration as user-service and product-service: 1–5 replicas scaling at 80% CPU.

---

## 8. Ingress

### `templates/ingress/ingress.yaml`

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: ecommerce-ingress
  namespace: ecommerce
  annotations:
    kubernetes.io/ingress.class: nginx
    nginx.ingress.kubernetes.io/proxy-read-timeout: "300"
    nginx.ingress.kubernetes.io/limit-rps: "100"
spec:
  rules:
    - host: ecommerce.local
      http:
        paths:
          - path: /api/users
            pathType: Prefix
            backend:
              service:
                name: apigateway
                port:
                  number: 2027
          - path: /api/products
            ...
          - path: /api/orders
            ...
          - path: /auth
            ...
          - path: /
            ...
```

**What it does:** Configures the NGINX Ingress Controller to route HTTP traffic from `ecommerce.local` to the API Gateway.

**How requests flow:**
```
Browser → ecommerce.local → NGINX Ingress → apigateway:2027 → backend microservice
```

**All paths route to the API Gateway.** The gateway then applies its own routing rules (defined in its `application.yml`) to forward requests to the correct downstream service.

**Annotations explained:**

| Annotation | Value | Effect |
|------------|-------|--------|
| `kubernetes.io/ingress.class: nginx` | nginx | Selects the NGINX Ingress Controller (in case multiple Ingress controllers are installed) |
| `proxy-read-timeout: 300` | 300s | Prevents 502 errors on long-running requests (streaming, slow queries) |
| `proxy-connect-timeout: 10` | 10s | Fails fast if backend pod is not accepting connections |
| `use-forwarded-headers: true` | true | Passes real client IP in `X-Forwarded-For` header to the gateway |
| `enable-gzip: true` | true | Compresses responses at the Ingress layer, reducing bandwidth |
| `limit-rps: 100` | 100 req/s | Rate-limits at the Ingress layer (per client IP) as a DDoS defense layer |

**Local DNS setup (required):**
```
# Windows: C:\Windows\System32\drivers\etc\hosts
# Mac/Linux: /etc/hosts
127.0.0.1  ecommerce.local
```

---

## 9. Observability Stack

The observability stack provides three pillars of observability:

| Pillar | Tool | Purpose |
|--------|------|---------|
| **Metrics** | Prometheus + Grafana | CPU, memory, JVM, request rate, error rate |
| **Traces** | OTel Collector → Tempo + Jaeger | Distributed request tracing across services |
| **Logs** | Spring Boot → stdout → kubectl logs | Structured application logs |

**Trace flow:**
```
Microservice (OTel SDK)
  → HTTP POST /v1/traces
  → OTel Collector (:4318)
  → [fan-out]
      → Jaeger (:4317)   — for querying individual traces
      → Tempo (:4317)    — for Grafana trace visualization
```

---

### 9.1 Prometheus

#### `templates/observability/prometheus/configmap.yaml`

Contains the `prometheus.yml` scrape configuration with two strategies:

**1. Static scrape targets** (explicit service names):
```yaml
- job_name: user-service
  metrics_path: /actuator/prometheus
  static_configs:
    - targets: ['user-service.ecommerce.svc.cluster.local:2026']
```
Prometheus polls `http://user-service.ecommerce.svc.cluster.local:2026/actuator/prometheus` every 15 seconds and stores all Spring Boot metrics.

**2. Kubernetes pod auto-discovery:**
```yaml
- job_name: kubernetes-pods
  kubernetes_sd_configs:
    - role: pod
      namespaces:
        names: [ecommerce]
  relabel_configs:
    - source_labels: [__meta_kubernetes_pod_annotation_prometheus_io_scrape]
      action: keep
      regex: "true"
```
Any pod in the `ecommerce` namespace with the annotation `prometheus.io/scrape: "true"` is automatically discovered and scraped. This means new services are scraped without editing the Prometheus config.

---

#### `templates/observability/prometheus/deployment.yaml`

Contains the Deployment, a 5 GiB PVC for metric storage, and RBAC resources:

**ServiceAccount + ClusterRole + ClusterRoleBinding:** Prometheus needs permission to call the Kubernetes API to discover pods. The RBAC rules grant read access to `pods`, `services`, and `endpoints` across all namespaces.

**Retention:** `--storage.tsdb.retention.time=7d` keeps 7 days of metrics data. Increase for production.

**Web lifecycle:** `--web.enable-lifecycle` allows hot-reloading the config via `curl -X POST http://prometheus:9090/-/reload` without restarting the pod.

---

#### `templates/observability/prometheus/service.yaml`

NodePort `30900` — access Prometheus at `http://localhost:30900`.

---

### 9.2 Grafana

#### `templates/observability/grafana/pvc.yaml`

1 GiB PVC stores Grafana's SQLite database, which holds dashboards, user accounts, and alert rules you create through the UI.

---

#### `templates/observability/grafana/deployment.yaml`

Contains a ConfigMap with pre-provisioned datasources and the Deployment.

**Pre-provisioned datasources** (auto-configured on startup):

| Datasource | URL | Purpose |
|-----------|-----|---------|
| Prometheus | `http://prometheus:9090` | Default datasource for metrics dashboards |
| Tempo | `http://tempo:3200` | Distributed trace visualization |
| Jaeger | `http://jaeger:16686` | Trace query and analysis |

The datasource provisioning ConfigMap is mounted at `/etc/grafana/provisioning/datasources/` — Grafana reads this directory on startup and registers all datasources automatically. You do **not** need to manually add datasources after deployment.

**Security context:**
```yaml
securityContext:
  fsGroup: 472
  runAsUser: 472
```
Grafana container runs as UID 472 (Grafana's non-root user). `fsGroup: 472` ensures the PVC is writable by this user.

---

#### `templates/observability/grafana/service.yaml`

NodePort `30300` — access Grafana at `http://localhost:30300`. Default login: `admin` / `admin`.

**Recommended dashboards to import:**
- Spring Boot Statistics: ID `12900`
- JVM Micrometer: ID `4701`
- Kubernetes cluster monitoring: ID `315`

---

### 9.3 Tempo

Grafana Tempo is a **distributed tracing backend** that stores and queries traces. Unlike Jaeger, Tempo is designed to scale cheaply using object storage (S3, GCS) in production.

---

#### `templates/observability/tempo/configmap.yaml`

```yaml
data:
  tempo.yaml: |
    server:
      http_listen_port: 3200
    distributor:
      receivers:
        otlp:
          protocols:
            grpc:
              endpoint: 0.0.0.0:4317
            http:
              endpoint: 0.0.0.0:4318
    storage:
      trace:
        backend: local
        local:
          path: /var/tempo/traces
```

**What it does:** Configures Tempo to accept OTLP traces on ports 4317 (gRPC) and 4318 (HTTP), and store them locally in `/var/tempo/traces` (ephemeral `emptyDir` — traces are lost when the pod restarts, acceptable for local dev).

**`block_retention: 24h`** — traces older than 24 hours are deleted automatically to control disk usage.

---

#### `templates/observability/tempo/deployment.yaml`

Runs Grafana Tempo 2.4.1. Uses `emptyDir` for storage (no PVC needed for local dev). In production, switch to a PVC or configure S3 storage in `tempo.yaml`.

---

#### `templates/observability/tempo/service.yaml`

| Port | NodePort | Purpose |
|------|----------|---------|
| 3200 | 30320 | Tempo HTTP API (Grafana queries traces here) |
| 4317 | — | OTLP gRPC (from OTel Collector) |
| 4318 | — | OTLP HTTP (from OTel Collector) |

---

### 9.4 Jaeger

Jaeger is a **distributed tracing system** with a rich UI for analyzing traces, comparing service call patterns, and identifying slow operations.

---

#### `templates/observability/jaeger/deployment.yaml`

Runs `jaegertracing/all-in-one:1.57` — a single container with collector, query engine, and UI combined. Suitable for local development only.

```yaml
env:
  - name: COLLECTOR_OTLP_ENABLED
    value: "true"
  - name: SPAN_STORAGE_TYPE
    value: memory
  - name: MEMORY_MAX_TRACES
    value: "50000"
```

- `COLLECTOR_OTLP_ENABLED: true` — Jaeger accepts OTLP format (what the OTel Collector sends)
- `SPAN_STORAGE_TYPE: memory` — traces are stored in RAM; lost on pod restart (for dev)
- `MEMORY_MAX_TRACES: 50000` — limits memory consumption by evicting old traces

---

#### `templates/observability/jaeger/service.yaml`

| Port | NodePort | Purpose |
|------|----------|---------|
| 16686 | 30686 | Jaeger UI — access at `http://localhost:30686` |
| 4317 | — | OTLP gRPC ingestion (from OTel Collector) |
| 4318 | — | OTLP HTTP ingestion |
| 14268 | — | Jaeger Thrift HTTP (legacy clients) |

---

### 9.5 OpenTelemetry Collector

The OTel Collector is a **vendor-neutral telemetry pipeline** that receives traces from all microservices, processes them, and fans out to multiple backends simultaneously.

---

#### `templates/observability/collector/configmap.yaml`

```yaml
receivers:
  otlp:
    protocols:
      grpc: { endpoint: 0.0.0.0:4317 }
      http: { endpoint: 0.0.0.0:4318 }

processors:
  batch:
    timeout: 1s
    send_batch_size: 1024
  memory_limiter:
    limit_mib: 200

exporters:
  otlp/jaeger:
    endpoint: jaeger:4317
    tls: { insecure: true }
  otlp/tempo:
    endpoint: tempo:4317
    tls: { insecure: true }

service:
  pipelines:
    traces:
      receivers:  [otlp]
      processors: [memory_limiter, batch]
      exporters:  [otlp/jaeger, otlp/tempo, logging]
```

**Pipeline explained:**

```
Microservice → OTLP HTTP :4318 → [Receivers]
                                        ↓
                              [Processors]
                              memory_limiter (drops data if RAM > 200MB)
                              batch (accumulates spans, sends in bulk)
                                        ↓
                              [Exporters — fan-out]
                              ├── Jaeger :4317  → UI at localhost:30686
                              └── Tempo  :4317  → Grafana datasource
```

**Why both Jaeger AND Tempo?**
- **Jaeger** has a better standalone trace inspection UI with flamegraph views
- **Tempo** integrates with Grafana dashboards, allowing you to correlate traces with metrics on the same screen (TraceQL queries)

**`batch` processor:** Accumulates spans for 1 second before sending, dramatically reducing the number of HTTP connections and improving throughput.

**`memory_limiter` processor:** If the collector's memory exceeds 200 MiB, it drops new spans. This prevents the collector from causing an out-of-memory pod kill during traffic spikes.

---

#### `templates/observability/collector/service.yaml`

| Port | NodePort | Purpose |
|------|----------|---------|
| 4317 | 30417 | OTLP gRPC — microservices can also send directly here |
| 4318 | 30418 | OTLP HTTP — Spring Boot Actuator sends traces here |
| 8888 | — | Collector's own Prometheus metrics |

**Spring Boot trace endpoint configuration (in all microservices):**
```
MANAGEMENT_OTLP_TRACING_ENDPOINT = http://otel-collector:4318/v1/traces
```

---

## 10. Deployment Scripts

### `scripts/deploy-all.sh`

Deploys all resources in the correct dependency order:

```
Namespace → MySQL → Kafka → Kafka UI → Keycloak (wait for ready)
→ Secrets → API Gateway → User Service → Product Service → Order Service
→ Ingress → Observability stack
```

**Why order matters:**
- Keycloak must be fully started before microservices attempt to validate JWTs
- MySQL must be running before services with Flyway migrations start
- Secrets must exist before Deployments that reference them

**Usage:**
```bash
cd deployment/k8s/ecommerce-platform
bash scripts/deploy-all.sh
```

---

### `scripts/undeploy-all.sh`

Removes all resources in reverse order (applications first, infrastructure last). Uses `--ignore-not-found` so partial deployments can be cleaned up without errors.

**Note:** PVCs are NOT deleted. Your MySQL and Kafka data persists. To delete everything including data:
```bash
kubectl delete namespace ecommerce
```

---

### `scripts/restart-services.sh`

Triggers a rolling restart of microservices (useful after rebuilding Docker images).

```bash
# Restart all microservices
bash scripts/restart-services.sh

# Restart only one service
bash scripts/restart-services.sh user-service
bash scripts/restart-services.sh product-service
bash scripts/restart-services.sh order-service
bash scripts/restart-services.sh apigateway
```

A rolling restart creates new pods from the latest image pulled from the local Docker daemon, then terminates old pods — no downtime.

---

## 11. Step-by-Step Deployment

### Step 1: Build Docker images

```bash
# Run from the ecommerce/ project root
docker build -t ecommerce/user-service:latest ./services/user-service
docker build -t ecommerce/product-service:latest ./services/product-service
docker build -t ecommerce/order-service:latest ./services/order-service
docker build -t ecommerce/apigateway:latest ./services/apigateway
```

### Step 2: Apply the namespace first

```bash
kubectl apply -f templates/namespace/namespace.yaml
```

### Step 3: Deploy infrastructure

```bash
kubectl apply -n ecommerce -f templates/infrastructure/mysql/
kubectl apply -n ecommerce -f templates/infrastructure/kafka/
kubectl apply -n ecommerce -f templates/infrastructure/ui/
kubectl apply -n ecommerce -f templates/infrastructure/keycloak/
```

Wait for Keycloak to be ready:
```bash
kubectl rollout status deployment/keycloak -n ecommerce --timeout=120s
```

### Step 4: Configure Keycloak realm

After Keycloak is running, import the realm:
1. Open `http://localhost:30080/admin`
2. Login with `admin` / `admin123`
3. Create realm `microservices-realm`
4. Create clients `apigateway-client` and `user-service-client`
5. Copy the client secrets and update the base64-encoded values in `templates/applications/api-gateway/secret.yaml`

Re-apply the secret after updating:
```bash
kubectl apply -n ecommerce -f templates/applications/api-gateway/secret.yaml
```

### Step 5: Deploy application secrets and services

```bash
kubectl apply -n ecommerce -f templates/applications/api-gateway/secret.yaml
kubectl apply -n ecommerce -f templates/applications/user-service/secret.yaml
kubectl apply -n ecommerce -f templates/applications/product-service/secret.yaml
kubectl apply -n ecommerce -f templates/applications/order-service/secret.yaml

kubectl apply -n ecommerce -f templates/applications/api-gateway/
kubectl apply -n ecommerce -f templates/applications/user-service/
kubectl apply -n ecommerce -f templates/applications/product-service/
kubectl apply -n ecommerce -f templates/applications/order-service/
```

### Step 6: Deploy Ingress

```bash
kubectl apply -n ecommerce -f templates/ingress/ingress.yaml
```

### Step 7: Deploy observability

```bash
kubectl apply -n ecommerce -f templates/observability/prometheus/
kubectl apply -n ecommerce -f templates/observability/grafana/
kubectl apply -n ecommerce -f templates/observability/tempo/
kubectl apply -n ecommerce -f templates/observability/jaeger/
kubectl apply -n ecommerce -f templates/observability/collector/
```

### Step 8: Verify everything is running

```bash
kubectl get pods -n ecommerce
kubectl get services -n ecommerce
kubectl get hpa -n ecommerce
kubectl get ingress -n ecommerce
```

Expected output — all pods should be `Running`:
```
NAME                              READY   STATUS    RESTARTS
apigateway-xxxx                   1/1     Running   0
user-service-xxxx                 1/1     Running   0
product-service-xxxx              1/1     Running   0
order-service-xxxx                1/1     Running   0
mysql-xxxx                        1/1     Running   0
kafka-xxxx                        1/1     Running   0
kafka-ui-xxxx                     1/1     Running   0
keycloak-xxxx                     1/1     Running   0
prometheus-xxxx                   1/1     Running   0
grafana-xxxx                      1/1     Running   0
tempo-xxxx                        1/1     Running   0
jaeger-xxxx                       1/1     Running   0
otel-collector-xxxx               1/1     Running   0
```

---

## 12. Build Docker Images

All Dockerfiles use a **multi-stage build**:

```dockerfile
# Stage 1: Build — Maven with JDK 21
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline -B      # cache dependencies layer
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Runtime — JRE 21 Alpine (no JDK, no Maven, minimal image size)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN apk add --no-cache curl           # for HEALTHCHECK
COPY --from=builder /build/target/*.jar app.jar
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
```

**Why multi-stage?**
- The builder stage contains Maven, JDK, source code, and build cache — **not shipped in the final image**
- The runtime stage contains only the JRE and the compiled JAR — significantly smaller image (~200MB vs ~700MB)

**JVM flags explained:**
- `-XX:+UseContainerSupport` — JVM reads CPU and memory limits from cgroup instead of the host
- `-XX:MaxRAMPercentage=75.0` — JVM heap is capped at 75% of the container's memory limit (e.g., 384MB heap in a 512MB limit container)

**Build all images at once:**
```bash
# From ecommerce/ project root
for svc in apigateway user-service product-service order-service serviceregistry; do
  docker build -t ecommerce/$svc:latest ./$svc
  echo "Built ecommerce/$svc:latest"
done
```

---

## 13. Service Access URLs

| Service | URL | Notes |
|---------|-----|-------|
| API Gateway | `http://localhost:30027` | Direct NodePort access |
| API Gateway (Ingress) | `http://ecommerce.local` | Requires `/etc/hosts` entry |
| Swagger UI (user-service) | `http://ecommerce.local/swagger-ui/index.html` | Via gateway |
| Keycloak Admin | `http://localhost:30080/admin` | admin / admin123 |
| Kafka UI | `http://localhost:30808` | Browse topics/messages |
| Prometheus | `http://localhost:30900` | Metrics query UI |
| Grafana | `http://localhost:30300` | Dashboards (admin / admin) |
| Jaeger UI | `http://localhost:30686` | Trace search |
| Tempo HTTP | `http://localhost:30320` | Query via Grafana datasource |
| OTel Collector (gRPC) | `localhost:30417` | For direct gRPC trace submission |
| OTel Collector (HTTP) | `localhost:30418` | For direct HTTP trace submission |
| MySQL | `localhost:30036` | DB clients (root / root123) |
| Kafka | `localhost:30092` | Kafka CLI / desktop clients |

### Keycloak Token URL (for Postman / curl testing)

```
POST http://localhost:30080/realms/microservices-realm/protocol/openid-connect/token
Content-Type: application/x-www-form-urlencoded

grant_type=password&client_id=apigateway-client&username=testuser&password=testpass
```

---

## 14. Configuration Reference

### Spring Boot Environment Variable Mapping

Spring Boot converts environment variables to properties by:
1. Converting to lowercase
2. Replacing `_` with `.`

Examples:
```
SPRING_DATASOURCE_URL  →  spring.datasource.url
SPRING_KAFKA_BOOTSTRAP_SERVERS  →  spring.kafka.bootstrap-servers
EUREKA_CLIENT_SERVICEURL_DEFAULTZONE  →  eureka.client.service-url.defaultZone
```

### Kubernetes DNS Names

All services resolve via `<service-name>.<namespace>.svc.cluster.local`. Within the same namespace, just `<service-name>` works:

| Service | In-cluster DNS |
|---------|----------------|
| MySQL | `mysql-service:3306` |
| Kafka | `kafka-service:9092` |
| Keycloak | `keycloak:8080` |
| API Gateway | `apigateway:2027` |
| User Service | `user-service:2026` |
| Product Service | `product-service:2028` |
| Order Service | `order-service:2029` |
| Prometheus | `prometheus:9090` |
| Grafana | `grafana:3000` |
| Tempo | `tempo:3200` |
| Jaeger | `jaeger:16686` |
| OTel Collector | `otel-collector:4318` |

### Updating Secrets

To update a secret value:

```bash
# Re-encode new value
echo -n "my-new-secret-value" | base64

# Edit the secret YAML, paste new base64 value, then apply
kubectl apply -n ecommerce -f templates/applications/api-gateway/secret.yaml

# Restart affected pods to pick up new value
kubectl rollout restart deployment/apigateway -n ecommerce
```

---

## 15. Troubleshooting

### Pod won't start

```bash
# Check pod status and events
kubectl describe pod <pod-name> -n ecommerce

# Check logs
kubectl logs <pod-name> -n ecommerce
kubectl logs <pod-name> -n ecommerce --previous   # logs from crashed container
```

**Common causes:**
- `ImagePullBackOff` — Docker image not found; ensure you built it with the correct tag
- `CrashLoopBackOff` — Application is crashing on startup; check logs for stack trace
- `Pending` — No node has enough resources; check `kubectl describe node`

---

### Service can't connect to MySQL

```bash
# Test connectivity from inside a pod
kubectl exec -it <pod-name> -n ecommerce -- sh
# Inside the pod:
nc -zv mysql-service 3306
```

**Check MySQL pod:**
```bash
kubectl logs deployment/mysql -n ecommerce
kubectl exec deployment/mysql -n ecommerce -- mysqladmin ping -h localhost
```

---

### Keycloak JWT validation failing

```bash
# Verify Keycloak is reachable from a service pod
kubectl exec -it deployment/user-service -n ecommerce -- sh
curl http://keycloak:8080/realms/microservices-realm/.well-known/openid-configuration
```

The response should be a JSON object. If it fails, Keycloak is not ready or the realm doesn't exist yet.

---

### Ingress returning 404 or 502

```bash
# Check NGINX Ingress Controller is running
kubectl get pods -n ingress-nginx

# Check Ingress resource
kubectl describe ingress ecommerce-ingress -n ecommerce

# Check NGINX controller logs
kubectl logs deployment/ingress-nginx-controller -n ingress-nginx
```

**502 Bad Gateway** means the backend pod (API Gateway) is not responding. Check the gateway pod logs.

---

### HPA not scaling

```bash
# Check HPA status
kubectl get hpa -n ecommerce
kubectl describe hpa user-service-hpa -n ecommerce
```

If `TARGETS` shows `<unknown>/80%`, Metrics Server is not installed:
```bash
kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml
```

---

### Traces not appearing in Jaeger / Tempo

```bash
# Check OTel Collector logs
kubectl logs deployment/otel-collector -n ecommerce

# Verify a service is sending traces
kubectl exec -it deployment/user-service -n ecommerce -- sh
curl -v http://otel-collector:4318/v1/traces
```

**If Spring Boot isn't sending traces:** Ensure `management.tracing.sampling.probability=1.0` is set in the service's ConfigMap and `MANAGEMENT_OTLP_TRACING_ENDPOINT=http://otel-collector:4318/v1/traces` is correct.

---

### Check resource usage

```bash
# Node resource usage
kubectl top nodes

# Pod resource usage
kubectl top pods -n ecommerce

# Watch HPA decisions in real time
kubectl get hpa -n ecommerce -w
```

---

### Full reset (delete everything including data)

```bash
# WARNING: This deletes all persistent data (MySQL, Kafka, Grafana, Prometheus)
kubectl delete namespace ecommerce

# Recreate from scratch
bash scripts/deploy-all.sh
```

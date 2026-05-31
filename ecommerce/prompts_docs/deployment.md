Prompt

I have developed an e-commerce microservices application using Spring Boot and Java.

Existing Services
Infrastructure Components (Already Running in Kubernetes)

These are already deployed in Rancher Desktop Kubernetes under namespace ecommerce:

Keycloak (Authentication & Authorization)
MySQL Database
Apache Kafka
Kafka UI (if required)

Namespace:

ecommerce
Microservices to Deploy
API Gateway Service
User Service
Product Service
Order Service

Technology Stack
Java 21
Spring Boot 3.x
Spring Cloud Gateway
Spring Security OAuth2
Keycloak
Spring Data JPA
MySQL
Kafka
Docker
Kubernetes (Rancher Desktop)
Actuator
OpenAPI/Swagger
Requirements

Generate a complete Kubernetes deployment solution for local Rancher Desktop.

1. Docker

Generate:

Dockerfile for API Gateway
Dockerfile for User Service
Dockerfile for Product Service
Dockerfile for Order Service

Use:

eclipse-temurin:17-jre
Follow multi-stage build best practices.

2. Kubernetes Manifests

Generate separate YAML files for:

API Gateway
Deployment
Service
ConfigMap
Secret (if required)
User Service
Deployment
Service
ConfigMap
Secret
Product Service
Deployment
Service
ConfigMap
Secret
Order Service
Deployment
Service
ConfigMap
Secret

3. Namespace

Generate:
namespace.yaml
Namespace name:ecommerce

4. Configuration Management

Move all environment-specific properties from application.yml into:

ConfigMaps
Secrets

Examples:

SPRING_DATASOURCE_URL
SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_PASSWORD

SPRING_KAFKA_BOOTSTRAP_SERVERS

SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI

KEYCLOAK_URL
KEYCLOAK_REALM
KEYCLOAK_CLIENT_ID

5. Internal Service Communication

Configure Kubernetes DNS-based communication:

API Gateway → User Service

http://user-service:8081

API Gateway → Product Service

http://product-service:8082

API Gateway → Order Service

http://order-service:8083

6. Resource Limits
Add Kubernetes resource requests and limits.

Example:

resources:
  requests:
    memory: "256Mi"
    cpu: "250m"
  limits:
    memory: "512Mi"
    cpu: "500m"
	
7. Health Checks

Configure:

Liveness Probe
Readiness Probe

Using Spring Boot Actuator:

/actuator/health

8. Scaling

Add Horizontal Pod Autoscaler (HPA) for:

User Service
Product Service
Order Service

Example:

minReplicas: 1
maxReplicas: 5

CPU utilization:

80%

9. Ingress

Generate Ingress configuration.

Host:

ecommerce.local

Routes:

/api/users/**
/api/products/**
/api/orders/**

Forward all traffic through API Gateway.

10. Observability

Prepare Kubernetes configuration for:

Spring Boot Actuator
Prometheus scraping
Grafana dashboards
OpenTelemetry
Jaeger or Tempo tracing

Add annotations:

prometheus.io/scrape: "true"
prometheus.io/port: "8080"

11. Deployment Strategy

Use Rolling Update strategy:

strategy:
  type: RollingUpdate

Configure:

maxSurge: 1
maxUnavailable: 0
12. Folder Structure

Generate production-ready folder structure:

Using helm but values.yaml, Chart.yml keep empty later i can implement

ecommerce-platform/
│
├── charts/
│
├── values.yaml
├── Chart.yaml
│
└── templates/
		│
		├── namespace/
		│   └── namespace.yaml
		│
		├── infrastructure/
		│   │
		│   ├── mysql/
		│   │   ├── pvc.yaml
		│   │   ├── secret.yaml
		│   │   ├── deployment.yaml
		│   │   └── service.yaml
		│   │
		│   ├── kafka/
		│   │   ├── pvc.yaml
		│   │   ├── configmap.yaml
		│   │   ├── deployment.yaml
		│   │   └── service.yaml
		│   │
		│   ├── ui/
		│   │   ├── ui-deployment.yaml
		│   │   └── ui-service.yaml
		│   │
		│   └── keycloak/
		│       ├── secret.yaml
		│       ├── configmap.yaml
		│       ├── deployment.yaml
		│       └── service.yaml
		│
		├── applications/
		│   │
		│   ├── api-gateway/
		│   │   ├── deployment.yaml
		│   │   ├── service.yaml
		│   │   ├── configmap.yaml
		│   │   └── secret.yaml
		│   │
		│   ├── user-service/
		│   │   ├── deployment.yaml
		│   │   ├── service.yaml
		│   │   ├── configmap.yaml
		│   │   ├── secret.yaml
		│   │   └── hpa.yaml
		│   │
		│   ├── product-service/
		│   │   ├── deployment.yaml
		│   │   ├── service.yaml
		│   │   ├── configmap.yaml
		│   │   ├── secret.yaml
		│   │   └── hpa.yaml
		│   │
		│   └── order-service/
		│       ├── deployment.yaml
		│       ├── service.yaml
		│       ├── configmap.yaml
		│       ├── secret.yaml
		│       └── hpa.yaml
		│
		├── ingress/
		│   └── ingress.yaml
		│
		├── observability/
		│   │
		│   ├── prometheus/
		│   │   ├── configmap.yaml
		│   │   ├── deployment.yaml
		│   │   └── service.yaml
		│   │
		│   ├── grafana/
		│   │   ├── pvc.yaml
		│   │   ├── deployment.yaml
		│   │   └── service.yaml
		│   │
		│   ├── tempo/
		│   │   ├── configmap.yaml
		│   │   ├── deployment.yaml
		│   │   └── service.yaml
		│   │
		│   ├── jaeger/
		│   │   ├── deployment.yaml
		│   │   └── service.yaml
		│   │
		│   └── collector/
		│       ├── configmap.yaml
		│       ├── deployment.yaml
		│       └── service.yaml
		│
		│
		└── scripts/
			├── deploy-all.sh
			├── undeploy-all.sh
			└── restart-services.sh

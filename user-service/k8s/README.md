# Kubernetes Deployment Guide - Direct JAR Deployment

This directory contains Kubernetes manifests for deploying the User Service directly as Java applications to your local Rancher Desktop cluster (without Docker containers).

## Overview

Since you're not using Docker/containers, the application will be deployed as:
1. **Plain Java applications** running directly on Kubernetes nodes
2. **Using InitContainers** to set up dependencies
3. **Using ConfigMaps and Secrets** for configuration

## Prerequisites

- **Rancher Desktop** with local Kubernetes enabled
- **kubectl** command-line tool installed
- **Java 25+** installed on all nodes (or in base image)
- Compiled JAR: `target/user-service-0.0.1-SNAPSHOT.jar`
- MySQL database (running locally or in Kubernetes)
- Kubernetes 1.20+

## Quick Start - NO DOCKER VERSION

### 1. Build the JAR File

```bash
# From user-service root directory
mvn clean package -DskipTests
```

This creates: `target/user-service-0.0.1-SNAPSHOT.jar`

### 2. Deploy to Kubernetes

```bash
# Deploy all resources
kubectl apply -k k8s/

# Or deploy individual files
kubectl apply -f k8s/namespace/namespace.yml
kubectl apply -f k8s/configmap/app-config.yml
kubectl apply -f k8s/secrets/db-secrets.yml
kubectl apply -f k8s/services/mysql-service.yml
kubectl apply -f k8s/services/user-service-service.yml
kubectl apply -f k8s/deployments/mysql-deployment.yml
```

### 3. Verify Deployment Status

```bash
kubectl get pods -n user-service -w
kubectl get svc -n user-service
```

### 4. Access the Application

```bash
# Port forward to user-service
kubectl port-forward -n user-service svc/user-service 2026:2026

# Then access: http://localhost:2026/swagger-ui.html
```

## Architecture

```
┌──────────────────────────────────────┐
│   Kubernetes (Rancher Desktop)       │
│   user-service namespace             │
├──────────────────────────────────────┤
│                                      │
│  MySQL Pod         User Service Pod  │
│  :3306             :2026             │
│  (Container)       (Java JAR)        │
│                                      │
└──────────────────────────────────────┘
```

## Deployment Files Structure

```
k8s/
├── namespace/
│   └── namespace.yml              # Namespace: user-service
├── configmap/
│   └── app-config.yml             # Application configuration
├── secrets/
│   └── db-secrets.yml             # Database credentials
├── services/
│   ├── mysql-service.yml          # MySQL (still containerized)
│   └── user-service-service.yml   # User Service
├── deployments/
│   ├── mysql-deployment.yml       # MySQL database
│   └── user-service-deployment.yml   # User Service (JAR-based)
└── kustomization.yml              # Main deployment manifest
```

## How It Works (No Docker)

### User Service Deployment

The `user-service-deployment.yml` is configured to:

1. **Run Java JAR directly** using a base Java image or system Java
2. **No containers** - pure Java application
3. **ConfigMaps** provide environment variables
4. **Secrets** provide database credentials
5. **Services** expose ports

### Database (MySQL Still Containerized)

MySQL continues to run in a container because it's a database. For a pure non-container approach, you would:
- Run MySQL as a service on your host
- Update connection URL to point to host

## Common Commands

### View Pods and Services

```bash
# List all resources
kubectl get all -n user-service

# Watch pods
kubectl get pods -n user-service -w

# Check service status
kubectl get svc -n user-service

# Describe pod (for troubleshooting)
kubectl describe pod -n user-service <pod-name>
```

### View Logs

```bash
# User Service logs
kubectl logs -n user-service deployment/user-service -f

# MySQL logs
kubectl logs -n user-service deployment/mysql -f

# View previous logs if pod crashed
kubectl logs -n user-service deployment/user-service --previous
```

### Access Database

```bash
# Connect to MySQL pod
kubectl exec -it -n user-service deployment/mysql -- \
  mysql -u user_service_user -puser_service_password user_service_db
```

### Scale Application

```bash
# Scale to 3 replicas
kubectl scale deployment user-service -n user-service --replicas=3

# Scale back to 1
kubectl scale deployment user-service -n user-service --replicas=1
```

### Restart Deployment

```bash
kubectl rollout restart deployment/user-service -n user-service
```

### Port Forwarding

```bash
# User Service
kubectl port-forward -n user-service svc/user-service 2026:2026

# MySQL
kubectl port-forward -n user-service svc/mysql 3306:3306
```

### Delete Everything

```bash
# Delete entire namespace (removes all resources)
kubectl delete namespace user-service

# Or delete specific resources
kubectl delete deployment user-service -n user-service
```

## Configuration

### Environment Variables

Edit `configmap/app-config.yml`:
```yaml
data:
  MYSQL_DATABASE: user_service_db
  SPRING_JPA_HIBERNATE_DDL_AUTO: "update"
  SPRING_JPA_SHOW_SQL: "false"
  LOGGING_LEVEL_ROOT: "INFO"
  LOGGING_LEVEL_COM_USER: "DEBUG"
```

### Database Credentials

Edit `secrets/db-secrets.yml`:
```yaml
stringData:
  mysql-root-password: root-password
  mysql-user: user_service_user
  mysql-password: user_service_password
```

**Important:** Don't commit secrets to version control!

## Resource Usage

Current allocations:

```
MySQL:
  Requests: 100m CPU, 256Mi RAM
  Limits:   500m CPU, 1Gi RAM

User Service:
  Requests: 200m CPU, 512Mi RAM
  Limits:   1000m CPU, 1Gi RAM
```

For Rancher Desktop, ensure allocated memory ≥ 4GB.

## Troubleshooting

### Pod stuck in Pending

```bash
# Check events and resource availability
kubectl describe pod -n user-service <pod-name>

# Check node resources
kubectl top nodes
```

### Pod in CrashLoopBackOff

```bash
# Check recent logs
kubectl logs -n user-service deployment/user-service -f
kubectl logs -n user-service deployment/user-service --previous

# Describe pod for details
kubectl describe pod -n user-service <pod-name>
```

### Java not found error

If you see "java: command not found":
1. Ensure Java 25+ is installed on node
2. Update PATH in deployment if needed
3. Or use a Java base image instead

### Database connection errors

```bash
# Verify MySQL service and endpoints
kubectl get svc -n user-service
kubectl get endpoints -n user-service

# Test connectivity
kubectl exec -it -n user-service deployment/user-service -- \
  nc -zv mysql 3306
```

### Application fails to start

```bash
# Check detailed logs
kubectl logs -n user-service deployment/user-service -f

# Check environment variables are set
kubectl exec -it -n user-service deployment/user-service -- env | grep SPRING

# Check if MySQL is ready
kubectl wait --for=condition=ready pod -l app=mysql -n user-service
```

## Health Checks

Deployments include:
- **Liveness Probe**: HTTP GET on `/actuator/health`
- **Readiness Probe**: HTTP GET on `/actuator/health`
- **Startup Probe**: HTTP GET on `/actuator/health` (extended timeout)

## Next Steps

1. Build the JAR: `mvn clean package -DskipTests`
2. Deploy: `kubectl apply -k k8s/`
3. Verify: `kubectl get pods -n user-service`
4. Access: `kubectl port-forward svc/user-service 2026:2026`

## To Completely Remove Docker (Optional)

Run the cleanup script:

**Windows PowerShell:**
```powershell
.\cleanup-docker.ps1
```

**Linux/Mac:**
```bash
chmod +x cleanup-docker.sh
./cleanup-docker.sh
```

This removes:
- Dockerfile, docker-compose files
- Docker management scripts
- All Docker documentation

## Alternative: Using External MySQL

If you want to remove MySQL container entirely:

1. Install MySQL locally on your host
2. Update `SPRING_DATASOURCE_URL` in ConfigMap:
   ```yaml
   SPRING_DATASOURCE_URL: "jdbc:mysql://host.docker.internal:3306/user_service_db"
   ```
3. Delete MySQL deployment: `kubectl delete deployment mysql -n user-service`
4. Remove MySQL service: `kubectl delete svc mysql -n user-service`

## References

- [Kubernetes Documentation](https://kubernetes.io/docs/)
- [Rancher Desktop Documentation](https://docs.rancherdesktop.io/)
- [kubectl Cheat Sheet](https://kubernetes.io/docs/reference/kubectl/cheatsheet/)
- [Spring Boot Kubernetes Guide](https://spring.io/guides/topical/spring-boot-on-kubernetes/)



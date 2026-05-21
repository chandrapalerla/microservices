# Kubernetes Deployment Guide - MySQL Only

This directory contains Kubernetes manifests for deploying the User Service with MySQL database to Rancher Desktop (or any local Kubernetes cluster).

## Simplified Setup (MySQL Only)

**Files to use:**
- ✅ `namespace/namespace.yml` - Namespace
- ✅ `configmap/app-config.yml` - Application config
- ✅ `secrets/db-secrets.yml` - MySQL credentials
- ✅ `pv-pvc/mysql-pvc.yml` - MySQL storage
- ✅ `services/mysql-service.yml` - MySQL service
- ✅ `services/user-service-service.yml` - User Service
- ✅ `deployments/mysql-deployment.yml` - MySQL deployment
- ✅ `deployments/user-service-deployment.yml` - User Service deployment

**Files to ignore/delete (MongoDB & Hazelcast):**
- ❌ `deployments/mongodb-deployment.yml`
- ❌ `deployments/hazelcast-deployment.yml`
- ❌ `services/mongodb-service.yml`
- ❌ `services/hazelcast-service.yml`
- ❌ `pv-pvc/mongodb-pvc.yml`

## Prerequisites

- **Rancher Desktop** with local Kubernetes enabled
- **kubectl** command-line tool installed
- Docker image: `user-service:1.0.0` built locally
- Kubernetes 1.20+

## Quick Start

### 1. Build Docker Image

```bash
cd C:\git-hub\microservices\user-service
docker build -t user-service:1.0.0 .
```

### 2. Deploy to Kubernetes

```bash
# Deploy using kustomization (includes only MySQL)
kubectl apply -k k8s/

# Or deploy individual files manually
kubectl apply -f k8s/namespace/namespace.yml
kubectl apply -f k8s/secrets/db-secrets.yml
kubectl apply -f k8s/configmap/app-config.yml
kubectl apply -f k8s/pv-pvc/mysql-pvc.yml
kubectl apply -f k8s/services/mysql-service.yml
kubectl apply -f k8s/services/user-service-service.yml
kubectl apply -f k8s/deployments/mysql-deployment.yml
kubectl apply -f k8s/deployments/user-service-deployment.yml
```

### 3. Verify Deployment

```bash
# Check namespace
kubectl get ns | grep user-service

# Check all resources
kubectl get all -n user-service

# Check pods
kubectl get pods -n user-service -w
```

Expected pods:
- `mysql-xxxxx` - MySQL database
- `user-service-xxxxx` - Spring Boot application

### 4. Access the API

```bash
# Port forward user-service to localhost
kubectl port-forward -n user-service svc/user-service 2026:2026
```

Then access:
- **Swagger UI**: http://localhost:2026/swagger-ui.html
- **API Docs**: http://localhost:2026/v3/api-docs
- **Health Check**: http://localhost:2026/actuator/health

## Services Architecture

```
┌─────────────────────────────────────┐
│   Kubernetes Network (user-service)  │
│                                      │
│  MySQL ←→ User Service              │
│  :3306     :2026                    │
└─────────────────────────────────────┘
     ↑
   Host
```

## Common Commands

### Monitoring

```bash
# View pods status
kubectl get pods -n user-service

# Watch pods in real-time
kubectl get pods -n user-service -w

# Check service status
kubectl get svc -n user-service

# Check persistent volumes
kubectl get pvc -n user-service
```

### Logs

```bash
# User Service logs
kubectl logs -n user-service deployment/user-service -f

# MySQL logs
kubectl logs -n user-service deployment/mysql -f

# Previous pod logs (if pod crashed)
kubectl logs -n user-service deployment/user-service --previous
```

### Access Database

```bash
# Connect to MySQL
kubectl exec -it -n user-service deployment/mysql -- \
  mysql -u user_service_user -puser_service_password user_service_db

# OR with root
kubectl exec -it -n user-service deployment/mysql -- \
  mysql -u root -proot-password
```

### Restart Services

```bash
# Restart MySQL
kubectl rollout restart deployment/mysql -n user-service

# Restart User Service
kubectl rollout restart deployment/user-service -n user-service

# Check rollout status
kubectl rollout status deployment/user-service -n user-service -w
```

### Scale Application

```bash
# Scale user-service to 3 replicas
kubectl scale deployment user-service -n user-service --replicas=3

# Scale back to 1
kubectl scale deployment user-service -n user-service --replicas=1
```

### Port Forwarding

```bash
# User Service
kubectl port-forward -n user-service svc/user-service 2026:2026

# MySQL (for local database clients)
kubectl port-forward -n user-service svc/mysql 3306:3306
```

### Delete Resources

```bash
# Delete all resources in namespace
kubectl delete namespace user-service

# Or delete specific resources
kubectl delete -f k8s/deployments/user-service-deployment.yml
kubectl delete -f k8s/deployments/mysql-deployment.yml
```

## Configuration

### Environment Variables

Edit `k8s/configmap/app-config.yml` to change:
- `MYSQL_DATABASE` - Database name
- `SPRING_JPA_HIBERNATE_DDL_AUTO` - Hibernate DDL mode (update/create/create-drop)
- `SPRING_JPA_SHOW_SQL` - Enable SQL logging (true/false)
- `LOGGING_LEVEL_ROOT` - Root logging level
- `LOGGING_LEVEL_COM_USER` - Application logging level

### Database Credentials

Edit `k8s/secrets/db-secrets.yml` to change:
- `mysql-root-password` - MySQL root password
- `mysql-user` - Application user
- `mysql-password` - Application user password

**Important:** Don't commit secrets to version control!

### Database Storage

Edit `k8s/pv-pvc/mysql-pvc.yml` to change:
- `storage: 10Gi` - Persistent storage size

## Troubleshooting

### Pod stuck in Pending

```bash
# Check events
kubectl describe pod -n user-service <pod-name>

# Check node resources
kubectl top nodes
kubectl top pods -n user-service
```

### Pod in CrashLoopBackOff

```bash
# Check logs
kubectl logs -n user-service deployment/user-service
kubectl logs -n user-service deployment/user-service --previous

# Describe pod for detailed info
kubectl describe pod -n user-service <pod-name>
```

### Database connection errors

```bash
# Verify MySQL service exists
kubectl get svc -n user-service mysql

# Check MySQL pod logs
kubectl logs -n user-service deployment/mysql

# Test connectivity from user-service pod
kubectl exec -it -n user-service deployment/user-service -- \
  nc -zv mysql 3306
```

### Application not ready

```bash
# Check startup probe
kubectl describe pod -n user-service <pod-name>

# View recent events
kubectl get events -n user-service --sort-by='.lastTimestamp' | tail -20
```

## Resource Usage

Current resource requests/limits:

```
MySQL:
  Requests: 100m CPU, 256Mi RAM
  Limits:   500m CPU, 1Gi RAM

User Service:
  Requests: 200m CPU, 512Mi RAM
  Limits:   1000m CPU, 1Gi RAM
```

For Rancher Desktop, ensure allocated memory ≥ 4GB.

## Health Checks

- **Liveness Probe**: Detects if pod needs restart
- **Readiness Probe**: Checks if pod can accept traffic
- **Startup Probe**: Allows time for application startup

## File Organization

```
k8s/
├── kustomization.yml               # Main deployment config
├── README.md                       # This file
├── namespace/
│   └── namespace.yml               # Namespace definition
├── configmap/
│   └── app-config.yml              # App configuration
├── secrets/
│   └── db-secrets.yml              # Database credentials
├── pv-pvc/
│   └── mysql-pvc.yml               # MySQL storage
│   └── mongodb-pvc.yml             # [UNUSED] Delete if not needed
├── services/
│   ├── mysql-service.yml           # MySQL service
│   ├── user-service-service.yml    # User Service
│   ├── mongodb-service.yml         # [UNUSED]
│   └── hazelcast-service.yml       # [UNUSED]
└── deployments/
    ├── mysql-deployment.yml        # MySQL deployment
    ├── user-service-deployment.yml # User Service
    ├── mongodb-deployment.yml      # [UNUSED]
    └── hazelcast-deployment.yml    # [UNUSED]
```

## Next Steps

1. Build the Docker image: `docker build -t user-service:1.0.0 .`
2. Deploy: `kubectl apply -k k8s/`
3. Verify: `kubectl get pods -n user-service`
4. Access API: `kubectl port-forward svc/user-service 2026:2026`

## References

- [Kubernetes Documentation](https://kubernetes.io/docs/)
- [Rancher Desktop](https://docs.rancherdesktop.io/)
- [kubectl Cheat Sheet](https://kubernetes.io/docs/reference/kubectl/cheatsheet/)


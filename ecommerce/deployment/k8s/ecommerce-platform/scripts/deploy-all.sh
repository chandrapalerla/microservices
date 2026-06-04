#!/usr/bin/env bash
# deploy-all.sh — Deploy the full ecommerce platform to Rancher Desktop Kubernetes.
# Run from the ecommerce-platform/ directory: bash scripts/deploy-all.sh
set -euo pipefail

NAMESPACE=ecommerce
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$SCRIPT_DIR/.."
# ecommerce/ project root — 4 levels up from scripts/
ECOMMERCE_ROOT="$(cd "$SCRIPT_DIR/../../../.." && pwd)"

# ── Detect container CLI (nerdctl for Rancher Desktop, docker as fallback) ────
# nerdctl: must build into k8s.io namespace so Kubernetes can find the image.
# docker:  images are shared automatically with Kubernetes (no namespace needed).
if command -v nerdctl &> /dev/null; then
  CTR="nerdctl"
  CTR_BUILD="nerdctl --namespace k8s.io"
elif command -v docker &> /dev/null; then
  CTR="docker"
  CTR_BUILD="docker"
else
  echo "ERROR: Neither nerdctl nor docker found. Install Rancher Desktop or Docker Desktop."
  exit 1
fi
echo "==> Using container CLI: $CTR (build namespace: ${CTR_BUILD})"

# ── Build image if it does not exist locally ──────────────────────────────────
build_if_missing() {
  local image="$1"
  local context="$2"
  if $CTR_BUILD image inspect "$image" > /dev/null 2>&1; then
    echo "  [SKIP]  $image already exists"
  else
    echo "  [BUILD] $image not found — building from $context"
    $CTR_BUILD build -t "$image" "$context"
  fi
}

echo "==> Checking Docker images (building any that are missing)"
build_if_missing "ecommerce/apigateway:latest"      "$ECOMMERCE_ROOT/services/apigateway"
build_if_missing "ecommerce/user-service:latest"    "$ECOMMERCE_ROOT/services/user-service"
build_if_missing "ecommerce/product-service:latest" "$ECOMMERCE_ROOT/services/product-service"
build_if_missing "ecommerce/order-service:latest"   "$ECOMMERCE_ROOT/services/order-service"
build_if_missing "ecommerce/payment-service:latest" "$ECOMMERCE_ROOT/services/payment-service"
build_if_missing "ecommerce/client:latest"          "$ECOMMERCE_ROOT/client"

# ── Install NGINX Ingress Controller if not already installed ─────────────────
NGINX_VERSION="v1.10.1"
NGINX_MANIFEST="https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-${NGINX_VERSION}/deploy/static/provider/cloud/deploy.yaml"

echo "==> Checking NGINX Ingress Controller"
if kubectl get deployment ingress-nginx-controller -n ingress-nginx > /dev/null 2>&1; then
  echo "  [SKIP]  NGINX Ingress Controller already installed"
else
  echo "  [INSTALL] NGINX Ingress Controller not found — installing ${NGINX_VERSION}"
  kubectl apply -f "$NGINX_MANIFEST"
  echo "  Waiting for NGINX Ingress Controller to be ready..."
  kubectl wait --namespace ingress-nginx \
    --for=condition=ready pod \
    --selector=app.kubernetes.io/component=controller \
    --timeout=180s
  echo "  [DONE] NGINX Ingress Controller is ready"
fi

echo "==> Creating namespace $NAMESPACE"
kubectl apply -f "$ROOT/templates/namespace/namespace.yaml"

echo "==> Deploying infrastructure: MySQL"
kubectl apply -n "$NAMESPACE" -f "$ROOT/templates/infrastructure/mysql/"

echo "==> Deploying infrastructure: Kafka"
kubectl apply -n "$NAMESPACE" -f "$ROOT/templates/infrastructure/kafka/"

echo "==> Deploying infrastructure: Kafka UI"
kubectl apply -n "$NAMESPACE" -f "$ROOT/templates/infrastructure/ui/"

echo "==> Deploying infrastructure: Keycloak"
kubectl apply -n "$NAMESPACE" -f "$ROOT/templates/infrastructure/keycloak/"

echo "==> Waiting for Keycloak to be ready..."
kubectl rollout status deployment/keycloak -n "$NAMESPACE" --timeout=120s

echo "==> Deploying secrets (gateway + Keycloak clients)"
kubectl apply -n "$NAMESPACE" -f "$ROOT/templates/applications/api-gateway/secret.yaml"
kubectl apply -n "$NAMESPACE" -f "$ROOT/templates/applications/user-service/secret.yaml"
kubectl apply -n "$NAMESPACE" -f "$ROOT/templates/applications/product-service/secret.yaml"
kubectl apply -n "$NAMESPACE" -f "$ROOT/templates/applications/order-service/secret.yaml"
kubectl apply -n "$NAMESPACE" -f "$ROOT/templates/applications/payment-service/secret.yaml"

echo "==> Deploying application: API Gateway"
kubectl apply -n "$NAMESPACE" -f "$ROOT/templates/applications/api-gateway/configmap.yaml"
kubectl apply -n "$NAMESPACE" -f "$ROOT/templates/applications/api-gateway/deployment.yaml"
kubectl apply -n "$NAMESPACE" -f "$ROOT/templates/applications/api-gateway/service.yaml"

echo "==> Deploying application: User Service"
kubectl apply -n "$NAMESPACE" -f "$ROOT/templates/applications/user-service/configmap.yaml"
kubectl apply -n "$NAMESPACE" -f "$ROOT/templates/applications/user-service/deployment.yaml"
kubectl apply -n "$NAMESPACE" -f "$ROOT/templates/applications/user-service/service.yaml"
kubectl apply -n "$NAMESPACE" -f "$ROOT/templates/applications/user-service/hpa.yaml"

echo "==> Deploying application: Product Service"
kubectl apply -n "$NAMESPACE" -f "$ROOT/templates/applications/product-service/configmap.yaml"
kubectl apply -n "$NAMESPACE" -f "$ROOT/templates/applications/product-service/deployment.yaml"
kubectl apply -n "$NAMESPACE" -f "$ROOT/templates/applications/product-service/service.yaml"
kubectl apply -n "$NAMESPACE" -f "$ROOT/templates/applications/product-service/hpa.yaml"

echo "==> Deploying application: Order Service"
kubectl apply -n "$NAMESPACE" -f "$ROOT/templates/applications/order-service/configmap.yaml"
kubectl apply -n "$NAMESPACE" -f "$ROOT/templates/applications/order-service/deployment.yaml"
kubectl apply -n "$NAMESPACE" -f "$ROOT/templates/applications/order-service/service.yaml"
kubectl apply -n "$NAMESPACE" -f "$ROOT/templates/applications/order-service/hpa.yaml"

echo "==> Deploying application: Payment Service"
kubectl apply -n "$NAMESPACE" -f "$ROOT/templates/applications/payment-service/secret.yaml"
kubectl apply -n "$NAMESPACE" -f "$ROOT/templates/applications/payment-service/configmap.yaml"
kubectl apply -n "$NAMESPACE" -f "$ROOT/templates/applications/payment-service/deployment.yaml"
kubectl apply -n "$NAMESPACE" -f "$ROOT/templates/applications/payment-service/service.yaml"
kubectl apply -n "$NAMESPACE" -f "$ROOT/templates/applications/payment-service/hpa.yaml"

echo "==> Deploying application: Client (React)"
kubectl apply -n "$NAMESPACE" -f "$ROOT/templates/applications/client/deployment.yaml"
kubectl apply -n "$NAMESPACE" -f "$ROOT/templates/applications/client/service.yaml"

echo "==> Deploying Ingress"
kubectl apply -n "$NAMESPACE" -f "$ROOT/templates/ingress/ingress.yaml"

echo "==> Deploying observability stack"
kubectl apply -n "$NAMESPACE" -f "$ROOT/templates/observability/prometheus/"
kubectl apply -n "$NAMESPACE" -f "$ROOT/templates/observability/loki/"
kubectl apply -n "$NAMESPACE" -f "$ROOT/templates/observability/grafana/"
kubectl apply -n "$NAMESPACE" -f "$ROOT/templates/observability/tempo/"
kubectl apply -n "$NAMESPACE" -f "$ROOT/templates/observability/jaeger/"
kubectl apply -n "$NAMESPACE" -f "$ROOT/templates/observability/collector/"
kubectl apply -n "$NAMESPACE" -f "$ROOT/templates/observability/promtail/"

echo ""
echo "==> Deployment complete!"
echo ""
echo "Access URLs (Rancher Desktop):"
echo "  Client App   : http://localhost:30500"
echo "  API Gateway  : http://localhost:30027"
echo "  Keycloak     : http://localhost:30080"
echo "  Prometheus   : http://localhost:30900"
echo "  Grafana      : http://localhost:30300  (admin/admin)"
echo "  Jaeger UI    : http://localhost:30686"
echo "  Tempo        : http://localhost:30320"
echo "  Loki         : http://localhost:30100"
echo "  Kafka UI     : http://localhost:30808"
echo "  MySQL        : localhost:30036"
echo "  Kafka        : localhost:30092"
echo ""
echo "  Ingress (ecommerce.local): add '127.0.0.1 ecommerce.local' to /etc/hosts"
echo ""
kubectl get pods -n "$NAMESPACE"

#!/usr/bin/env bash
# restart-services.sh — Build image and rolling restart of microservices.
# Run from the ecommerce-platform/ directory:
#   bash scripts/restart-services.sh                 # build + restart all services
#   bash scripts/restart-services.sh user-service    # build + restart only user-service
set -euo pipefail

NAMESPACE=ecommerce
TARGET="${1:-all}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
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
  echo "ERROR: Neither nerdctl nor docker found."
  exit 1
fi

# ── Map service name → image name and source folder ──────────────────────────
image_for() {
  case "$1" in
    apigateway)      echo "ecommerce/apigateway:latest" ;;
    user-service)    echo "ecommerce/user-service:latest" ;;
    product-service) echo "ecommerce/product-service:latest" ;;
    order-service)    echo "ecommerce/order-service:latest" ;;
    payment-service)  echo "ecommerce/payment-service:latest" ;;
    client)           echo "ecommerce/client:latest" ;;
    *) echo "ERROR: Unknown service '$1'. Valid: apigateway, user-service, product-service, order-service, payment-service, client" >&2; exit 1 ;;
  esac
}

context_for() {
  case "$1" in
    client) echo "$ECOMMERCE_ROOT/client" ;;
    *)      echo "$ECOMMERCE_ROOT/services/$1" ;;
  esac
}

# ── Build image then restart deployment ───────────────────────────────────────
build_and_restart() {
  local svc="$1"
  local image; image="$(image_for "$svc")"
  local context; context="$(context_for "$svc")"

  echo ""
  echo "==> [$svc] Building image: $image"
  $CTR_BUILD build -t "$image" "$context"

  echo "==> [$svc] Restarting deployment"
  kubectl rollout restart deployment/"$svc" -n "$NAMESPACE"
  kubectl rollout status deployment/"$svc" -n "$NAMESPACE" --timeout=120s

  echo "==> [$svc] Done"
}

# ── Run for all or single service ─────────────────────────────────────────────
if [[ "$TARGET" == "all" ]]; then
  build_and_restart apigateway
  build_and_restart user-service
  build_and_restart product-service
  build_and_restart order-service
  build_and_restart payment-service
  build_and_restart client
else
  build_and_restart "$TARGET"
fi

echo ""
echo "==> All done."
kubectl get pods -n "$NAMESPACE" -l "app in (apigateway,user-service,product-service,order-service,payment-service,client)"

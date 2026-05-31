#!/usr/bin/env bash
# undeploy-all.sh — Tear down the full ecommerce platform.
# Run from the ecommerce-platform/ directory: bash scripts/undeploy-all.sh
set -euo pipefail

NAMESPACE=ecommerce
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$SCRIPT_DIR/.."

echo "==> Removing observability stack"
kubectl delete -n "$NAMESPACE" -f "$ROOT/templates/observability/collector/" --ignore-not-found
kubectl delete -n "$NAMESPACE" -f "$ROOT/templates/observability/jaeger/" --ignore-not-found
kubectl delete -n "$NAMESPACE" -f "$ROOT/templates/observability/tempo/" --ignore-not-found
kubectl delete -n "$NAMESPACE" -f "$ROOT/templates/observability/grafana/" --ignore-not-found
kubectl delete -n "$NAMESPACE" -f "$ROOT/templates/observability/prometheus/" --ignore-not-found

echo "==> Removing Ingress"
kubectl delete -n "$NAMESPACE" -f "$ROOT/templates/ingress/ingress.yaml" --ignore-not-found

echo "==> Removing application services"
kubectl delete -n "$NAMESPACE" -f "$ROOT/templates/applications/order-service/" --ignore-not-found
kubectl delete -n "$NAMESPACE" -f "$ROOT/templates/applications/product-service/" --ignore-not-found
kubectl delete -n "$NAMESPACE" -f "$ROOT/templates/applications/user-service/" --ignore-not-found
kubectl delete -n "$NAMESPACE" -f "$ROOT/templates/applications/api-gateway/" --ignore-not-found

echo "==> Removing infrastructure"
kubectl delete -n "$NAMESPACE" -f "$ROOT/templates/infrastructure/keycloak/" --ignore-not-found
kubectl delete -n "$NAMESPACE" -f "$ROOT/templates/infrastructure/ui/" --ignore-not-found
kubectl delete -n "$NAMESPACE" -f "$ROOT/templates/infrastructure/kafka/" --ignore-not-found
kubectl delete -n "$NAMESPACE" -f "$ROOT/templates/infrastructure/mysql/" --ignore-not-found

echo ""
echo "==> All resources removed. Namespace '$NAMESPACE' preserved."
echo "    To delete the namespace: kubectl delete namespace $NAMESPACE"

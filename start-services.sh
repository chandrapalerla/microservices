#!/bin/bash

# Quick Start Script for Keycloak Microservices Setup
# This script helps you quickly start all the required services

set -e

echo "=========================================="
echo "Keycloak Microservices Setup"
echo "=========================================="
echo ""

# Color codes
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check if Docker is installed
if ! command -v docker &> /dev/null; then
    echo -e "${RED}Docker is not installed. Please install Docker first.${NC}"
    exit 1
fi

echo -e "${YELLOW}Step 1: Starting Keycloak Container${NC}"
echo "========================================"

# Check if Keycloak container already exists
if docker ps -a --format '{{.Names}}' | grep -q '^keycloak$'; then
    echo -e "${YELLOW}Keycloak container already exists. Starting it...${NC}"
    docker start keycloak || echo -e "${YELLOW}Container is already running${NC}"
else
    echo "Creating and starting Keycloak container..."
    docker run -d \
        --name keycloak \
        -e KEYCLOAK_ADMIN=admin \
        -e KEYCLOAK_ADMIN_PASSWORD=admin \
        -p 30080:8080 \
        quay.io/keycloak/keycloak:latest \
        start-dev
fi

# Wait for Keycloak to start
echo "Waiting for Keycloak to be ready..."
MAX_ATTEMPTS=30
ATTEMPT=0
until curl -s http://localhost:30080/health > /dev/null 2>&1 || [ $ATTEMPT -eq $MAX_ATTEMPTS ]; do
    ATTEMPT=$((ATTEMPT + 1))
    echo "Attempt $ATTEMPT/$MAX_ATTEMPTS..."
    sleep 1
done

if [ $ATTEMPT -eq $MAX_ATTEMPTS ]; then
    echo -e "${RED}Keycloak failed to start${NC}"
    exit 1
fi

echo -e "${GREEN}Keycloak is running!${NC}"
echo ""

echo -e "${YELLOW}Step 2: Starting MySQL Container${NC}"
echo "========================================"

# Check if MySQL container already exists
if docker ps -a --format '{{.Names}}' | grep -q '^mysql-userdb$'; then
    echo -e "${YELLOW}MySQL container already exists. Starting it...${NC}"
    docker start mysql-userdb || echo -e "${YELLOW}Container is already running${NC}"
else
    echo "Creating and starting MySQL container..."
    docker run -d \
        --name mysql-userdb \
        -e MYSQL_ROOT_PASSWORD=root123 \
        -e MYSQL_DATABASE=user_db \
        -p 30036:3306 \
        -v mysql_data:/var/lib/mysql \
        mysql:8.0
fi

# Wait for MySQL to start
echo "Waiting for MySQL to be ready..."
ATTEMPT=0
until docker exec mysql-userdb mysqladmin ping -h localhost -u root -proot123 > /dev/null 2>&1 || [ $ATTEMPT -eq $MAX_ATTEMPTS ]; do
    ATTEMPT=$((ATTEMPT + 1))
    echo "Attempt $ATTEMPT/$MAX_ATTEMPTS..."
    sleep 1
done

if [ $ATTEMPT -eq $MAX_ATTEMPTS ]; then
    echo -e "${RED}MySQL failed to start${NC}"
    exit 1
fi

echo -e "${GREEN}MySQL is running!${NC}"
echo ""

echo -e "${YELLOW}Step 3: Building Microservices${NC}"
echo "========================================"

# Check if we're in the microservices directory
if [ ! -d "serviceregistry" ] || [ ! -d "user-service" ] || [ ! -d "apigateway" ]; then
    echo -e "${RED}Error: Please run this script from the microservices root directory${NC}"
    exit 1
fi

echo "Building Service Registry..."
cd serviceregistry
mvn clean package -DskipTests > /dev/null 2>&1
cd ..

echo "Building User Service..."
cd user-service
mvn clean package -DskipTests > /dev/null 2>&1
cd ..

echo "Building API Gateway..."
cd apigateway
mvn clean package -DskipTests > /dev/null 2>&1
cd ..

echo -e "${GREEN}Build complete!${NC}"
echo ""

echo -e "${YELLOW}Step 4: Starting Microservices${NC}"
echo "========================================"

echo "Starting Service Registry (Eureka) on port 8761..."
cd serviceregistry
mvn spring-boot:run > /tmp/service-registry.log 2>&1 &
SERVICE_REGISTRY_PID=$!
cd ..

# Wait for Service Registry to start
sleep 5

echo "Starting User Service on port 2026..."
cd user-service
mvn spring-boot:run > /tmp/user-service.log 2>&1 &
USER_SERVICE_PID=$!
cd ..

# Wait for User Service to start
sleep 5

echo "Starting API Gateway on port 2027..."
cd apigateway
mvn spring-boot:run > /tmp/api-gateway.log 2>&1 &
GATEWAY_PID=$!
cd ..

echo ""
echo -e "${GREEN}=========================================="
echo "All Services Started Successfully!"
echo "==========================================${NC}"
echo ""
echo "Service URLs:"
echo "  - Keycloak Admin: ${YELLOW}http://localhost:30080/admin${NC}"
echo "  - Service Registry: ${YELLOW}http://localhost:8761${NC}"
echo "  - User Service: ${YELLOW}http://localhost:2026${NC}"
echo "  - API Gateway: ${YELLOW}http://localhost:2027${NC}"
echo ""
echo "Default Credentials:"
echo "  - Keycloak Admin Username: admin"
echo "  - Keycloak Admin Password: admin"
echo "  - MySQL Root Password: root123"
echo ""
echo "Next Steps:"
echo "1. Open Keycloak Admin Console: ${YELLOW}http://localhost:30080/admin${NC}"
echo "2. Login with admin/admin"
echo "3. Follow the KEYCLOAK_SETUP_GUIDE.md for configuration"
echo ""
echo "Logs:"
echo "  - Service Registry: tail -f /tmp/service-registry.log"
echo "  - User Service: tail -f /tmp/user-service.log"
echo "  - API Gateway: tail -f /tmp/api-gateway.log"
echo ""
echo "To stop all services, run:"
echo "  ${YELLOW}./stop-services.sh${NC}"
echo ""

# Cleanup function
cleanup() {
    echo ""
    echo -e "${YELLOW}Shutting down services...${NC}"
    kill $SERVICE_REGISTRY_PID 2>/dev/null || true
    kill $USER_SERVICE_PID 2>/dev/null || true
    kill $GATEWAY_PID 2>/dev/null || true
    echo -e "${GREEN}Services stopped${NC}"
}

# Trap SIGINT and SIGTERM
trap cleanup INT TERM

# Wait for processes
wait


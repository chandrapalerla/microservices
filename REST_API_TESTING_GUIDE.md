# REST API Testing Guide for Keycloak Integration

This guide provides practical examples for testing the Keycloak authentication and authorization implementation.

## API Endpoints

### Authentication Endpoints

#### 1. Get Access Token
**Endpoint**: `POST /realms/{realm}/protocol/openid-connect/token`
**Base URL**: `http://localhost:30080`
**Realm**: `microservices-realm`

```bash
# Using cURL
curl -X POST http://localhost:30080/realms/microservices-realm/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=admin-user" \
  -d "password=admin123" \
  -d "client_id=apigateway-client" \
  -d "grant_type=password"
```

**Response**:
```json
{
  "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "expires_in": 300,
  "refresh_expires_in": 1800,
  "refresh_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "token_type": "Bearer",
  "not-before-policy": 0,
  "session_state": "uuid",
  "scope": "openid profile email"
}
```

#### 2. Refresh Token
**Endpoint**: `POST /realms/{realm}/protocol/openid-connect/token`

```bash
curl -X POST http://localhost:30080/realms/microservices-realm/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=apigateway-client" \
  -d "client_secret=YOUR_CLIENT_SECRET" \
  -d "grant_type=refresh_token" \
  -d "refresh_token=YOUR_REFRESH_TOKEN"
```

#### 3. Logout
**Endpoint**: `POST /realms/{realm}/protocol/openid-connect/logout`

```bash
curl -X POST http://localhost:30080/realms/microservices-realm/protocol/openid-connect/logout \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=apigateway-client" \
  -d "client_secret=YOUR_CLIENT_SECRET" \
  -d "refresh_token=YOUR_REFRESH_TOKEN"
```

### Gateway Endpoints

#### 1. Gateway Health Check
```bash
curl -X GET http://localhost:2027/auth/health
```

**Response**:
```json
{
  "status": "UP",
  "gateway": "API Gateway is running"
}
```

#### 2. Gateway Logout
```bash
curl -X POST http://localhost:2027/auth/logout \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN"
```

**Response**:
```json
{
  "status": "success",
  "message": "User logged out successfully",
  "timestamp": 1234567890
}
```

#### 3. Gateway Refresh Token
```bash
curl -X POST http://localhost:2027/auth/refresh?refreshToken=YOUR_REFRESH_TOKEN
```

### User Service Endpoints

#### 1. Get Current Authenticated User
```bash
curl -X GET http://localhost:2026/api/v1/auth/me \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN"
```

**Response**:
```json
{
  "username": "admin-user",
  "authorities": [
    {
      "authority": "ROLE_ADMIN"
    },
    {
      "authority": "ROLE_USER"
    }
  ],
  "authenticated": true
}
```

#### 2. User Service Health Check
```bash
curl -X GET http://localhost:2026/api/v1/auth/health
```

**Response**:
```json
{
  "status": "UP",
  "service": "User Service",
  "timestamp": 1234567890
}
```

#### 3. User Service Logout
```bash
curl -X POST http://localhost:2026/api/v1/auth/logout \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN"
```

#### 4. Get Users (Protected Endpoint - Requires USER or ADMIN role)
```bash
curl -X GET http://localhost:2026/api/v1/users \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN"
```

## Complete Testing Workflow

### Step 1: Set Environment Variables
```bash
#!/bin/bash

KEYCLOAK_URL="http://localhost:30080"
REALM="microservices-realm"
CLIENT_ID="apigateway-client"
CLIENT_SECRET="your-client-secret"  # Set your actual client secret
USERNAME="admin-user"
PASSWORD="admin123"
GATEWAY_URL="http://localhost:2027"
USER_SERVICE_URL="http://localhost:2026"
```

### Step 2: Get Token (Testing Script)
```bash
#!/bin/bash

echo "Getting access token..."
TOKEN_RESPONSE=$(curl -s -X POST "${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=${USERNAME}" \
  -d "password=${PASSWORD}" \
  -d "client_id=${CLIENT_ID}" \
  -d "grant_type=password")

echo "Token Response:"
echo $TOKEN_RESPONSE | jq .

ACCESS_TOKEN=$(echo $TOKEN_RESPONSE | jq -r '.access_token')
REFRESH_TOKEN=$(echo $TOKEN_RESPONSE | jq -r '.refresh_token')

echo ""
echo "Access Token: $ACCESS_TOKEN"
echo "Refresh Token: $REFRESH_TOKEN"
```

### Step 3: Decode JWT (View Claims)
```bash
#!/bin/bash

echo "JWT Token Claims:"
echo $ACCESS_TOKEN | cut -d'.' -f2 | base64 -d | jq .

# Expected claims:
# {
#   "exp": 1234567890,
#   "iat": 1234567880,
#   "jti": "uuid",
#   "iss": "http://localhost:30080/realms/microservices-realm",
#   "aud": "account",
#   "sub": "user-id",
#   "typ": "Bearer",
#   "azp": "apigateway-client",
#   "nonce": "nonce-value",
#   "session_state": "session-id",
#   "name": "Admin User",
#   "preferred_username": "admin-user",
#   "given_name": "Admin",
#   "family_name": "User",
#   "email": "admin@example.com",
#   "email_verified": true,
#   "realm_access": {
#     "roles": ["default-roles-microservices-realm", "ADMIN", "USER"]
#   },
#   "resource_access": {
#     "account": {
#       "roles": ["manage-account", "manage-account-links", "view-profile"]
#     }
#   }
# }
```

### Step 4: Test Protected Endpoints
```bash
#!/bin/bash

# Test Gateway
echo "Testing Gateway Protected Endpoint..."
curl -X GET "${GATEWAY_URL}/api/v1/users" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" | jq .

# Test User Service
echo "Testing User Service Authentication Endpoint..."
curl -X GET "${USER_SERVICE_URL}/api/v1/auth/me" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" | jq .
```

### Step 5: Test Refresh Token
```bash
#!/bin/bash

echo "Refreshing Token..."
NEW_TOKEN_RESPONSE=$(curl -s -X POST "${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=${CLIENT_ID}" \
  -d "client_secret=${CLIENT_SECRET}" \
  -d "grant_type=refresh_token" \
  -d "refresh_token=${REFRESH_TOKEN}")

echo "New Token Response:"
echo $NEW_TOKEN_RESPONSE | jq .

NEW_ACCESS_TOKEN=$(echo $NEW_TOKEN_RESPONSE | jq -r '.access_token')
echo "New Access Token: $NEW_ACCESS_TOKEN"
```

### Step 6: Test Logout
```bash
#!/bin/bash

echo "Testing Logout..."
curl -X POST "${GATEWAY_URL}/auth/logout" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" | jq .

# Try to use token after logout (should still work if no blacklisting)
# Token validity depends on JWT expiration and blacklist implementation
curl -X GET "${GATEWAY_URL}/api/v1/users" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" | jq .
```

## Advanced Testing Scenarios

### Test 1: Role-Based Access Control
```bash
#!/bin/bash

# Create test users with different roles
# 1. admin-user with ADMIN, USER roles
# 2. regular-user with USER role

# Get token for regular user
REGULAR_TOKEN=$(curl -s -X POST "${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=test-user" \
  -d "password=test123" \
  -d "client_id=${CLIENT_ID}" \
  -d "grant_type=password" | jq -r '.access_token')

# This should work (USER endpoint with USER role)
echo "User accessing USER endpoint (should succeed):"
curl -X GET "${USER_SERVICE_URL}/api/v1/users" \
  -H "Authorization: Bearer ${REGULAR_TOKEN}" | jq .

# This should fail (ADMIN endpoint with USER role)
echo "User accessing ADMIN endpoint (should fail):"
curl -X DELETE "${USER_SERVICE_URL}/api/v1/users/123" \
  -H "Authorization: Bearer ${REGULAR_TOKEN}" | jq .
```

### Test 2: Expired Token
```bash
#!/bin/bash

# Create a token with short expiration or wait for expiration
EXPIRED_TOKEN="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.expired..."

echo "Testing with expired token (should fail):"
curl -X GET "${USER_SERVICE_URL}/api/v1/users" \
  -H "Authorization: Bearer ${EXPIRED_TOKEN}" | jq .

# Expected: 401 Unauthorized
```

### Test 3: Invalid Token
```bash
#!/bin/bash

INVALID_TOKEN="invalid.token.here"

echo "Testing with invalid token (should fail):"
curl -X GET "${USER_SERVICE_URL}/api/v1/users" \
  -H "Authorization: Bearer ${INVALID_TOKEN}" | jq .

# Expected: 401 Unauthorized
```

### Test 4: Missing Token
```bash
#!/bin/bash

echo "Testing without token (should fail):"
curl -X GET "${USER_SERVICE_URL}/api/v1/users" | jq .

# Expected: 401 Unauthorized or 302 Redirect
```

### Test 5: Service-to-Service Communication
```bash
#!/bin/bash

# Test that services can call each other with token propagation
# Make a request to gateway that calls user-service
echo "Testing Service-to-Service Communication..."
curl -v -X GET "${GATEWAY_URL}/api/v1/users" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}"

# Check logs to ensure token was propagated to user-service
```

## Postman Collection

### Import into Postman

Create a new Postman collection with these requests:

#### 1. Get Token
```
POST http://localhost:30080/realms/microservices-realm/protocol/openid-connect/token

Body (form-data):
- username: admin-user
- password: admin123
- client_id: apigateway-client
- grant_type: password
```

#### 2. Refresh Token
```
POST http://localhost:30080/realms/microservices-realm/protocol/openid-connect/token

Body (form-data):
- client_id: apigateway-client
- client_secret: YOUR_CLIENT_SECRET
- grant_type: refresh_token
- refresh_token: {{refresh_token}}  // From previous response
```

#### 3. Get Current User
```
GET http://localhost:2026/api/v1/auth/me

Headers:
- Authorization: Bearer {{access_token}}  // From token response
```

#### 4. Get Users List
```
GET http://localhost:2026/api/v1/users

Headers:
- Authorization: Bearer {{access_token}}
```

#### 5. Logout
```
POST http://localhost:2027/auth/logout

Headers:
- Authorization: Bearer {{access_token}}
```

### Postman Environment Variables
```json
{
  "id": "keycloak-env",
  "name": "Keycloak Testing",
  "values": [
    {
      "key": "keycloak_url",
      "value": "http://localhost:30080",
      "type": "string"
    },
    {
      "key": "realm",
      "value": "microservices-realm",
      "type": "string"
    },
    {
      "key": "client_id",
      "value": "apigateway-client",
      "type": "string"
    },
    {
      "key": "client_secret",
      "value": "your-client-secret",
      "type": "string"
    },
    {
      "key": "gateway_url",
      "value": "http://localhost:2027",
      "type": "string"
    },
    {
      "key": "user_service_url",
      "value": "http://localhost:2026",
      "type": "string"
    },
    {
      "key": "access_token",
      "value": "",
      "type": "string"
    },
    {
      "key": "refresh_token",
      "value": "",
      "type": "string"
    }
  ]
}
```

## Performance Testing

### Load Testing Authentication
```bash
#!/bin/bash

# Sequential requests
for i in {1..100}; do
  curl -s -X POST "${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/token" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "username=admin-user" \
    -d "password=admin123" \
    -d "client_id=${CLIENT_ID}" \
    -d "grant_type=password" > /dev/null
  echo "Request $i completed"
done
```

### Load Testing Protected Endpoint
```bash
#!/bin/bash

# Using ab (Apache Bench)
ab -n 1000 -c 10 \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  http://localhost:2026/api/v1/users
```

## Debugging

### Enable Debug Logging
```properties
# application.properties or application.yaml
logging.level.org.springframework.security=DEBUG
logging.level.org.springframework.security.oauth2=DEBUG
logging.level.org.keycloak=DEBUG
```

### View Keycloak Logs
```bash
# For Docker container
docker logs keycloak

# With follow
docker logs -f keycloak
```

### Check Token Validity
Visit: https://jwt.io
- Paste your JWT token
- View decoded claims and signature validation

## Common Error Responses

### 401 Unauthorized
```json
{
  "error": "Unauthorized",
  "error_description": "Invalid token"
}
```
**Causes**:
- No token provided
- Expired token
- Invalid token signature
- Wrong issuer URI

### 403 Forbidden
```json
{
  "error": "Forbidden",
  "error_description": "Insufficient permissions"
}
```
**Causes**:
- Missing required role
- Token doesn't contain required scope

### 400 Bad Request
```json
{
  "error": "invalid_grant",
  "error_description": "Invalid username or password"
}
```
**Causes**:
- Wrong username/password
- Invalid grant type
- Missing required parameters

### 500 Internal Server Error
```json
{
  "error": "server_error",
  "error_description": "Internal server error"
}
```
**Causes**:
- Service connection error
- Configuration issue
- Database error


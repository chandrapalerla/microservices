# Keycloak Authentication & Authorization Setup Guide

## Overview
This guide provides comprehensive instructions for setting up and using Keycloak-based authentication and authorization across your microservices architecture.

## Table of Contents
1. [Prerequisites](#prerequisites)
2. [Keycloak Setup](#keycloak-setup)
3. [Service Configuration](#service-configuration)
4. [JWT Authentication](#jwt-authentication)
5. [Role-Based Authorization](#role-based-authorization)
6. [Gateway Security](#gateway-security)
7. [Service-to-Service Communication](#service-to-service-communication)
8. [Token Propagation](#token-propagation)
9. [Swagger Security](#swagger-security)
10. [Refresh Tokens](#refresh-tokens)
11. [Logout](#logout)
12. [Testing](#testing)

## Prerequisites

### Required Software
- Java 25+
- Maven 3.6+
- Docker (for Keycloak)
- MySQL 8.0+
- Postman or cURL for testing

### Keycloak Installation
1. **Using Docker (Recommended)**:
```bash
docker run -d \
  --name keycloak \
  -e KEYCLOAK_ADMIN=admin \
  -e KEYCLOAK_ADMIN_PASSWORD=admin \
  -p 30080:8080 \
  quay.io/keycloak/keycloak:latest \
  start-dev
```

2. **Access Keycloak Admin Console**:
   - URL: http://localhost:30080/admin
   - Username: admin
   - Password: admin

## Keycloak Setup

### Step 1: Create Realm
1. Login to Keycloak admin console
2. Go to Realms section
3. Click "Create Realm"
4. Name it: `microservices-realm`
5. Click "Create"

### Step 2: Create OAuth2 Clients

#### API Gateway Client
1. Go to Clients → Create Client
2. Client ID: `apigateway-client`
3. Client Type: OpenID Connect
4. Next and configure:
   - **Capability config:**
     - Standard flow enabled: ON
     - Implicit flow enabled: OFF
     - Direct access grants enabled: ON
   - **Access settings:**
     - Valid redirect URIs: `http://localhost:2027/login/oauth2/code/keycloak`
     - Web origins: `http://localhost:2027`
5. Save

#### User Service Client
1. Go to Clients → Create Client
2. Client ID: `user-service-client`
3. Client Type: OpenID Connect
4. Configure:
   - **Capability config:**
     - Service accounts enabled: ON
     - Authorization Code Flow: OFF
     - Direct access grants: ON
   - **Client credentials:** (Get client secret here)
5. Save and note the client secret

#### Service-to-Service Client
1. Go to Clients → Create Client
2. Client ID: `service-to-service`
3. Client Type: OpenID Connect
4. Configure:
   - **Capability config:**
     - Service accounts enabled: ON
   - Assign to user-service

### Step 3: Create Roles

1. Go to Realm Roles → Create Role
2. Create the following roles:
   - `ADMIN` - For administrative operations
   - `USER` - For regular users
   - `SERVICE` - For service-to-service communication

### Step 4: Create Users

1. Go to Users → Create User
   - Username: `admin-user`
   - Email: `admin@example.com`
   - First Name: Admin
   - Last Name: User
   - Email Verified: ON
   - Assign Roles: ADMIN, USER

2. Set Password:
   - Go to Credentials tab
   - Set Password: `admin123` (or secure password)
   - Temporary: OFF

3. Create test user:
   - Username: `test-user`
   - Email: `test@example.com`
   - Assign Roles: USER

## Service Configuration

### API Gateway Configuration (application.yaml)

```yaml
server:
  port: 2027

spring:
  application:
    name: apigateway

  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:30080/realms/microservices-realm
      client:
        registration:
          keycloak:
            client-id: apigateway-client
            client-secret: YOUR_CLIENT_SECRET
            authorization-grant-type: authorization_code
            redirect-uri: http://localhost:2027/login/oauth2/code/keycloak
            scope: openid,profile,email
        provider:
          keycloak:
            issuer-uri: http://localhost:30080/realms/microservices-realm
            user-name-attribute: preferred_username

  cloud:
    gateway:
      routes:
        - id: user-service-route
          uri: lb://USER-SERVICE
          predicates:
            - Path=/api/v1/users/**
          filters:
            - StripPrefix=1

eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka
```

### User Service Configuration (application.properties)

```properties
# OAuth2/JWT Configuration
spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:30080/realms/microservices-realm
spring.security.oauth2.client.registration.keycloak.client-id=user-service-client
spring.security.oauth2.client.registration.keycloak.client-secret=YOUR_CLIENT_SECRET
spring.security.oauth2.client.registration.keycloak.authorization-grant-type=client_credentials
spring.security.oauth2.client.provider.keycloak.issuer-uri=http://localhost:30080/realms/microservices-realm
spring.security.oauth2.client.provider.keycloak.token-uri=http://localhost:30080/realms/microservices-realm/protocol/openid-connect/token

# JWT Configuration
jwt.issuer=http://localhost:30080/realms/microservices-realm
jwt.audience=user-service
```

## JWT Authentication

### How It Works
1. User logs in via API Gateway
2. Keycloak validates credentials and issues JWT token
3. User includes JWT in Authorization header for subsequent requests
4. Services validate JWT signature and claims
5. Request is allowed/denied based on token validity and claims

### JWT Token Structure
```
Header.Payload.Signature

Payload contains:
{
  "sub": "user-id",
  "preferred_username": "username",
  "email": "user@example.com",
  "realm_access": {
    "roles": ["USER", "ADMIN"]
  },
  "iss": "http://localhost:30080/realms/microservices-realm",
  "exp": 1234567890,
  "iat": 1234567890
}
```

### Using JWT Token

#### Getting a Token
```bash
# Using Direct Access Grant (for testing)
curl -X POST http://localhost:30080/realms/microservices-realm/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=admin-user" \
  -d "password=admin123" \
  -d "client_id=apigateway-client" \
  -d "grant_type=password"
```

#### Using the Token
```bash
TOKEN="your_jwt_token_here"

curl -X GET http://localhost:2027/api/v1/users \
  -H "Authorization: Bearer $TOKEN"
```

## Role-Based Authorization

### Securing Endpoints with Roles

#### In Spring Security Config
```java
.authorizeHttpRequests()
    .requestMatchers("/api/v1/users/**").hasAnyRole("USER", "ADMIN")
    .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
    .anyRequest().authenticated()
```

#### Using @PreAuthorize Annotation
```java
@GetMapping("/users")
@PreAuthorize("hasRole('USER')")
public List<User> getUsers() {
    // Only accessible to USER and ADMIN roles
}

@DeleteMapping("/users/{id}")
@PreAuthorize("hasRole('ADMIN')")
public void deleteUser(@PathVariable Long id) {
    // Only accessible to ADMIN role
}
```

#### Custom Role Checking
```java
@PostMapping("/sensitive-operation")
@RequiresAdmin
public ResponseEntity<?> sensitiveOperation() {
    // Implementation
}
```

## Gateway Security

### Security Filter Chain
The API Gateway enforces:
1. **CSRF Protection**: Disabled for API (configure if needed)
2. **CORS**: Only allows requests from configured origins
3. **JWT Validation**: All requests must have valid JWT (except public endpoints)
4. **Role-Based Access**: Routes are protected based on user roles
5. **Token Propagation**: Tokens are forwarded to backend services

### Public Endpoints
- `/auth/login` - Login endpoint
- `/auth/logout` - Logout endpoint
- `/auth/refresh` - Refresh token endpoint
- `/health` - Health check

### Protected Endpoints
- `/api/v1/users/**` - Requires USER or ADMIN role

### CORS Configuration
Configured to allow:
- Origins: `http://localhost:3000`, `http://localhost:4200`
- Methods: GET, POST, PUT, DELETE, OPTIONS, PATCH
- Headers: Authorization, all others allowed
- Credentials: Allowed

## Service-to-Service Communication

### Overview
Microservices communicate using OAuth2 Client Credentials flow for service accounts.

### Configuration
1. The user-service has OAuth2 client registration configured
2. When calling other services, it obtains a service-level token
3. Service account has necessary permissions for inter-service calls

### Implementing Service-to-Service Calls

#### Using WebClient
```java
@Autowired
private WebClient webClient;

public Mono<User> getUserFromAnotherService(String userId) {
    return webClient.get()
        .uri("lb://OTHER-SERVICE/api/users/{id}", userId)
        .retrieve()
        .bodyToMono(User.class);
}
```

#### Using RestTemplate
```java
@Autowired
private RestTemplate restTemplate;

public User getUserFromAnotherService(String userId) {
    return restTemplate.getForObject(
        "http://lb/OTHER-SERVICE/api/users/" + userId,
        User.class
    );
}
```

The interceptor automatically adds the service token.

## Token Propagation

### How Token Propagation Works
1. User makes request with JWT to API Gateway
2. Gateway validates JWT and stores it in request context
3. Gateway forwards request to backend service with JWT
4. Backend service validates JWT
5. If backend calls another service, token is automatically propagated
6. Chain of calls maintains original user context

### Implementation Details

#### API Gateway
- `TokenPropagationFilter`: Extracts JWT from incoming request
- Stores token in request attributes
- Available for use in downstream filters

#### User Service
- `JwtTokenFilter`: Validates incoming JWT
- `HttpClientConfig`: Adds JWT to outgoing HTTP requests
- `JwtUtility`: Helper methods for token handling

```java
// Example: Using propagated token in service
@GetMapping("/endpoint")
public Mono<ResponseEntity<?>> getEndpoint(HttpServletRequest request) {
    String token = (String) request.getAttribute("jwt_token");
    // Use token for downstream calls
}
```

## Swagger Security

### OpenAPI Configuration
The User Service includes Swagger UI with security configuration:

1. **JWT Bearer Scheme**: Configured and documented
2. **Security Requirement**: All endpoints require bearer token
3. **Interactive Testing**: Swagger UI supports JWT token input

### Using Swagger UI

1. Navigate to: `http://localhost:2026/swagger-ui.html`
2. Click "Authorize" button
3. Enter JWT token: `Bearer <your_jwt_token>`
4. Test endpoints with full authentication context

### Swagger Configuration
```java
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
    description = "JWT authentication token"
)
```

## Refresh Tokens

### Overview
Refresh tokens allow obtaining new access tokens without re-authenticating.

### Token Types
- **Access Token**: Short-lived (default 5 minutes), used for API authentication
- **Refresh Token**: Long-lived (default 30 days), used to get new access token
- **ID Token**: Contains user information and claims

### Configuration in Keycloak
1. Go to Realm → Settings → Tokens
2. Configure token lifetimes:
   - Access Token Lifespan: 5 minutes
   - Session Idle: 30 minutes
   - Session Max: 1 day
   - Refresh Token Max Reuse: Unlimited

### Using Refresh Token

```bash
# Get initial tokens
curl -X POST http://localhost:30080/realms/microservices-realm/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=admin-user" \
  -d "password=admin123" \
  -d "client_id=apigateway-client" \
  -d "grant_type=password"

# Response includes:
# {
#   "access_token": "...",
#   "refresh_token": "...",
#   "token_type": "Bearer",
#   "expires_in": 300
# }

# Use refresh token to get new access token
curl -X POST http://localhost:30080/realms/microservices-realm/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=apigateway-client" \
  -d "client_secret=YOUR_CLIENT_SECRET" \
  -d "grant_type=refresh_token" \
  -d "refresh_token=<refresh_token_from_previous_request>"
```

### Gateway Refresh Endpoint

```bash
curl -X POST http://localhost:2027/auth/refresh \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "refreshToken=<your_refresh_token>"
```

## Logout

### Logout Flow
1. User initiates logout request
2. Gateway/Service clears security context
3. Session is invalidated
4. Token can continue to be used if not blacklisted
5. Keycloak invalidates refresh token

### Logout Endpoints

#### API Gateway Logout
```bash
curl -X POST http://localhost:2027/auth/logout \
  -H "Authorization: Bearer <your_jwt_token>"
```

#### User Service Logout
```bash
curl -X POST http://localhost:2026/api/v1/auth/logout \
  -H "Authorization: Bearer <your_jwt_token>"
```

### Response
```json
{
  "status": "success",
  "message": "User logged out successfully",
  "timestamp": 1234567890
}
```

### Token Blacklisting (Optional)
For production environments, implement token blacklisting:

1. Create a token blacklist table in database
2. On logout, add token to blacklist
3. In JWT validation, check if token is blacklisted
4. Tokens expire naturally from blacklist after expiration time

## Testing

### 1. Setup Testing Environment

```bash
# Start all services
# 1. Keycloak (Docker)
docker run -d --name keycloak -e KEYCLOAK_ADMIN=admin \
  -e KEYCLOAK_ADMIN_PASSWORD=admin -p 30080:8080 \
  quay.io/keycloak/keycloak:latest start-dev

# 2. MySQL
docker run -d --name mysql -e MYSQL_ROOT_PASSWORD=root123 \
  -e MYSQL_DATABASE=user_db -p 30036:3306 mysql:8.0

# 3. Service Registry
cd serviceregistry
mvn spring-boot:run

# 4. User Service
cd user-service
mvn spring-boot:run

# 5. API Gateway
cd apigateway
mvn spring-boot:run
```

### 2. Test Authentication Flow

#### Get Token
```bash
TOKEN=$(curl -s -X POST \
  http://localhost:30080/realms/microservices-realm/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=admin-user" \
  -d "password=admin123" \
  -d "client_id=apigateway-client" \
  -d "grant_type=password" | jq -r '.access_token')

echo "Token: $TOKEN"
```

#### Call Protected Endpoint
```bash
curl -X GET http://localhost:2027/api/v1/users \
  -H "Authorization: Bearer $TOKEN"
```

#### Verify JWT Claims
```bash
# Decode JWT to see claims
echo $TOKEN | cut -d'.' -f2 | base64 -d | jq
```

### 3. Test Role-Based Authorization

#### Try with USER Role
```bash
# Should work for /api/v1/users
curl -X GET http://localhost:2027/api/v1/users \
  -H "Authorization: Bearer $TOKEN"
```

#### Try without Required Role
```bash
# Create a token for USER without ADMIN role and try admin endpoint
# Should get 403 Forbidden
```

### 4. Test Token Propagation

#### Make call from Gateway to User Service
```bash
# Add debug logging to see token propagation
curl -v -X GET http://localhost:2027/api/v1/users \
  -H "Authorization: Bearer $TOKEN"

# Check user-service logs to confirm token was propagated
```

### 5. Test Refresh Token

```bash
# Get new tokens
RESPONSE=$(curl -s -X POST \
  http://localhost:30080/realms/microservices-realm/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=admin-user" \
  -d "password=admin123" \
  -d "client_id=apigateway-client" \
  -d "grant_type=password")

ACCESS_TOKEN=$(echo $RESPONSE | jq -r '.access_token')
REFRESH_TOKEN=$(echo $RESPONSE | jq -r '.refresh_token')

# Use refresh token
NEW_RESPONSE=$(curl -s -X POST \
  http://localhost:30080/realms/microservices-realm/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=apigateway-client" \
  -d "client_secret=YOUR_CLIENT_SECRET" \
  -d "grant_type=refresh_token" \
  -d "refresh_token=$REFRESH_TOKEN")

NEW_TOKEN=$(echo $NEW_RESPONSE | jq -r '.access_token')
echo "New Token: $NEW_TOKEN"
```

### 6. Test Logout

```bash
curl -X POST http://localhost:2027/auth/logout \
  -H "Authorization: Bearer $TOKEN"

# Response should be success
```

### 7. Test Swagger UI

1. Navigate to: `http://localhost:2026/swagger-ui.html`
2. Click "Authorize" button in the UI
3. Paste the Bearer token
4. Click "Authorize"
5. Try out any endpoint

## Troubleshooting

### Common Issues

#### 1. Invalid JWT Signature
**Cause**: Service using wrong issuer URI or Keycloak not accessible
**Solution**: 
- Verify `spring.security.oauth2.resourceserver.jwt.issuer-uri` is correct
- Ensure Keycloak is running and accessible
- Check firewall rules

#### 2. Token Expiration
**Error**: `401 Unauthorized`
**Solution**:
- Use refresh token to get new access token
- Implement automatic token refresh in client

#### 3. CORS Issues
**Error**: `Access-Control-Allow-Origin header missing`
**Solution**:
- Verify CORS configuration includes client origin
- Check that preflight requests (OPTIONS) are allowed

#### 4. Role Not Found
**Error**: `Access Denied`, despite having required role
**Solution**:
- Check role name case sensitivity (usually `ROLE_ADMIN`)
- Verify role is assigned to user in Keycloak
- Check token contains realm_access.roles claim

#### 5. Service-to-Service Auth Fails
**Error**: Service cannot call another service
**Solution**:
- Verify OAuth2 client credentials configuration
- Ensure service account is created and active
- Check token endpoint is accessible

## Security Best Practices

1. **HTTPS Only**: Use HTTPS in production, not HTTP
2. **Secret Management**: Store client secrets in environment variables, not code
3. **Token Expiration**: Keep access tokens short-lived (5-15 minutes)
4. **Refresh Tokens**: Keep refresh tokens in secure HTTP-only cookies
5. **CORS Configuration**: Only allow trusted origins
6. **Rate Limiting**: Implement rate limiting on token endpoints
7. **Token Revocation**: Implement token blacklisting for logout
8. **Audit Logging**: Log all authentication/authorization events
9. **Certificate Validation**: Validate SSL certificates in production
10. **Scope Limiting**: Use minimal scopes for service accounts

## Additional Resources

- [Keycloak Documentation](https://www.keycloak.org/documentation)
- [Spring Security OAuth2](https://spring.io/projects/spring-security)
- [OAuth 2.0 Specification](https://tools.ietf.org/html/rfc6749)
- [JWT.io](https://jwt.io) - JWT visualization tool


  Context:
    Existing project: C:\git-hub\microservices\ecommerce\
    Existing services:
      - serviceregistry  :8761  (Eureka)
      - apigateway       :2027  (Spring Cloud Gateway MVC, servlet)
      - user-service     :2026  (Spring MVC + JPA + Hazelcast + Keycloak JWT)

  Task:
    Create order-service at C:\git-hub\microservices\ecommerce\order-service\
    as a standalone Maven project following the user-service structure exactly.

  ─── TECHNOLOGY STACK ────────────────────────────────────────────────────────
    Spring Boot       3.5.x (match user-service parent version)
    Java              25
    Web               spring-boot-starter-web  (MVC, NOT WebFlux)
    JPA               spring-boot-starter-data-jpa
    Database          MySQL  →  order_db
    Migration         Flyway (flyway-core + flyway-mysql) — no ddl-auto=update
    Cache             Hazelcast 5.7.0 (hazelcast + hazelcast-spring)
    Security          spring-boot-starter-security + spring-boot-starter-oauth2-resource-server
    Service discovery spring-cloud-starter-netflix-eureka-client
    Feign             spring-cloud-starter-openfeign
    Kafka             spring-kafka
    Resilience        resilience4j-spring-boot3 + spring-boot-starter-aop
    Validation        spring-boot-starter-validation
    Swagger           springdoc-openapi-starter-webmvc-ui 2.8.6
    Lombok            + MapStruct 1.5.5.Final  (same annotation processor setup as user-service)
    Spring Cloud      2025.0.2

  ─── SECURITY — copy exactly from user-service ───────────────────────────────
    1. KeycloakJwtConverter.java
         - reads realm_access.roles → ROLE_USER, ROLE_ADMIN
         - reads resource_access.<clientId>.roles
         - setPrincipalClaimName("preferred_username")
    2. SecurityConfig.java
         - @EnableWebSecurity + @EnableMethodSecurity
         - GatewaySecretFilter (inner static class, OncePerRequestFilter)
           blocks any /api/** request missing X-Gateway-Secret header → 403
         - JWT resource server using KeycloakJwtConverter.blocking()
         - SessionCreationPolicy.STATELESS
         - Permit: /swagger-ui/**, /v3/api-docs/**
         - Require ROLE_USER or ROLE_ADMIN: /api/v1/orders/**
    3. application.properties
         - gateway.internal-secret=gw-secret-change-in-prod
         - spring.security.oauth2.resourceserver.jwt.jwk-set-uri=
           http://localhost:30080/realms/microservices-realm/protocol/openid-connect/certs

  ─── DATABASE SCHEMA — Flyway migrations ─────────────────────────────────────
    src/main/resources/db/migration/
      V1__create_orders_table.sql
        CREATE TABLE orders (
          id               BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
          order_number     VARCHAR(20)  NOT NULL UNIQUE,
          user_id          BIGINT       NOT NULL,
          user_email       VARCHAR(255) NOT NULL,
          status           VARCHAR(30)  NOT NULL,
          payment_status   VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
          payment_method   VARCHAR(30)  NOT NULL,
          payment_reference VARCHAR(100),
          subtotal         DECIMAL(10,2) NOT NULL,
          tax_amount       DECIMAL(10,2) NOT NULL DEFAULT 0.00,
          shipping_amount  DECIMAL(10,2) NOT NULL DEFAULT 0.00,
          discount_amount  DECIMAL(10,2) NOT NULL DEFAULT 0.00,
          total_amount     DECIMAL(10,2) NOT NULL,
          coupon_code      VARCHAR(50),
          tracking_number  VARCHAR(100),
          courier_name     VARCHAR(100),
          notes            VARCHAR(500),
          shipping_full_name VARCHAR(200),
          shipping_phone   VARCHAR(20),
          shipping_street  VARCHAR(300),
          shipping_city    VARCHAR(100),
          shipping_state   VARCHAR(100),
          shipping_zip     VARCHAR(20),
          shipping_country VARCHAR(100),
          version          BIGINT       NOT NULL DEFAULT 0,
          created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
          confirmed_at     TIMESTAMP    NULL,
          shipped_at       TIMESTAMP    NULL,
          delivered_at     TIMESTAMP    NULL,
          cancelled_at     TIMESTAMP    NULL
        );

      V2__create_order_items_table.sql
        CREATE TABLE order_items (
          id            BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
          order_id      BIGINT       NOT NULL,
          product_id    BIGINT       NOT NULL,
          product_name  VARCHAR(255) NOT NULL,
          product_sku   VARCHAR(100),
          quantity      INT          NOT NULL,
          unit_price    DECIMAL(10,2) NOT NULL,
          total_price   DECIMAL(10,2) NOT NULL,
          CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders(id)
        );

      V3__create_order_status_history_table.sql
        CREATE TABLE order_status_history (
          id           BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
          order_id     BIGINT       NOT NULL,
          from_status  VARCHAR(30),
          to_status    VARCHAR(30)  NOT NULL,
          reason       VARCHAR(500),
          changed_by   VARCHAR(255) NOT NULL,
          changed_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
          CONSTRAINT fk_history_order FOREIGN KEY (order_id) REFERENCES orders(id)
        );

  ─── DOMAIN — Entities and Enums ─────────────────────────────────────────────
    OrderStatus enum:
      PENDING, PAYMENT_FAILED, CONFIRMED, PROCESSING,
      SHIPPED, OUT_FOR_DELIVERY, DELIVERED,
      RETURN_REQUESTED, RETURNED, REFUNDED, CANCELLED

    PaymentStatus enum:  PENDING, PAID, FAILED, REFUNDED

    PaymentMethod enum:  CREDIT_CARD, DEBIT_CARD, UPI, NET_BANKING

    Order entity  (implements Serializable — required for Hazelcast)
      - @Version Long version (optimistic locking)
      - OrderStatus as @Enumerated(STRING)
      - List<OrderItem> as @OneToMany(cascade=ALL, orphanRemoval=true)

    OrderItem entity (implements Serializable)
    OrderStatusHistory entity

  ─── ORDER LIFECYCLE — State machine ─────────────────────────────────────────
    Enforce valid transitions in OrderService via a transitions map:
      PENDING           → CONFIRMED, PAYMENT_FAILED, CANCELLED
      PAYMENT_FAILED    → CONFIRMED, CANCELLED
      CONFIRMED         → PROCESSING, CANCELLED
      PROCESSING        → SHIPPED, CANCELLED (ADMIN only)
      SHIPPED           → OUT_FOR_DELIVERY, DELIVERED
      OUT_FOR_DELIVERY  → DELIVERED
      DELIVERED         → RETURN_REQUESTED
      RETURN_REQUESTED  → RETURNED
      RETURNED          → REFUNDED
      CANCELLED         → (terminal)
      REFUNDED          → (terminal)

    Throw InvalidOrderTransitionException (HTTP 400) for illegal transitions.

  ─── CACHING — Hazelcast ─────────────────────────────────────────────────────
    Cache maps in hazelcast.xml:
      orders      TTL 10min, max 1000 entries, LRU eviction
      ordersPage  TTL 5min,  max 500 entries,  LRU eviction

    Annotations in OrderService:
      getById()   → @Cacheable("orders", key="#id")
      getAll()    → @Cacheable("ordersPage", key="#pageable")
      create()    → @CacheEvict("ordersPage", allEntries=true)
      any status  → @Caching: evict orders key, evict ordersPage allEntries

  ─── FEIGN CLIENTS ───────────────────────────────────────────────────────────
    UserServiceClient
      @FeignClient(name="user-service", url="${services.user-service.url}")
      UserDto getUser(@PathVariable Long id);

    ProductServiceClient
      @FeignClient(name="product-service", url="${services.product-service.url}")
      ProductDto getProduct(@PathVariable Long id);
      void deductStock(@PathVariable Long id, @RequestBody StockRequest request);

    FeignSecurityConfig — RequestInterceptor:
      copies Authorization: Bearer <token> from SecurityContext to all Feign calls
      also adds X-Gateway-Secret header to inter-service calls

    Resilience4j on every Feign call:
      @CircuitBreaker(name="user-service",    fallbackMethod="...")
      @CircuitBreaker(name="product-service", fallbackMethod="...")
      @Retry(name="user-service")
      @Retry(name="product-service")

  ─── KAFKA PRODUCER ──────────────────────────────────────────────────────────
    Topic: order-events
    Key:   orderId (String)
    Payload: OrderEvent record {
      String  eventId       (UUID)
      String  eventType     (ORDER_CREATED | ORDER_CONFIRMED | ORDER_SHIPPED | ...)
      Long    orderId
      Long    userId
      String  userEmail
      List    items         (productId, productName, quantity, unitPrice)
      BigDecimal totalAmount
      String  trackingNumber (nullable)
      Instant timestamp
    }
    Publish on EVERY status transition from OrderService.
    Use KafkaTemplate<String, OrderEvent> with JsonSerializer.

  ─── ENDPOINTS — 14 total ────────────────────────────────────────────────────
    POST   /api/v1/orders                           USER   → place order
    GET    /api/v1/orders                           ADMIN  → paginated all orders
    GET    /api/v1/orders/my                        USER   → current user's orders
    GET    /api/v1/orders/{id}                      USER,ADMIN → by ID
    GET    /api/v1/orders/number/{orderNumber}      USER,ADMIN → by order number
    GET    /api/v1/orders/{id}/items                USER,ADMIN → line items only
    GET    /api/v1/orders/{id}/history              USER,ADMIN → status history
    POST   /api/v1/orders/{id}/confirm-payment      ADMIN  → PENDING→CONFIRMED
    POST   /api/v1/orders/{id}/process              ADMIN  → CONFIRMED→PROCESSING
    POST   /api/v1/orders/{id}/ship                 ADMIN  → PROCESSING→SHIPPED
    POST   /api/v1/orders/{id}/out-for-delivery     ADMIN  → SHIPPED→OUT_FOR_DELIVERY
    POST   /api/v1/orders/{id}/deliver              ADMIN  → OUT_FOR_DELIVERY→DELIVERED
    POST   /api/v1/orders/{id}/cancel               USER,ADMIN → cancel
    POST   /api/v1/orders/{id}/return               USER   → DELIVERED→RETURN_REQUESTED
    PUT    /api/v1/orders/{id}/status               ADMIN  → generic transition

    Authorization rule on USER endpoints:
      order.userId must equal JWT userId; otherwise return 403.

  ─── DTOS ────────────────────────────────────────────────────────────────────
    OrderRequest     { items[{productId,quantity}], shippingAddress, paymentMethod, couponCode, notes }
    OrderResponse    { id, orderNumber, userId, userEmail, status, paymentStatus, items[],
                       subtotal, taxAmount, shippingAmount, discountAmount, totalAmount,
                       couponCode, shippingAddress, trackingNumber, notes,
                       estimatedDelivery, createdAt, confirmedAt, shippedAt, deliveredAt }
    OrderItemResponse { id, productId, productName, productSku, quantity, unitPrice, totalPrice }
    StatusUpdateRequest  { reason }
    ShipRequest          { trackingNumber, courierName, notes }
    PaymentConfirmRequest { paymentReference, notes }
    ReturnRequest        { reason, description, items[{orderItemId,quantity,reason}] }
    OrderStatusHistoryResponse { fromStatus, toStatus, reason, changedBy, changedAt }

    Use MapStruct for all entity↔DTO mappings.

  ─── PLACE ORDER — Business logic ────────────────────────────────────────────
    1. Feign → user-service: validate userId, get userEmail
    2. For each item:
         Feign → product-service: validate product, snapshot name/sku/price, check stock
    3. Calculate:
         subtotal       = Σ (qty × unit_price)
         tax_amount     = subtotal × 0.18
         shipping       = subtotal > 500 ? 0 : 50
         discount       = coupon valid ? subtotal × rate : 0
         total_amount   = subtotal + tax + shipping − discount
    4. For each item: Feign → product-service: deduct stock
    5. Generate orderNumber: "ORD-" + YEAR + "-" + LPAD(sequence,5,'0')
    6. Persist Order + OrderItems + first OrderStatusHistory record
    7. Publish ORDER_CREATED to Kafka
    8. Return 201 Created

  ─── CANCEL ORDER — Stock restoration ────────────────────────────────────────
    If order.status was CONFIRMED, PROCESSING (stock was deducted):
      For each OrderItem: Feign → product-service: restore stock
    If order.status was PENDING (stock not yet deducted): no restoration needed.

  ─── APPLICATION.PROPERTIES ──────────────────────────────────────────────────
    spring.application.name=order-service
    server.port=2029
    spring.datasource.url=jdbc:mysql://localhost:30036/order_db
    spring.datasource.username=root
    spring.datasource.password=root123
    spring.jpa.hibernate.ddl-auto=validate
    spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect
    spring.flyway.enabled=true
    spring.flyway.locations=classpath:db/migration
    eureka.client.service-url.defaultZone=http://localhost:8761/eureka
    gateway.internal-secret=gw-secret-change-in-prod
    spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:30080/...
    spring.kafka.bootstrap-servers=localhost:9092
    spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer
    spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer
    services.user-service.url=http://localhost:2026
    services.product-service.url=http://localhost:2028
    spring.hazelcast.config=classpath:hazelcast.xml
    spring.cache.type=hazelcast

  ─── API GATEWAY CHANGES ─────────────────────────────────────────────────────
    File: apigateway/src/main/java/com/apigateway/config/GatewayConfig.java
      Add @Value("${gateway.routes.order-service:http://localhost:2029}") orderServiceUrl
      Add orderServiceRoutes() @Bean routing /api/v1/orders/** to orderServiceUrl

    File: apigateway/src/main/java/com/apigateway/config/SecurityConfig.java
      Add inside authorizeHttpRequests():
        .requestMatchers("/api/v1/orders/**").hasAnyRole("USER", "ADMIN")

    File: apigateway/src/main/resources/application.yaml
      Add under gateway.routes:
        order-service: http://localhost:2029

  ─── PROJECT STRUCTURE ───────────────────────────────────────────────────────
    order-service/
    ├── pom.xml
    └── src/main/java/com/order/
        ├── OrderServiceApplication.java   (@SpringBootApplication @EnableFeignClients @EnableCaching)
        ├── config/
        │   ├── KeycloakJwtConverter.java
        │   ├── SecurityConfig.java        (GatewaySecretFilter inner class)
        │   ├── FeignSecurityConfig.java   (JWT forwarding RequestInterceptor)
        │   ├── KafkaConfig.java           (topic bean: order-events, 3 partitions)
        │   └── SwaggerConfig.java
        ├── controller/
        │   └── OrderController.java       (all 14 endpoints)
        ├── service/
        │   └── OrderService.java          (@Cacheable, state machine, Feign calls, Kafka publish)
        ├── repository/
        │   ├── OrderRepository.java
        │   └── OrderStatusHistoryRepository.java
        ├── entity/
        │   ├── Order.java                 (implements Serializable, @Version)
        │   ├── OrderItem.java             (implements Serializable)
        │   └── OrderStatusHistory.java
        ├── enums/
        │   ├── OrderStatus.java
        │   ├── PaymentStatus.java
        │   └── PaymentMethod.java
        ├── dto/
        │   ├── request/
        │   │   ├── OrderRequest.java
        │   │   ├── StatusUpdateRequest.java
        │   │   ├── ShipRequest.java
        │   │   ├── PaymentConfirmRequest.java
        │   │   └── ReturnRequest.java
        │   └── response/
        │       ├── OrderResponse.java
        │       ├── OrderItemResponse.java
        │       └── OrderStatusHistoryResponse.java
        ├── mapper/
        │   └── OrderMapper.java           (MapStruct)
        ├── client/
        │   ├── UserServiceClient.java     (@FeignClient)
        │   ├── ProductServiceClient.java  (@FeignClient)
        │   └── dto/
        │       ├── UserDto.java
        │       └── ProductDto.java
        ├── kafka/
        │   ├── OrderEventPublisher.java   (KafkaTemplate)
        │   └── event/
        │       └── OrderEvent.java
        └── exception/
            ├── GlobalExceptionHandler.java
            ├── ResourceNotFoundException.java
            ├── InvalidOrderTransitionException.java
            └── InsufficientStockException.java

    order-service/src/main/resources/
    ├── application.properties
    ├── hazelcast.xml
    └── db/migration/
        ├── V1__create_orders_table.sql
        ├── V2__create_order_items_table.sql
        └── V3__create_order_status_history_table.sql

  ─── QUALITY REQUIREMENTS ────────────────────────────────────────────────────
    - Write every file in full — no TODOs, no placeholders, no "add logic here"
    - Every entity implements java.io.Serializable with serialVersionUID
    - Flyway migrations run before any JPA validation
    - spring.jpa.hibernate.ddl-auto=validate (never update or create)
    - Hazelcast whitelist in hazelcast.xml includes com.order and
      org.springframework.data.domain prefixes
    - All controller endpoints have @Operation and @ApiResponse Swagger annotations
    - GlobalExceptionHandler handles: ResourceNotFoundException (404),
      InvalidOrderTransitionException (400), InsufficientStockException (409),
      MethodArgumentNotValidException (400), DataIntegrityViolationException (409),
      FeignException (502/503 depending on type), Exception (500)
    - User ownership check on every USER-role endpoint (order.userId == JWT userId)
    - pom.xml annotation processor order: Lombok first, then MapStruct
    - No spring-boot-starter-webflux anywhere in pom.xml

  ---
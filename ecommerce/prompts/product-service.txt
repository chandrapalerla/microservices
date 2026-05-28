  Context:
    Existing project: C:\git-hub\microservices\ecommerce\
    Existing services:
      - serviceregistry  :8761  (Eureka)
      - apigateway       :2027  (Spring Cloud Gateway MVC, servlet)
      - user-service     :2026  (Spring MVC + JPA + Hazelcast + Keycloak JWT)
      - order-service    :2029  (Spring MVC + JPA + Hazelcast + Feign + Kafka)

    The order-service already has a ProductServiceClient pointing to :2028 with these endpoints:
      GET  /api/v1/products/{id}                  → ProductDto
      POST /api/v1/products/{id}/deduct-stock      → void
      POST /api/v1/products/{id}/restore-stock     → void
    These three endpoints MUST exist with these exact paths and return types.

  Task:
    Create product-service at C:\git-hub\microservices\ecommerce\product-service\
    as a standalone Maven project following the user-service and order-service structure exactly.

  ─── TECHNOLOGY STACK ────────────────────────────────────────────────────────
    Spring Boot       3.5.x (match user-service parent version)
    Java              25
    Web               spring-boot-starter-web  (MVC, NOT WebFlux)
    JPA               spring-boot-starter-data-jpa + JpaSpecificationExecutor for search
    Database          MySQL  →  product_db
    Migration         Flyway (flyway-core + flyway-mysql) — no ddl-auto=update
    Cache             Hazelcast 5.7.0 (hazelcast + hazelcast-spring)
    Security          spring-boot-starter-security + spring-boot-starter-oauth2-resource-server
    Service discovery spring-cloud-starter-netflix-eureka-client
    Kafka             spring-kafka  (producer only — publishes product and stock events)
    Validation        spring-boot-starter-validation
    Swagger           springdoc-openapi-starter-webmvc-ui 2.8.6
    Lombok            + MapStruct 1.5.5.Final  (same annotation processor setup as user-service)
    Spring Cloud      2025.0.2
    NO Feign          product-service does not call other microservices
    NO Resilience4j   no downstream Feign calls to wrap

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
         - Require ROLE_USER or ROLE_ADMIN for all /api/v1/products/** and /api/v1/categories/**
           (stock deduction/restoration called by order-service propagates the user's JWT,
            so USER role is sufficient — ADMIN is NOT required for deduct/restore)
    3. application.properties
         - gateway.internal-secret=gw-secret-change-in-prod
         - spring.security.oauth2.resourceserver.jwt.jwk-set-uri=
           http://localhost:30080/realms/microservices-realm/protocol/openid-connect/certs

  ─── DATABASE SCHEMA — Flyway migrations ─────────────────────────────────────
    src/main/resources/db/migration/

      V1__create_categories_table.sql
        CREATE TABLE categories (
          id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
          name        VARCHAR(100) NOT NULL UNIQUE,
          description VARCHAR(500),
          slug        VARCHAR(100) NOT NULL UNIQUE,
          parent_id   BIGINT,
          active      BOOLEAN      NOT NULL DEFAULT TRUE,
          created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
          CONSTRAINT fk_category_parent FOREIGN KEY (parent_id) REFERENCES categories(id)
        );

      V2__create_products_table.sql
        CREATE TABLE products (
          id                  BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
          name                VARCHAR(255)  NOT NULL,
          sku                 VARCHAR(100)  NOT NULL UNIQUE,
          description         TEXT,
          brand               VARCHAR(100),
          category_id         BIGINT,
          price               DECIMAL(10,2) NOT NULL,
          original_price      DECIMAL(10,2),
          stock_quantity      INT           NOT NULL DEFAULT 0,
          reserved_quantity   INT           NOT NULL DEFAULT 0,
          low_stock_threshold INT           NOT NULL DEFAULT 10,
          status              VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
          thumbnail_url       VARCHAR(500),
          weight              DECIMAL(8,3),
          version             BIGINT        NOT NULL DEFAULT 0,
          created_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
          updated_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
                                            ON UPDATE CURRENT_TIMESTAMP,
          CONSTRAINT fk_product_category FOREIGN KEY (category_id) REFERENCES categories(id)
        );

      V3__seed_categories.sql
        INSERT INTO categories (name, description, slug, active) VALUES
          ('Electronics',  'Electronic devices and accessories',  'electronics',  TRUE),
          ('Clothing',     'Apparel for men, women and children', 'clothing',     TRUE),
          ('Books',        'Physical and digital books',          'books',        TRUE),
          ('Home & Garden','Home décor, tools and garden items',  'home-garden',  TRUE),
          ('Sports',       'Sports and outdoor equipment',        'sports',       TRUE),
          ('Beauty',       'Beauty, health and personal care',    'beauty',       TRUE),
          ('Toys',         'Toys and games for all ages',         'toys',         TRUE),
          ('Food',         'Grocery and gourmet food items',      'food',         TRUE);

  ─── DOMAIN — Entities and Enums ─────────────────────────────────────────────
    ProductStatus enum:  ACTIVE, INACTIVE, OUT_OF_STOCK, DISCONTINUED

    Category entity (implements Serializable)
      - id, name, description, slug (unique), parent (self-referential @ManyToOne), active
      - createdAt set via @PrePersist

    Product entity  (implements Serializable — required for Hazelcast)
      - @Version Long version (optimistic locking — critical for concurrent stock deductions)
      - @ManyToOne Category category
      - @Enumerated(STRING) ProductStatus status
      - stockQuantity: available to purchase
      - reservedQuantity: reserved by pending orders (informational, updated by events)
      - lowStockThreshold: triggers LOW_STOCK event when stock drops below this value
      - originalPrice: null means no discount; if set, price < originalPrice = on sale

  ─── CACHING — Hazelcast ─────────────────────────────────────────────────────
    Cache maps in hazelcast.xml:
      products      TTL 10min, max 1000 entries, LRU eviction
      productsPage  TTL 5min,  max 500 entries,  LRU eviction
      categories    TTL 30min, max 200 entries,  LRU eviction

    Annotations in ProductService:
      getById()         → @Cacheable("products",  key="#id")
      getBySku()        → @Cacheable("products",  key="'sku:'+#sku")
      getAll()          → @Cacheable("productsPage", key="...")
      search()          → @Cacheable("productsPage", key="'search:'+#filter.cacheKey()")
      getByCategory()   → @Cacheable("productsPage", key="'cat:'+#categoryId+':'+#pageable...")
      create()          → @CacheEvict("productsPage" + "categories", allEntries=true)
      update()          → @Caching: evict products key, evict productsPage allEntries
      deductStock()     → @Caching: evict products key (stockQuantity changed)
      restoreStock()    → @Caching: evict products key
      updateStatus()    → @Caching: evict products key, evict productsPage allEntries
      delete()          → @Caching: evict products key, evict productsPage allEntries

    CategoryService:
      getAll()  → @Cacheable("categories", key="'all'")
      create()  → @CacheEvict("categories", allEntries=true)
      update()  → @CacheEvict("categories", allEntries=true)
      delete()  → @CacheEvict("categories", allEntries=true)

  ─── KAFKA PRODUCER ──────────────────────────────────────────────────────────
    Topic: product-events
    Key:   productId (String)
    Payload: ProductEvent record {
      String  eventId       (UUID)
      String  eventType     (PRODUCT_CREATED | PRODUCT_UPDATED | PRODUCT_STATUS_CHANGED |
                             STOCK_DEDUCTED | STOCK_RESTORED | STOCK_UPDATED |
                             STOCK_LOW | STOCK_OUT)
      Long    productId
      String  productName
      String  sku
      BigDecimal price
      Integer stockQuantity      (value AFTER the change)
      Integer stockDelta         (positive=added, negative=deducted; null for non-stock events)
      String  status
      Long    categoryId
      Instant timestamp
    }
    Publish:
      - PRODUCT_CREATED       on successful create
      - PRODUCT_UPDATED       on update()
      - PRODUCT_STATUS_CHANGED on status change
      - STOCK_DEDUCTED        on deductStock() — after successful deduction
      - STOCK_RESTORED        on restoreStock()
      - STOCK_UPDATED         on setStock()
      - STOCK_LOW             additionally when stockQuantity drops below lowStockThreshold
      - STOCK_OUT             additionally when stockQuantity reaches 0
    Use KafkaTemplate<String, ProductEvent> with JsonSerializer.

  ─── ENDPOINTS — 18 total ────────────────────────────────────────────────────
    ── Product endpoints (14) ──────────────────────────────────────────────────
    POST   /api/v1/products                         ADMIN  → create product
    GET    /api/v1/products                         USER   → paginated, default status=ACTIVE
    GET    /api/v1/products/{id}                    USER   → by ID  ← used by order-service Feign
    GET    /api/v1/products/sku/{sku}               USER   → by SKU (unique)
    GET    /api/v1/products/search                  USER   → dynamic search (query params below)
    GET    /api/v1/products/category/{categoryId}   USER   → products in a category (paginated)
    GET    /api/v1/products/low-stock               ADMIN  → stock < lowStockThreshold
    PUT    /api/v1/products/{id}                    ADMIN  → full update (all fields)
    PATCH  /api/v1/products/{id}/status             ADMIN  → change ProductStatus
    PATCH  /api/v1/products/{id}/price              ADMIN  → update price + originalPrice
    PUT    /api/v1/products/{id}/stock              ADMIN  → set absolute stockQuantity
    POST   /api/v1/products/{id}/deduct-stock       USER   → deduct stock ← called by order-service
    POST   /api/v1/products/{id}/restore-stock      USER   → restore stock ← called by order-service
    DELETE /api/v1/products/{id}                    ADMIN  → soft-delete (status=DISCONTINUED)

    ── Category endpoints (4) ──────────────────────────────────────────────────
    GET    /api/v1/categories                       USER   → all active categories (flat + tree)
    POST   /api/v1/categories                       ADMIN  → create category
    PUT    /api/v1/categories/{id}                  ADMIN  → update category
    DELETE /api/v1/categories/{id}                  ADMIN  → delete (reject if products exist)

    Search query params (GET /api/v1/products/search):
      name        String   — case-insensitive contains match on product name
      categoryId  Long     — filter by category
      brand       String   — case-insensitive contains match
      minPrice    Decimal  — price >= minPrice
      maxPrice    Decimal  — price <= maxPrice
      inStockOnly Boolean  — stockQuantity > 0 when true
      status      Enum     — default ACTIVE (users); ADMIN can filter by any status
      page, size, sort     — standard Pageable params

  ─── DTOS ────────────────────────────────────────────────────────────────────
    Request DTOs:
      ProductRequest     { name, sku, description, brand, categoryId, price, originalPrice,
                           stockQuantity, lowStockThreshold, status, thumbnailUrl, weight }
      UpdateStockRequest { quantity }                  ← used by deduct/restore/setStock
      UpdatePriceRequest { price, originalPrice }
      UpdateStatusRequest { status }
      CategoryRequest    { name, description, slug, parentId }
      ProductSearchRequest { name, categoryId, brand, minPrice, maxPrice, inStockOnly, status }
        + method: String cacheKey() → concatenation of all non-null fields for Hazelcast key

    Response DTOs (all implement Serializable for Hazelcast):
      ProductResponse    { id, sku, name, description, brand, price, originalPrice,
                           discountPercent (computed: if originalPrice set),
                           stockQuantity, reservedQuantity, lowStockThreshold,
                           status, stockStatus (computed: IN_STOCK/LOW_STOCK/OUT_OF_STOCK),
                           thumbnailUrl, weight, category (CategoryResponse), version,
                           createdAt, updatedAt }
      ProductSummaryResponse { id, sku, name, brand, price, originalPrice, discountPercent,
                               stockQuantity, status, stockStatus, thumbnailUrl, categoryName }
        — lightweight DTO for paginated lists (no description, no full category)
      CategoryResponse   { id, name, description, slug, parentId, parentName, active, productCount }

    Use MapStruct for all entity ↔ DTO mappings.
    discountPercent computed in mapper: if originalPrice != null && originalPrice > price
        → ((originalPrice - price) / originalPrice * 100).setScale(1, HALF_UP)
        else → null
    stockStatus computed in mapper:
        stockQuantity == 0             → OUT_OF_STOCK
        stockQuantity < lowStockThreshold → LOW_STOCK
        else                           → IN_STOCK

  ─── STOCK DEDUCTION LOGIC — thread-safe ─────────────────────────────────────
    deductStock(Long id, UpdateStockRequest req):
      1. Load product (findById)
      2. if product.stockQuantity < req.quantity → throw InsufficientStockException
      3. product.stockQuantity -= req.quantity   (@Version handles concurrent requests)
      4. saveAndFlush (ObjectOptimisticLockingFailureException → GlobalExceptionHandler → 409)
      5. @Caching: evict products key
      6. Publish STOCK_DEDUCTED event
      7. if product.stockQuantity == 0 → also publish STOCK_OUT
         if product.stockQuantity < product.lowStockThreshold → also publish STOCK_LOW
      8. Return updated ProductResponse

    restoreStock(Long id, UpdateStockRequest req):
      1. Load product
      2. product.stockQuantity += req.quantity
      3. saveAndFlush
      4. Evict cache, publish STOCK_RESTORED event
      5. Return updated ProductResponse

    setStock(Long id, UpdateStockRequest req):  [admin: absolute value]
      1. Load product
      2. int delta = req.quantity - product.stockQuantity
      3. product.stockQuantity = req.quantity
      4. if req.quantity == 0 → product.status = OUT_OF_STOCK
         else if product.status == OUT_OF_STOCK → product.status = ACTIVE
      5. saveAndFlush; evict cache; publish STOCK_UPDATED (with delta)
      6. if new stock == 0 → publish STOCK_OUT
         if 0 < new stock < threshold → publish STOCK_LOW
      7. Return updated ProductResponse

  ─── CATEGORY DELETE GUARD ───────────────────────────────────────────────────
    Before deleting a category, check:
      if productRepository.existsByCategoryId(id) → throw CategoryInUseException (HTTP 409)
    Only delete if no products (including DISCONTINUED) reference this category.

  ─── APPLICATION.PROPERTIES ──────────────────────────────────────────────────
    spring.application.name=product-service
    server.port=2028
    spring.datasource.url=jdbc:mysql://localhost:30036/product_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
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
    spring.kafka.producer.properties.spring.json.add.type.headers=false
    spring.hazelcast.config=classpath:hazelcast.xml
    spring.cache.type=hazelcast
    springdoc.api-docs.path=/v3/api-docs
    springdoc.swagger-ui.path=/swagger-ui.html

  ─── API GATEWAY CHANGES ─────────────────────────────────────────────────────
    File: apigateway/src/main/java/com/apigateway/config/GatewayConfig.java
      Add @Value("${gateway.routes.product-service:http://localhost:2028}") productServiceUrl
      Add productServiceRoutes() @Bean routing /api/v1/products/** and /api/v1/categories/**
      to productServiceUrl, stamping X-Gateway-Secret on each.

    File: apigateway/src/main/java/com/apigateway/config/SecurityConfig.java
      Add inside authorizeHttpRequests():
        .requestMatchers("/api/v1/products/**").hasAnyRole("USER", "ADMIN")
        .requestMatchers("/api/v1/categories/**").hasAnyRole("USER", "ADMIN")

    File: apigateway/src/main/resources/application.yaml
      Add under gateway.routes:
        product-service: http://localhost:2028

  ─── PROJECT STRUCTURE ───────────────────────────────────────────────────────
    product-service/
    ├── pom.xml
    └── src/main/java/com/product/
        ├── ProductServiceApplication.java    (@SpringBootApplication @EnableCaching)
        ├── config/
        │   ├── KeycloakJwtConverter.java
        │   ├── SecurityConfig.java           (GatewaySecretFilter inner class)
        │   ├── KafkaConfig.java              (topic bean: product-events, 3 partitions)
        │   └── SwaggerConfig.java
        ├── controller/
        │   ├── ProductController.java        (14 product endpoints)
        │   └── CategoryController.java       (4 category endpoints)
        ├── service/
        │   ├── ProductService.java           (@Cacheable, stock logic, Kafka publish)
        │   └── CategoryService.java          (@Cacheable, delete guard)
        ├── repository/
        │   ├── ProductRepository.java        (JpaRepository + JpaSpecificationExecutor)
        │   └── CategoryRepository.java
        ├── specification/
        │   └── ProductSpecification.java     (JPA Criteria API for dynamic search)
        ├── entity/
        │   ├── Product.java                  (implements Serializable, @Version)
        │   └── Category.java                 (implements Serializable)
        ├── enums/
        │   ├── ProductStatus.java            (ACTIVE, INACTIVE, OUT_OF_STOCK, DISCONTINUED)
        │   └── StockStatus.java              (IN_STOCK, LOW_STOCK, OUT_OF_STOCK)
        ├── dto/
        │   ├── request/
        │   │   ├── ProductRequest.java
        │   │   ├── UpdateStockRequest.java
        │   │   ├── UpdatePriceRequest.java
        │   │   ├── UpdateStatusRequest.java
        │   │   ├── ProductSearchRequest.java  (+ cacheKey() method)
        │   │   └── CategoryRequest.java
        │   └── response/
        │       ├── ProductResponse.java       (implements Serializable, full detail)
        │       ├── ProductSummaryResponse.java (implements Serializable, for lists)
        │       └── CategoryResponse.java      (implements Serializable)
        ├── mapper/
        │   ├── ProductMapper.java             (MapStruct, computes discountPercent + stockStatus)
        │   └── CategoryMapper.java
        ├── kafka/
        │   ├── ProductEventPublisher.java     (KafkaTemplate)
        │   └── event/
        │       └── ProductEvent.java
        └── exception/
            ├── GlobalExceptionHandler.java    (404, 400, 409, 502, 500 + DuplicateSku, CategoryInUse)
            ├── ResourceNotFoundException.java
            ├── DuplicateSkuException.java     (HTTP 409)
            ├── InsufficientStockException.java (HTTP 409)
            └── CategoryInUseException.java    (HTTP 409)

    product-service/src/main/resources/
    ├── application.properties
    ├── hazelcast.xml
    └── db/migration/
        ├── V1__create_categories_table.sql
        ├── V2__create_products_table.sql
        └── V3__seed_categories.sql

  ─── QUALITY REQUIREMENTS ────────────────────────────────────────────────────
    - Write every file in full — no TODOs, no placeholders, no "add logic here"
    - Every entity implements java.io.Serializable with serialVersionUID = 1L
    - Every response DTO implements java.io.Serializable with serialVersionUID = 1L
    - Flyway migrations run before any JPA validation (ddl-auto=validate)
    - Hazelcast whitelist in hazelcast.xml includes com.product and
      org.springframework.data.domain prefixes
    - All controller endpoints have @Operation and @ApiResponse Swagger annotations
    - GlobalExceptionHandler handles: ResourceNotFoundException (404),
      DuplicateSkuException (409), InsufficientStockException (409),
      CategoryInUseException (409), ObjectOptimisticLockingFailureException (409 — concurrent
      stock update collision), MethodArgumentNotValidException (400),
      DataIntegrityViolationException (409), Exception (500)
    - pom.xml annotation processor order: Lombok first, then MapStruct
    - No spring-boot-starter-webflux anywhere in pom.xml
    - No Feign dependencies (product-service is a leaf service, no outbound calls)
    - ProductRepository extends JpaRepository<Product, Long> AND JpaSpecificationExecutor<Product>
    - ProductSpecification.withFilters(ProductSearchRequest) returns Specification<Product>
      using JPA Criteria API (cb.like, cb.equal, cb.greaterThanOrEqualTo, cb.lessThanOrEqualTo)
    - deductStock and restoreStock rely on @Version optimistic locking — no synchronized blocks
    - DELETE /api/v1/products/{id} is a SOFT DELETE (status → DISCONTINUED), not a hard delete
    - Category tree: CategoryResponse includes parentId and parentName
      (null for root categories); no recursive loading
    - productCount in CategoryResponse: use @Query COUNT in CategoryRepository
    - Swagger UI: http://localhost:2028/swagger-ui/index.html
    - OpenAPI JSON: http://localhost:2028/v3/api-docs

  ---

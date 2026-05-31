package com.order.service;

import com.order.client.ProductServiceClient;
import com.order.client.UserServiceClient;
import com.order.client.dto.ProductDto;
import com.order.client.dto.StockRequest;
import com.order.client.dto.UserDto;
import com.order.dto.request.OrderRequest;
import com.order.dto.request.PaymentConfirmRequest;
import com.order.dto.request.ReturnRequest;
import com.order.dto.request.ShipRequest;
import com.order.dto.request.StatusUpdateRequest;
import com.order.dto.response.OrderItemResponse;
import com.order.dto.response.OrderResponse;
import com.order.dto.response.OrderStatusHistoryResponse;
import com.order.entity.IdempotencyKey;
import com.order.entity.Order;
import com.order.entity.OrderItem;
import com.order.entity.OrderStatusHistory;
import com.order.entity.OutboxEvent;
import com.order.entity.PendingStockRestore;
import com.order.enums.CompensationStatus;
import com.order.enums.OrderStatus;
import com.order.enums.OutboxStatus;
import com.order.enums.PaymentStatus;
import com.order.exception.InsufficientStockException;
import com.order.exception.InvalidOrderTransitionException;
import com.order.exception.ResourceNotFoundException;
import com.order.mapper.OrderMapper;
import com.order.observability.OrderMetrics;
import com.order.repository.IdempotencyKeyRepository;
import com.order.repository.OrderRepository;
import com.order.repository.OrderStatusHistoryRepository;
import com.order.repository.OutboxEventRepository;
import com.order.repository.PendingStockRestoreRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.*;

/**
 * Core order business service.
 *
 * Key responsibilities:
 *  - Enforce the order state machine (VALID_TRANSITIONS map)
 *  - Orchestrate Feign calls to user-service and product-service
 *  - Calculate pricing (subtotal, tax @ 18%, shipping, discount via coupon)
 *  - Manage Hazelcast cache (@Cacheable / @CacheEvict)
 *  - Write outbox records (same transaction) instead of publishing Kafka events directly
 *  - Idempotency: honour X-Idempotency-Key to deduplicate duplicate POSTs
 *  - Saga compensation: save PendingStockRestore when circuit breaker fires on restoreStock
 *
 * Resilience4j:
 *  - fetchUser() / fetchProduct() — wrapped with @CircuitBreaker + @Retry
 *  - Self-injection via @Lazy to allow AOP proxying of these methods
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    // ── Valid state transitions ───────────────────────────────────────────────
    private static final Map<OrderStatus, Set<OrderStatus>> VALID_TRANSITIONS;

    static {
        Map<OrderStatus, Set<OrderStatus>> map = new EnumMap<>(OrderStatus.class);
        map.put(OrderStatus.PENDING,           EnumSet.of(OrderStatus.CONFIRMED, OrderStatus.PAYMENT_FAILED, OrderStatus.CANCELLED));
        map.put(OrderStatus.PAYMENT_FAILED,    EnumSet.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED));
        map.put(OrderStatus.CONFIRMED,         EnumSet.of(OrderStatus.PROCESSING, OrderStatus.CANCELLED));
        map.put(OrderStatus.PROCESSING,        EnumSet.of(OrderStatus.SHIPPED, OrderStatus.CANCELLED));
        map.put(OrderStatus.SHIPPED,           EnumSet.of(OrderStatus.OUT_FOR_DELIVERY, OrderStatus.DELIVERED));
        map.put(OrderStatus.OUT_FOR_DELIVERY,  EnumSet.of(OrderStatus.DELIVERED));
        map.put(OrderStatus.DELIVERED,         EnumSet.of(OrderStatus.RETURN_REQUESTED));
        map.put(OrderStatus.RETURN_REQUESTED,  EnumSet.of(OrderStatus.RETURNED));
        map.put(OrderStatus.RETURNED,          EnumSet.of(OrderStatus.REFUNDED));
        map.put(OrderStatus.CANCELLED,         EnumSet.noneOf(OrderStatus.class));
        map.put(OrderStatus.REFUNDED,          EnumSet.noneOf(OrderStatus.class));
        VALID_TRANSITIONS = Collections.unmodifiableMap(map);
    }

    private static final BigDecimal TAX_RATE           = new BigDecimal("0.18");
    private static final BigDecimal SHIPPING_FEE        = new BigDecimal("50.00");
    private static final BigDecimal FREE_SHIPPING_ABOVE = new BigDecimal("500.00");

    private final OrderRepository               orderRepository;
    private final OrderStatusHistoryRepository  historyRepository;
    private final OutboxEventRepository         outboxEventRepository;
    private final IdempotencyKeyRepository      idempotencyKeyRepository;
    private final PendingStockRestoreRepository pendingStockRestoreRepository;
    private final OrderMapper                   orderMapper;
    private final UserServiceClient             userServiceClient;
    private final ProductServiceClient          productServiceClient;
    private final OrderMetrics                  orderMetrics;
    private final CouponService                 couponService;

    /**
     * Self-reference injected lazily so Spring can apply AOP proxies for
     * @CircuitBreaker / @Retry on fetchUser() and fetchProduct().
     */
    @Autowired
    @Lazy
    private OrderService self;

    // ═══════════════════════════════════════════════════════════════════════════
    // READ OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    @Cacheable(value = "orders", key = "#id")
    public OrderResponse getById(Long id) {
        Order order = findOrderById(id);
        return orderMapper.toResponse(order);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "ordersPage",
               key = "'all:' + #pageable.pageNumber + ':' + #pageable.pageSize + ':' + #pageable.sort")
    public Page<OrderResponse> getAll(Pageable pageable) {
        return orderRepository.findAll(pageable).map(orderMapper::toResponse);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "ordersPage",
               key = "'user:' + #userId + ':' + #pageable.pageNumber + ':' + #pageable.pageSize")
    public Page<OrderResponse> getByUserId(Long userId, Pageable pageable) {
        return orderRepository.findByUserId(userId, pageable).map(orderMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public OrderResponse getByOrderNumber(String orderNumber) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "orderNumber", orderNumber));
        return orderMapper.toResponse(order);
    }

    @Transactional(readOnly = true)
    public List<OrderItemResponse> getItems(Long id) {
        Order order = findOrderById(id);
        return order.getItems().stream().map(orderMapper::toItemResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<OrderStatusHistoryResponse> getHistory(Long id) {
        findOrderById(id);
        return historyRepository.findByOrderIdOrderByChangedAtAsc(id)
                .stream().map(orderMapper::toHistoryResponse).toList();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PLACE ORDER
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Creates a new order.
     *
     * Idempotency: if idempotencyKey is non-null and was already used, the original
     * order is returned immediately without any side effects.
     *
     * Outbox: instead of publishing to Kafka directly, an outbox record is saved in
     * the same DB transaction. OutboxPoller relays it to Kafka asynchronously,
     * guaranteeing at-least-once delivery even when the broker is temporarily down.
     */
    @Transactional
    @CacheEvict(value = "ordersPage", allEntries = true)
    public OrderResponse create(OrderRequest request, String placedBy, String idempotencyKey) {
        // ── Idempotency check ────────────────────────────────────────────────
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<IdempotencyKey> existing = idempotencyKeyRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                log.info("Duplicate request detected for idempotency key '{}', returning existing order {}",
                        idempotencyKey, existing.get().getOrderId());
                return getById(existing.get().getOrderId());
            }
        }

        log.info("Placing order for userId={} by={}", request.getUserId(), placedBy);

        // 1. Validate user and get email via user-service
        UserDto user = self.fetchUser(request.getUserId());

        // 2. Validate products, snapshot name/sku/price, check stock availability
        List<ResolvedItem> resolvedItems = new ArrayList<>();
        for (OrderRequest.OrderItemRequest itemReq : request.getItems()) {
            ProductDto product = self.fetchProduct(itemReq.getProductId());
            if (product.getStockQuantity() == null || product.getStockQuantity() < itemReq.getQuantity()) {
                throw new InsufficientStockException(
                        product.getId(),
                        itemReq.getQuantity(),
                        product.getStockQuantity() != null ? product.getStockQuantity() : 0);
            }
            resolvedItems.add(new ResolvedItem(product, itemReq.getQuantity()));
        }

        // 3. Calculate pricing
        BigDecimal subtotal = resolvedItems.stream()
                .map(ri -> ri.product.getPrice().multiply(BigDecimal.valueOf(ri.quantity)))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal taxAmount      = subtotal.multiply(TAX_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal shippingAmount = subtotal.compareTo(FREE_SHIPPING_ABOVE) > 0
                ? BigDecimal.ZERO : SHIPPING_FEE;

        // 4. Apply coupon discount (if provided)
        BigDecimal discountAmount = BigDecimal.ZERO;
        if (request.getCouponCode() != null && !request.getCouponCode().isBlank()) {
            discountAmount = couponService.validateAndApply(request.getCouponCode(), subtotal);
        }

        BigDecimal totalAmount = subtotal.add(taxAmount).add(shippingAmount).subtract(discountAmount)
                .setScale(2, RoundingMode.HALF_UP);

        // 5. Deduct stock for each item
        for (ResolvedItem ri : resolvedItems) {
            self.deductProductStock(ri.product.getId(), ri.quantity);
        }

        // 6. Build Order with a temporary order number (replaced after ID is generated)
        OrderRequest.ShippingAddressRequest addr = request.getShippingAddress();
        Order order = Order.builder()
                .orderNumber("TMP-" + UUID.randomUUID())
                .userId(request.getUserId())
                .userEmail(user.getEmail())
                .status(OrderStatus.PENDING)
                .paymentStatus(PaymentStatus.PENDING)
                .paymentMethod(request.getPaymentMethod())
                .subtotal(subtotal)
                .taxAmount(taxAmount)
                .shippingAmount(shippingAmount)
                .discountAmount(discountAmount)
                .totalAmount(totalAmount)
                .couponCode(request.getCouponCode())
                .notes(request.getNotes())
                .shippingFullName(addr.getFullName())
                .shippingPhone(addr.getPhone())
                .shippingStreet(addr.getStreet())
                .shippingCity(addr.getCity())
                .shippingState(addr.getState())
                .shippingZip(addr.getZip())
                .shippingCountry(addr.getCountry())
                .items(new ArrayList<>())
                .build();

        for (ResolvedItem ri : resolvedItems) {
            BigDecimal itemTotal = ri.product.getPrice()
                    .multiply(BigDecimal.valueOf(ri.quantity))
                    .setScale(2, RoundingMode.HALF_UP);
            OrderItem item = OrderItem.builder()
                    .order(order)
                    .productId(ri.product.getId())
                    .productName(ri.product.getName())
                    .productSku(ri.product.getSku())
                    .quantity(ri.quantity)
                    .unitPrice(ri.product.getPrice())
                    .totalPrice(itemTotal)
                    .build();
            order.getItems().add(item);
        }

        Order saved = orderRepository.saveAndFlush(order);

        // 7. Replace temp order number with readable sequential number
        saved.setOrderNumber("ORD-" + Year.now().getValue() + "-" + String.format("%05d", saved.getId()));
        saved = orderRepository.saveAndFlush(saved);

        appendHistory(saved, null, OrderStatus.PENDING, "Order placed", placedBy);

        // 8. Outbox — write event in same transaction instead of publishing directly
        saveToOutbox(saved.getId(), "ORDER_CREATED", null);

        // 9. Store idempotency key so duplicate requests return this order
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            idempotencyKeyRepository.save(IdempotencyKey.builder()
                    .idempotencyKey(idempotencyKey)
                    .orderId(saved.getId())
                    .build());
        }

        orderMetrics.incrementCreated();
        orderMetrics.recordAmount(saved.getTotalAmount().doubleValue());
        orderMetrics.recordTransition(null, OrderStatus.PENDING.name());
        log.info("Order {} created successfully (total={})", saved.getOrderNumber(), saved.getTotalAmount());
        return orderMapper.toResponse(saved);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // STATUS TRANSITIONS
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "orders",     key = "#id"),
        @CacheEvict(value = "ordersPage", allEntries = true)
    })
    public OrderResponse confirmPayment(Long id, PaymentConfirmRequest req, String changedBy) {
        Order order = findOrderById(id);
        OrderStatus from = order.getStatus();
        validateTransition(order, OrderStatus.CONFIRMED);

        order.setStatus(OrderStatus.CONFIRMED);
        order.setPaymentStatus(PaymentStatus.PAID);
        order.setPaymentReference(req.getPaymentReference());
        order.setConfirmedAt(LocalDateTime.now());

        Order saved = orderRepository.saveAndFlush(order);
        appendHistory(saved, from, OrderStatus.CONFIRMED, req.getNotes(), changedBy);
        saveToOutbox(saved.getId(), "ORDER_CONFIRMED", null);
        orderMetrics.recordTransition(from.name(), OrderStatus.CONFIRMED.name());
        return orderMapper.toResponse(saved);
    }

    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "orders",     key = "#id"),
        @CacheEvict(value = "ordersPage", allEntries = true)
    })
    public OrderResponse processOrder(Long id, StatusUpdateRequest req, String changedBy) {
        Order order = findOrderById(id);
        validateTransition(order, OrderStatus.PROCESSING);

        order.setStatus(OrderStatus.PROCESSING);
        Order saved = orderRepository.saveAndFlush(order);
        appendHistory(saved, OrderStatus.CONFIRMED, OrderStatus.PROCESSING, req.getReason(), changedBy);
        saveToOutbox(saved.getId(), "ORDER_PROCESSING", null);
        orderMetrics.recordTransition(OrderStatus.CONFIRMED.name(), OrderStatus.PROCESSING.name());
        return orderMapper.toResponse(saved);
    }

    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "orders",     key = "#id"),
        @CacheEvict(value = "ordersPage", allEntries = true)
    })
    public OrderResponse shipOrder(Long id, ShipRequest req, String changedBy) {
        Order order = findOrderById(id);
        validateTransition(order, OrderStatus.SHIPPED);

        order.setStatus(OrderStatus.SHIPPED);
        order.setTrackingNumber(req.getTrackingNumber());
        order.setCourierName(req.getCourierName());
        order.setShippedAt(LocalDateTime.now());

        Order saved = orderRepository.saveAndFlush(order);
        appendHistory(saved, OrderStatus.PROCESSING, OrderStatus.SHIPPED, req.getNotes(), changedBy);
        saveToOutbox(saved.getId(), "ORDER_SHIPPED", null);
        orderMetrics.recordTransition(OrderStatus.PROCESSING.name(), OrderStatus.SHIPPED.name());
        return orderMapper.toResponse(saved);
    }

    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "orders",     key = "#id"),
        @CacheEvict(value = "ordersPage", allEntries = true)
    })
    public OrderResponse markOutForDelivery(Long id, StatusUpdateRequest req, String changedBy) {
        Order order = findOrderById(id);
        validateTransition(order, OrderStatus.OUT_FOR_DELIVERY);

        order.setStatus(OrderStatus.OUT_FOR_DELIVERY);
        Order saved = orderRepository.saveAndFlush(order);
        appendHistory(saved, OrderStatus.SHIPPED, OrderStatus.OUT_FOR_DELIVERY, req.getReason(), changedBy);
        saveToOutbox(saved.getId(), "ORDER_OUT_FOR_DELIVERY", null);
        orderMetrics.recordTransition(OrderStatus.SHIPPED.name(), OrderStatus.OUT_FOR_DELIVERY.name());
        return orderMapper.toResponse(saved);
    }

    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "orders",     key = "#id"),
        @CacheEvict(value = "ordersPage", allEntries = true)
    })
    public OrderResponse deliverOrder(Long id, StatusUpdateRequest req, String changedBy) {
        Order order = findOrderById(id);
        OrderStatus from = order.getStatus();
        validateTransition(order, OrderStatus.DELIVERED);

        order.setStatus(OrderStatus.DELIVERED);
        order.setDeliveredAt(LocalDateTime.now());

        Order saved = orderRepository.saveAndFlush(order);
        appendHistory(saved, from, OrderStatus.DELIVERED, req.getReason(), changedBy);
        saveToOutbox(saved.getId(), "ORDER_DELIVERED", null);
        orderMetrics.recordTransition(from.name(), OrderStatus.DELIVERED.name());
        return orderMapper.toResponse(saved);
    }

    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "orders",     key = "#id"),
        @CacheEvict(value = "ordersPage", allEntries = true)
    })
    public OrderResponse cancelOrder(Long id, StatusUpdateRequest req, String changedBy, boolean isAdmin) {
        Order order = findOrderById(id);
        OrderStatus from = order.getStatus();

        if (from == OrderStatus.PROCESSING && !isAdmin) {
            throw new AccessDeniedException("Only admins can cancel orders in PROCESSING state");
        }

        validateTransition(order, OrderStatus.CANCELLED);

        // Saga compensation: restore stock for items whose stock was already deducted
        if (from == OrderStatus.CONFIRMED || from == OrderStatus.PROCESSING) {
            for (OrderItem item : order.getItems()) {
                self.restoreProductStock(id, item.getProductId(), item.getQuantity());
            }
        }

        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledAt(LocalDateTime.now());

        Order saved = orderRepository.saveAndFlush(order);
        appendHistory(saved, from, OrderStatus.CANCELLED, req.getReason(), changedBy);
        saveToOutbox(saved.getId(), "ORDER_CANCELLED", req.getReason());
        orderMetrics.incrementCancelled();
        orderMetrics.recordTransition(from.name(), OrderStatus.CANCELLED.name());
        return orderMapper.toResponse(saved);
    }

    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "orders",     key = "#id"),
        @CacheEvict(value = "ordersPage", allEntries = true)
    })
    public OrderResponse requestReturn(Long id, ReturnRequest req, String changedBy) {
        Order order = findOrderById(id);
        validateTransition(order, OrderStatus.RETURN_REQUESTED);

        order.setStatus(OrderStatus.RETURN_REQUESTED);
        Order saved = orderRepository.saveAndFlush(order);
        appendHistory(saved, OrderStatus.DELIVERED, OrderStatus.RETURN_REQUESTED, req.getReason(), changedBy);
        saveToOutbox(saved.getId(), "ORDER_RETURN_REQUESTED", req.getReason());
        return orderMapper.toResponse(saved);
    }

    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "orders",     key = "#id"),
        @CacheEvict(value = "ordersPage", allEntries = true)
    })
    public OrderResponse updateStatus(Long id, OrderStatus newStatus, StatusUpdateRequest req, String changedBy) {
        Order order = findOrderById(id);
        OrderStatus from = order.getStatus();
        validateTransition(order, newStatus);

        order.setStatus(newStatus);
        setTimestampForStatus(order, newStatus);

        Order saved = orderRepository.saveAndFlush(order);
        appendHistory(saved, from, newStatus, req.getReason(), changedBy);
        saveToOutbox(saved.getId(), "ORDER_" + newStatus.name(), req.getReason());
        return orderMapper.toResponse(saved);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // RESILIENCE4J-WRAPPED FEIGN CALLS  (called via self-proxy for AOP to work)
    // ═══════════════════════════════════════════════════════════════════════════

    @CircuitBreaker(name = "user-service", fallbackMethod = "fetchUserFallback")
    @Retry(name = "user-service")
    public UserDto fetchUser(Long userId) {
        return userServiceClient.getUser(userId);
    }

    public UserDto fetchUserFallback(Long userId, Exception ex) {
        log.error("user-service unavailable while fetching userId={}: {}", userId, ex.getMessage());
        throw new ResourceNotFoundException(
                "user-service is unavailable. Cannot validate user " + userId + ". Please retry.");
    }

    @CircuitBreaker(name = "product-service", fallbackMethod = "fetchProductFallback")
    @Retry(name = "product-service")
    public ProductDto fetchProduct(Long productId) {
        return productServiceClient.getProduct(productId);
    }

    public ProductDto fetchProductFallback(Long productId, Exception ex) {
        log.error("product-service unavailable while fetching productId={}: {}", productId, ex.getMessage());
        throw new ResourceNotFoundException(
                "product-service is unavailable. Cannot validate product " + productId + ". Please retry.");
    }

    @CircuitBreaker(name = "product-service", fallbackMethod = "deductStockFallback")
    @Retry(name = "product-service")
    public void deductProductStock(Long productId, Integer quantity) {
        productServiceClient.deductStock(productId, new StockRequest(quantity));
    }

    public void deductStockFallback(Long productId, Integer quantity, Exception ex) {
        log.error("product-service unavailable during stock deduction for productId={}: {}", productId, ex.getMessage());
        throw new InsufficientStockException(
                "product-service is unavailable. Cannot deduct stock for product " + productId + ". Please retry.");
    }

    /**
     * Restores stock after cancellation. When the circuit breaker opens (product-service down),
     * the fallback writes a PendingStockRestore record so StockCompensationScheduler can retry.
     * The orderId parameter is passed so the compensation record is fully traceable.
     */
    @CircuitBreaker(name = "product-service", fallbackMethod = "restoreStockFallback")
    @Retry(name = "product-service")
    public void restoreProductStock(Long orderId, Long productId, Integer quantity) {
        productServiceClient.restoreStock(productId, new StockRequest(quantity));
    }

    public void restoreStockFallback(Long orderId, Long productId, Integer quantity, Exception ex) {
        log.warn("product-service unavailable during stock restore for productId={} orderId={}. " +
                 "Saving compensation record for retry. Error: {}", productId, orderId, ex.getMessage());
        pendingStockRestoreRepository.save(PendingStockRestore.builder()
                .orderId(orderId)
                .productId(productId)
                .quantity(quantity)
                .reason("Circuit breaker open: " + truncate(ex.getMessage(), 200))
                .status(CompensationStatus.PENDING)
                .retryCount(0)
                .build());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    private Order findOrderById(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));
    }

    private void validateTransition(Order order, OrderStatus target) {
        Set<OrderStatus> allowed = VALID_TRANSITIONS.getOrDefault(order.getStatus(), Set.of());
        if (!allowed.contains(target)) {
            throw new InvalidOrderTransitionException(order.getStatus(), target);
        }
    }

    private void appendHistory(Order order, OrderStatus from, OrderStatus to, String reason, String changedBy) {
        OrderStatusHistory entry = OrderStatusHistory.builder()
                .order(order)
                .fromStatus(from != null ? from.name() : null)
                .toStatus(to.name())
                .reason(reason)
                .changedBy(changedBy)
                .build();
        historyRepository.save(entry);
    }

    /** Writes an outbox record in the caller's transaction — relayed to Kafka by OutboxPoller. */
    private void saveToOutbox(Long orderId, String eventType, String reason) {
        outboxEventRepository.save(OutboxEvent.builder()
                .orderId(orderId)
                .eventType(eventType)
                .reason(reason)
                .status(OutboxStatus.PENDING)
                .retryCount(0)
                .build());
    }

    private void setTimestampForStatus(Order order, OrderStatus status) {
        LocalDateTime now = LocalDateTime.now();
        switch (status) {
            case CONFIRMED      -> order.setConfirmedAt(now);
            case SHIPPED        -> order.setShippedAt(now);
            case DELIVERED      -> order.setDeliveredAt(now);
            case CANCELLED      -> order.setCancelledAt(now);
            default             -> { /* no timestamp for other states */ }
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    private record ResolvedItem(ProductDto product, int quantity) {}
}

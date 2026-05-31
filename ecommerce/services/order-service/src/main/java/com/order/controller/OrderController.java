package com.order.controller;

import com.order.dto.request.OrderRequest;
import com.order.dto.request.PaymentConfirmRequest;
import com.order.dto.request.ReturnRequest;
import com.order.dto.request.ShipRequest;
import com.order.dto.request.StatusUpdateRequest;
import com.order.dto.response.OrderItemResponse;
import com.order.dto.response.OrderResponse;
import com.order.dto.response.OrderStatusHistoryResponse;
import com.order.enums.OrderStatus;
import com.order.exception.ResourceNotFoundException;
import com.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for all order endpoints (15 total).
 *
 * Authorization layers:
 *  - Gateway validates JWT and enforces role access before request reaches here.
 *  - SecurityConfig.GatewaySecretFilter blocks non-gateway calls.
 *  - @PreAuthorize / inline role checks enforce fine-grained per-endpoint access.
 *  - USER-role endpoints additionally check order ownership (order.userId == JWT userId).
 */
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Order Management", description = "APIs for placing and managing e-commerce orders")
public class OrderController {

    private final OrderService orderService;

    // ══════════════════════════════════════════════════════════════════════════
    // READ ENDPOINTS
    // ══════════════════════════════════════════════════════════════════════════

    @Operation(summary = "Get all orders (paginated)",
               description = "Returns all orders across all users. ADMIN only.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Paginated order list"),
        @ApiResponse(responseCode = "403", description = "Forbidden — requires ADMIN role")
    })
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Page<OrderResponse> getAll(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return orderService.getAll(pageable);
    }

    @Operation(summary = "Get my orders",
               description = "Returns the authenticated user's own orders, paginated.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Paginated order list"),
        @ApiResponse(responseCode = "403", description = "Forbidden — requires USER role")
    })
    @GetMapping("/my")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public Page<OrderResponse> getMyOrders(
            Authentication authentication,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Long userId = requireUserId(authentication);
        return orderService.getByUserId(userId, pageable);
    }

    @Operation(summary = "Get order by ID",
               description = "Returns full order details. USER may only access their own orders; ADMIN may access any.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Order found"),
        @ApiResponse(responseCode = "403", description = "Forbidden — not your order"),
        @ApiResponse(responseCode = "404", description = "Order not found")
    })
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<OrderResponse> getById(
            @PathVariable Long id,
            Authentication authentication) {
        OrderResponse order = orderService.getById(id);
        checkOwnership(order, authentication);
        return ResponseEntity.ok(order);
    }

    @Operation(summary = "Get order by order number",
               description = "Returns order details by human-readable order number (e.g. ORD-2026-00042).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Order found"),
        @ApiResponse(responseCode = "403", description = "Forbidden — not your order"),
        @ApiResponse(responseCode = "404", description = "Order not found")
    })
    @GetMapping("/number/{orderNumber}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<OrderResponse> getByOrderNumber(
            @PathVariable String orderNumber,
            Authentication authentication) {
        OrderResponse order = orderService.getByOrderNumber(orderNumber);
        checkOwnership(order, authentication);
        return ResponseEntity.ok(order);
    }

    @Operation(summary = "Get order line items",
               description = "Returns the items (products) for a specific order.")
    @ApiResponse(responseCode = "200", description = "Item list")
    @GetMapping("/{id}/items")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<List<OrderItemResponse>> getItems(
            @PathVariable Long id,
            Authentication authentication) {
        OrderResponse order = orderService.getById(id);
        checkOwnership(order, authentication);
        return ResponseEntity.ok(orderService.getItems(id));
    }

    @Operation(summary = "Get order status history",
               description = "Returns the full audit trail of status transitions for an order.")
    @ApiResponse(responseCode = "200", description = "Status history list")
    @GetMapping("/{id}/history")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<List<OrderStatusHistoryResponse>> getHistory(
            @PathVariable Long id,
            Authentication authentication) {
        OrderResponse order = orderService.getById(id);
        checkOwnership(order, authentication);
        return ResponseEntity.ok(orderService.getHistory(id));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PLACE ORDER
    // ══════════════════════════════════════════════════════════════════════════

    @Operation(summary = "Place a new order",
               description = "Creates a new order in PENDING state. Validates user and products via Feign, " +
                             "deducts stock, calculates pricing, and publishes ORDER_CREATED to Kafka.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Order created"),
        @ApiResponse(responseCode = "400", description = "Validation error"),
        @ApiResponse(responseCode = "404", description = "User or product not found"),
        @ApiResponse(responseCode = "409", description = "Insufficient stock")
    })
    @PostMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<OrderResponse> placeOrder(
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody OrderRequest request,
            Authentication authentication) {
        String placedBy = authentication.getName();
        OrderResponse created = orderService.create(request, placedBy, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ADMIN STATUS TRANSITIONS
    // ══════════════════════════════════════════════════════════════════════════

    @Operation(summary = "Confirm payment (PENDING → CONFIRMED)",
               description = "Marks payment as received and transitions order to CONFIRMED. ADMIN only.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Order confirmed"),
        @ApiResponse(responseCode = "400", description = "Invalid transition"),
        @ApiResponse(responseCode = "404", description = "Order not found")
    })
    @PostMapping("/{id}/confirm-payment")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<OrderResponse> confirmPayment(
            @PathVariable Long id,
            @RequestBody(required = false) PaymentConfirmRequest req,
            Authentication authentication) {
        if (req == null) req = new PaymentConfirmRequest();
        return ResponseEntity.ok(orderService.confirmPayment(id, req, authentication.getName()));
    }

    @Operation(summary = "Move order to PROCESSING (CONFIRMED → PROCESSING)",
               description = "Indicates the order has been picked up for fulfilment. ADMIN only.")
    @PostMapping("/{id}/process")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<OrderResponse> processOrder(
            @PathVariable Long id,
            @RequestBody(required = false) StatusUpdateRequest req,
            Authentication authentication) {
        if (req == null) req = new StatusUpdateRequest();
        return ResponseEntity.ok(orderService.processOrder(id, req, authentication.getName()));
    }

    @Operation(summary = "Mark order as SHIPPED (PROCESSING → SHIPPED)",
               description = "Records tracking number and courier, publishes ORDER_SHIPPED event. ADMIN only.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Order shipped"),
        @ApiResponse(responseCode = "400", description = "Tracking number missing or invalid transition")
    })
    @PostMapping("/{id}/ship")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<OrderResponse> shipOrder(
            @PathVariable Long id,
            @Valid @RequestBody ShipRequest req,
            Authentication authentication) {
        return ResponseEntity.ok(orderService.shipOrder(id, req, authentication.getName()));
    }

    @Operation(summary = "Mark order as OUT FOR DELIVERY (SHIPPED → OUT_FOR_DELIVERY)",
               description = "Indicates the courier has the package and is en route. ADMIN only.")
    @PostMapping("/{id}/out-for-delivery")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<OrderResponse> markOutForDelivery(
            @PathVariable Long id,
            @RequestBody(required = false) StatusUpdateRequest req,
            Authentication authentication) {
        if (req == null) req = new StatusUpdateRequest();
        return ResponseEntity.ok(orderService.markOutForDelivery(id, req, authentication.getName()));
    }

    @Operation(summary = "Mark order as DELIVERED (OUT_FOR_DELIVERY → DELIVERED)",
               description = "Confirms delivery. ADMIN only.")
    @PostMapping("/{id}/deliver")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<OrderResponse> deliverOrder(
            @PathVariable Long id,
            @RequestBody(required = false) StatusUpdateRequest req,
            Authentication authentication) {
        if (req == null) req = new StatusUpdateRequest();
        return ResponseEntity.ok(orderService.deliverOrder(id, req, authentication.getName()));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // USER + ADMIN TRANSITIONS
    // ══════════════════════════════════════════════════════════════════════════

    @Operation(summary = "Cancel an order",
               description = "Cancels the order. USER can cancel PENDING or CONFIRMED orders. " +
                             "ADMIN can additionally cancel PROCESSING orders. " +
                             "Stock is restored if it was already deducted (CONFIRMED or PROCESSING).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Order cancelled"),
        @ApiResponse(responseCode = "400", description = "Invalid transition (e.g. already shipped)"),
        @ApiResponse(responseCode = "403", description = "USER cannot cancel a PROCESSING order")
    })
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<OrderResponse> cancelOrder(
            @PathVariable Long id,
            @RequestBody(required = false) StatusUpdateRequest req,
            Authentication authentication) {
        if (req == null) req = new StatusUpdateRequest();
        OrderResponse order = orderService.getById(id);
        checkOwnership(order, authentication);
        boolean isAdmin = isAdmin(authentication);
        return ResponseEntity.ok(
                orderService.cancelOrder(id, req, authentication.getName(), isAdmin));
    }

    @Operation(summary = "Request a return (DELIVERED → RETURN_REQUESTED)",
               description = "USER initiates a return for a delivered order. " +
                             "Only the order's owner may request a return.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Return requested"),
        @ApiResponse(responseCode = "400", description = "Order not in DELIVERED state"),
        @ApiResponse(responseCode = "403", description = "Not your order")
    })
    @PostMapping("/{id}/return")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<OrderResponse> requestReturn(
            @PathVariable Long id,
            @Valid @RequestBody ReturnRequest req,
            Authentication authentication) {
        OrderResponse order = orderService.getById(id);
        checkOwnership(order, authentication);
        return ResponseEntity.ok(orderService.requestReturn(id, req, authentication.getName()));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // GENERIC ADMIN STATUS UPDATE
    // ══════════════════════════════════════════════════════════════════════════

    @Operation(summary = "Generic status update (ADMIN)",
               description = "Transitions the order to any valid next status. Validated against the state machine.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Status updated"),
        @ApiResponse(responseCode = "400", description = "Invalid transition"),
        @ApiResponse(responseCode = "404", description = "Order not found")
    })
    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<OrderResponse> updateStatus(
            @PathVariable Long id,
            @Parameter(description = "Target status") @RequestParam OrderStatus status,
            @RequestBody(required = false) StatusUpdateRequest req,
            Authentication authentication) {
        if (req == null) req = new StatusUpdateRequest();
        return ResponseEntity.ok(
                orderService.updateStatus(id, status, req, authentication.getName()));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Extracts the user's Long ID from the JWT's "user_db_id" custom claim.
     * If the claim is absent (e.g. Keycloak not yet configured with a custom mapper),
     * returns null and ownership checks are relaxed.
     *
     * Production setup: add a "User Attribute" Keycloak mapper that maps the
     * user attribute "user_db_id" (Long) into the access token.
     */
    private Long getUserIdFromJwt(Authentication authentication) {
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            Object claim = jwt.getClaim("user_db_id");
            if (claim instanceof Long l)       return l;
            if (claim instanceof Integer i)    return i.longValue();
            if (claim instanceof Number n)     return n.longValue();
        }
        return null;
    }

    /**
     * Asserts that the current user may access the given order.
     * ADMINs may access any order. USERs may only access their own.
     * Ownership is determined by userId (from JWT claim) or email fallback.
     */
    private void checkOwnership(OrderResponse order, Authentication authentication) {
        if (isAdmin(authentication)) return;

        Long jwtUserId = getUserIdFromJwt(authentication);
        if (jwtUserId != null) {
            if (!jwtUserId.equals(order.getUserId())) {
                throw new AccessDeniedException("You do not have permission to access order " + order.getId());
            }
            return;
        }

        // Fallback: compare preferred_username (email) with the order's stored email
        String username = authentication.getName();
        if (!username.equalsIgnoreCase(order.getUserEmail())) {
            throw new AccessDeniedException("You do not have permission to access order " + order.getId());
        }
    }

    /**
     * Requires a Long userId — throws if both JWT claim and email-based fallback are unavailable.
     * Used for "my orders" to scope the query to the caller.
     */
    private Long requireUserId(Authentication authentication) {
        Long userId = getUserIdFromJwt(authentication);
        if (userId != null) return userId;

        // Cannot determine userId without the JWT custom claim or user-service lookup.
        // Throw a meaningful error so the caller understands the configuration requirement.
        throw new ResourceNotFoundException(
                "Cannot determine your userId from the JWT. " +
                "Ensure Keycloak is configured with a 'user_db_id' attribute mapper, " +
                "or use GET /api/v1/orders?userId=<id> with ADMIN role.");
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}

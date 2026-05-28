package com.order.enums;

/**
 * Full lifecycle of an order.
 *
 * Valid transitions are enforced in OrderService.validateTransition():
 *
 *   PENDING           → CONFIRMED, PAYMENT_FAILED, CANCELLED
 *   PAYMENT_FAILED    → CONFIRMED, CANCELLED
 *   CONFIRMED         → PROCESSING, CANCELLED
 *   PROCESSING        → SHIPPED, CANCELLED  (ADMIN only)
 *   SHIPPED           → OUT_FOR_DELIVERY, DELIVERED
 *   OUT_FOR_DELIVERY  → DELIVERED
 *   DELIVERED         → RETURN_REQUESTED
 *   RETURN_REQUESTED  → RETURNED
 *   RETURNED          → REFUNDED
 *   CANCELLED         → (terminal)
 *   REFUNDED          → (terminal)
 */
public enum OrderStatus {
    PENDING,
    PAYMENT_FAILED,
    CONFIRMED,
    PROCESSING,
    SHIPPED,
    OUT_FOR_DELIVERY,
    DELIVERED,
    RETURN_REQUESTED,
    RETURNED,
    REFUNDED,
    CANCELLED
}

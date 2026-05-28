package com.order.entity;

import com.order.enums.OrderStatus;
import com.order.enums.PaymentMethod;
import com.order.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Core order aggregate root.
 *
 * Notes:
 *  - implements Serializable with serialVersionUID — required for Hazelcast Java serialization
 *  - @Version enables optimistic locking; client must echo version on updates
 *  - @OneToMany(cascade=ALL, orphanRemoval=true) manages items as part of this aggregate
 *  - Shipping address stored as flat columns (no separate table) for simplicity
 *  - createdAt set in @PrePersist; timestamp fields (confirmedAt, etc.) set by service on transitions
 */
@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_number", nullable = false, unique = true, length = 20)
    private String orderNumber;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "user_email", nullable = false)
    private String userEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 20)
    private PaymentStatus paymentStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 30)
    private PaymentMethod paymentMethod;

    @Column(name = "payment_reference", length = 100)
    private String paymentReference;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "tax_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "shipping_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal shippingAmount;

    @Column(name = "discount_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "coupon_code", length = 50)
    private String couponCode;

    @Column(name = "tracking_number", length = 100)
    private String trackingNumber;

    @Column(name = "courier_name", length = 100)
    private String courierName;

    @Column(length = 500)
    private String notes;

    // ── Shipping address (flat columns, no join table) ────────────────────────
    @Column(name = "shipping_full_name", length = 200)
    private String shippingFullName;

    @Column(name = "shipping_phone", length = 20)
    private String shippingPhone;

    @Column(name = "shipping_street", length = 300)
    private String shippingStreet;

    @Column(name = "shipping_city", length = 100)
    private String shippingCity;

    @Column(name = "shipping_state", length = 100)
    private String shippingState;

    @Column(name = "shipping_zip", length = 20)
    private String shippingZip;

    @Column(name = "shipping_country", length = 100)
    private String shippingCountry;

    // ── Optimistic locking ────────────────────────────────────────────────────
    @Version
    @Column(nullable = false)
    private Long version;

    // ── Timestamps ────────────────────────────────────────────────────────────
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "shipped_at")
    private LocalDateTime shippedAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    // ── Line items (owned by this aggregate) ─────────────────────────────────
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = OrderStatus.PENDING;
        }
        if (paymentStatus == null) {
            paymentStatus = PaymentStatus.PENDING;
        }
    }
}

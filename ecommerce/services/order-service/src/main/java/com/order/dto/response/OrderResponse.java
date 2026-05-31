package com.order.dto.response;

import com.order.enums.OrderStatus;
import com.order.enums.PaymentMethod;
import com.order.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Full order response DTO — implements Serializable so Hazelcast can cache it
 * using Java serialization (configured in hazelcast.xml).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String orderNumber;
    private Long userId;
    private String userEmail;
    private OrderStatus status;
    private PaymentStatus paymentStatus;
    private PaymentMethod paymentMethod;

    private List<OrderItemResponse> items;

    private BigDecimal subtotal;
    private BigDecimal taxAmount;
    private BigDecimal shippingAmount;
    private BigDecimal discountAmount;
    private BigDecimal totalAmount;

    private String couponCode;
    private ShippingAddressResponse shippingAddress;
    private String trackingNumber;
    private String courierName;
    private String notes;

    /** Estimated delivery: 7 days from creation date (computed by mapper). */
    private LocalDate estimatedDelivery;

    private LocalDateTime createdAt;
    private LocalDateTime confirmedAt;
    private LocalDateTime shippedAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime cancelledAt;

    // ── Nested: shipping address ──────────────────────────────────────────────

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ShippingAddressResponse implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private String fullName;
        private String phone;
        private String street;
        private String city;
        private String state;
        private String zip;
        private String country;
    }
}

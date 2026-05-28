package com.order.dto.request;

import com.order.enums.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request body for placing a new order.
 *
 * userId: the caller's user-service Long ID.  The service validates it by calling
 * user-service via Feign, then cross-checks it against the JWT for ownership.
 * In production, configure a Keycloak attribute mapper to add "user_db_id" (Long)
 * to the JWT so the service can verify without an extra Feign call.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderRequest {

    @NotNull(message = "userId is required")
    private Long userId;

    @NotEmpty(message = "Order must contain at least one item")
    @Valid
    private List<OrderItemRequest> items;

    @NotNull(message = "Shipping address is required")
    @Valid
    private ShippingAddressRequest shippingAddress;

    @NotNull(message = "Payment method is required")
    private PaymentMethod paymentMethod;

    private String couponCode;

    private String notes;

    // ── Nested: line-item request ─────────────────────────────────────────────

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemRequest {

        @NotNull(message = "productId is required")
        private Long productId;

        @NotNull(message = "quantity is required")
        @Min(value = 1, message = "quantity must be at least 1")
        private Integer quantity;
    }

    // ── Nested: shipping address ──────────────────────────────────────────────

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShippingAddressRequest {

        @NotBlank(message = "Full name is required")
        private String fullName;

        @NotBlank(message = "Phone number is required")
        private String phone;

        @NotBlank(message = "Street address is required")
        private String street;

        @NotBlank(message = "City is required")
        private String city;

        @NotBlank(message = "State is required")
        private String state;

        @NotBlank(message = "ZIP code is required")
        private String zip;

        @NotBlank(message = "Country is required")
        private String country;
    }
}

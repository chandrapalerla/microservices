package com.order.dto.request;

import com.order.enums.DiscountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
public class CouponRequest {

    @NotBlank(message = "Coupon code is required")
    private String code;

    private String description;

    @NotNull(message = "discountType is required")
    private DiscountType discountType;

    @NotNull(message = "discountValue is required")
    @DecimalMin(value = "0.01", message = "discountValue must be positive")
    private BigDecimal discountValue;

    /** Cap on PERCENTAGE discounts. Null = uncapped. */
    private BigDecimal maxDiscount;

    private BigDecimal minOrderAmount;

    @Min(value = 1, message = "maxUses must be at least 1")
    private Integer maxUses;

    @NotNull(message = "validFrom is required")
    private Instant validFrom;

    @NotNull(message = "validUntil is required")
    private Instant validUntil;
}

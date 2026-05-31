package com.order.dto.response;

import com.order.enums.DiscountType;

import java.math.BigDecimal;
import java.time.Instant;

public record CouponResponse(
        Long       id,
        String     code,
        String     description,
        DiscountType discountType,
        BigDecimal discountValue,
        BigDecimal maxDiscount,
        BigDecimal minOrderAmount,
        int        maxUses,
        int        timesUsed,
        Instant    validFrom,
        Instant    validUntil,
        boolean    active
) {}

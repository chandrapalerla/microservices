package com.order.service;

import com.order.dto.request.CouponRequest;
import com.order.dto.response.CouponResponse;
import com.order.entity.Coupon;
import com.order.enums.DiscountType;
import com.order.exception.CouponException;
import com.order.exception.ResourceNotFoundException;
import com.order.repository.CouponRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class CouponService {

    private final CouponRepository couponRepository;

    /**
     * Validates the coupon and returns the discount amount to subtract from the order subtotal.
     * Increments times_used atomically — must be called within the same transaction as order creation.
     */
    @Transactional
    public BigDecimal validateAndApply(String code, BigDecimal subtotal) {
        Coupon coupon = couponRepository.findByCodeIgnoreCase(code)
                .orElseThrow(() -> new CouponException("Coupon code '" + code + "' not found"));

        Instant now = Instant.now();

        if (!coupon.isActive()) {
            throw new CouponException("Coupon '" + code + "' is no longer active");
        }
        if (now.isBefore(coupon.getValidFrom())) {
            throw new CouponException("Coupon '" + code + "' is not yet valid");
        }
        if (now.isAfter(coupon.getValidUntil())) {
            throw new CouponException("Coupon '" + code + "' has expired");
        }
        if (coupon.getTimesUsed() >= coupon.getMaxUses()) {
            throw new CouponException("Coupon '" + code + "' has reached its maximum usage limit");
        }
        if (subtotal.compareTo(coupon.getMinOrderAmount()) < 0) {
            throw new CouponException(
                    "Coupon '" + code + "' requires a minimum order of " + coupon.getMinOrderAmount());
        }

        BigDecimal discount;
        if (coupon.getDiscountType() == DiscountType.PERCENTAGE) {
            discount = subtotal.multiply(coupon.getDiscountValue())
                               .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            if (coupon.getMaxDiscount() != null) {
                discount = discount.min(coupon.getMaxDiscount());
            }
        } else {
            // FIXED — cap at subtotal so discount never exceeds order value
            discount = coupon.getDiscountValue().min(subtotal).setScale(2, RoundingMode.HALF_UP);
        }

        coupon.setTimesUsed(coupon.getTimesUsed() + 1);
        log.info("Coupon '{}' applied: discount={} on subtotal={}", code, discount, subtotal);
        return discount;
    }

    @Transactional
    public CouponResponse create(CouponRequest request) {
        if (couponRepository.findByCodeIgnoreCase(request.getCode()).isPresent()) {
            throw new CouponException("Coupon code '" + request.getCode() + "' already exists");
        }
        Coupon coupon = Coupon.builder()
                .code(request.getCode().toUpperCase())
                .description(request.getDescription())
                .discountType(request.getDiscountType())
                .discountValue(request.getDiscountValue())
                .maxDiscount(request.getMaxDiscount())
                .minOrderAmount(request.getMinOrderAmount() != null
                        ? request.getMinOrderAmount() : BigDecimal.ZERO)
                .maxUses(request.getMaxUses() != null ? request.getMaxUses() : 1)
                .timesUsed(0)
                .validFrom(request.getValidFrom())
                .validUntil(request.getValidUntil())
                .active(true)
                .build();
        return toResponse(couponRepository.save(coupon));
    }

    @Transactional(readOnly = true)
    public Page<CouponResponse> getAll(Pageable pageable) {
        return couponRepository.findAll(pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public CouponResponse getByCode(String code) {
        return couponRepository.findByCodeIgnoreCase(code)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Coupon", "code", code));
    }

    /**
     * Computes the discount amount for the given subtotal without consuming the coupon.
     * Validates all eligibility rules and throws CouponException on any failure.
     */
    @Transactional(readOnly = true)
    public BigDecimal previewDiscount(String code, BigDecimal subtotal) {
        Coupon coupon = couponRepository.findByCodeIgnoreCase(code)
                .orElseThrow(() -> new CouponException("Coupon code '" + code + "' not found"));

        Instant now = Instant.now();
        if (!coupon.isActive())                             throw new CouponException("Coupon '" + code + "' is no longer active");
        if (now.isBefore(coupon.getValidFrom()))            throw new CouponException("Coupon '" + code + "' is not yet valid");
        if (now.isAfter(coupon.getValidUntil()))            throw new CouponException("Coupon '" + code + "' has expired");
        if (coupon.getTimesUsed() >= coupon.getMaxUses())   throw new CouponException("Coupon '" + code + "' has reached its maximum usage limit");
        if (subtotal.compareTo(coupon.getMinOrderAmount()) < 0)
            throw new CouponException("Coupon '" + code + "' requires a minimum order of " + coupon.getMinOrderAmount());

        if (coupon.getDiscountType() == DiscountType.PERCENTAGE) {
            BigDecimal discount = subtotal.multiply(coupon.getDiscountValue())
                                         .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            return (coupon.getMaxDiscount() != null) ? discount.min(coupon.getMaxDiscount()) : discount;
        }
        return coupon.getDiscountValue().min(subtotal).setScale(2, RoundingMode.HALF_UP);
    }

    private CouponResponse toResponse(Coupon c) {
        return new CouponResponse(
                c.getId(), c.getCode(), c.getDescription(),
                c.getDiscountType(), c.getDiscountValue(), c.getMaxDiscount(),
                c.getMinOrderAmount(), c.getMaxUses(), c.getTimesUsed(),
                c.getValidFrom(), c.getValidUntil(), c.isActive());
    }
}

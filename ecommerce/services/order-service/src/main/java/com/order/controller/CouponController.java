package com.order.controller;

import com.order.dto.request.CouponRequest;
import com.order.dto.response.CouponResponse;
import com.order.service.CouponService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/coupons")
@RequiredArgsConstructor
@Tag(name = "Coupon Management", description = "APIs for creating and validating discount coupons")
public class CouponController {

    private final CouponService couponService;

    @Operation(summary = "Create a coupon (ADMIN only)")
    @ApiResponse(responseCode = "201", description = "Coupon created")
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CouponResponse> create(@Valid @RequestBody CouponRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(couponService.create(request));
    }

    @Operation(summary = "List all coupons (ADMIN only)")
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Page<CouponResponse> getAll(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return couponService.getAll(pageable);
    }

    @Operation(summary = "Get coupon by code (ADMIN only)")
    @GetMapping("/{code}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CouponResponse> getByCode(@PathVariable String code) {
        return ResponseEntity.ok(couponService.getByCode(code));
    }

    @Operation(summary = "Validate a coupon",
               description = "Checks if the coupon is valid for the given subtotal and returns the discount amount. " +
                             "Does NOT consume the coupon — call is idempotent.")
    @ApiResponse(responseCode = "200", description = "Coupon is valid; discount returned")
    @ApiResponse(responseCode = "400", description = "Coupon invalid, expired, or minimum order not met")
    @GetMapping("/{code}/validate")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<BigDecimal> validate(
            @PathVariable String code,
            @RequestParam BigDecimal subtotal) {
        // Read-only preview — computes actual discount for the subtotal, does NOT consume the coupon
        return ResponseEntity.ok(couponService.previewDiscount(code, subtotal));
    }
}

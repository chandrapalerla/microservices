package com.payment.controller;

import com.payment.dto.PaymentResponse;
import com.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Payment status and retry endpoints")
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping("/{orderId}")
    @Operation(summary = "Get payment status for an order")
    public ResponseEntity<PaymentResponse> getByOrderId(@PathVariable Long orderId) {
        return ResponseEntity.ok(paymentService.getByOrderId(orderId));
    }

    @PostMapping("/{orderId}/retry")
    @Operation(summary = "Manually retry a failed payment")
    public ResponseEntity<PaymentResponse> retry(@PathVariable Long orderId) {
        return ResponseEntity.ok(paymentService.retryPayment(orderId));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List all payments (admin only, paginated)")
    public ResponseEntity<Page<PaymentResponse>> getAll(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(paymentService.getAll(pageable));
    }
}

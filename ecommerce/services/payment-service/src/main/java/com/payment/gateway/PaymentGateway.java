package com.payment.gateway;

import com.payment.enums.PaymentMethod;
import com.payment.gateway.dto.GatewayChargeResult;

import java.math.BigDecimal;

/**
 * Strategy contract for all payment gateway implementations.
 *
 * Implementations:
 *   - CodGateway      — auto-confirms, no external API call
 *   - RazorpayGateway — real Razorpay API (enabled via property flag)
 *   - MockGateway     — @Profile("local") — always succeeds, simulates all methods
 */
public interface PaymentGateway {

    GatewayChargeResult charge(Long orderId, String orderNumber, BigDecimal amount,
                               String currency, PaymentMethod method);

    boolean supports(PaymentMethod method);
}

package com.payment.gateway;

import com.payment.enums.GatewayProvider;
import com.payment.enums.PaymentMethod;
import com.payment.gateway.dto.GatewayChargeResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

/**
 * Mock gateway active only on the "local" Spring profile.
 * Simulates all non-COD payment methods and always returns success.
 *
 * For testing failures locally, set the environment variable MOCK_PAYMENT_FAIL=true
 * or use a special amount trigger: any amount ending in .99 will simulate a failure.
 */
@Component
@Profile("local")
@Slf4j
public class MockGateway implements PaymentGateway {

    private static final Set<PaymentMethod> SUPPORTED = Set.of(
            PaymentMethod.CARD, PaymentMethod.CREDIT_CARD, PaymentMethod.DEBIT_CARD,
            PaymentMethod.UPI, PaymentMethod.NET_BANKING, PaymentMethod.WALLET
    );

    @Override
    public GatewayChargeResult charge(Long orderId, String orderNumber, BigDecimal amount,
                                      String currency, PaymentMethod method) {

        String mockTxnId = "mock_pay_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        // Simulate failure when MOCK_PAYMENT_FAIL env var is true OR amount ends in .99
        boolean simulateFailure = Boolean.parseBoolean(System.getenv("MOCK_PAYMENT_FAIL"))
                || (amount != null && amount.toPlainString().endsWith(".99"));

        if (simulateFailure) {
            log.info("[MOCK] Simulating payment FAILURE for orderId={} method={} amount={}",
                    orderId, method, amount);
            return GatewayChargeResult.builder()
                    .provider(GatewayProvider.MOCK)
                    .transactionId(mockTxnId)
                    .success(false)
                    .failureReason("Mock failure: payment declined (simulation)")
                    .build();
        }

        log.info("[MOCK] Payment SUCCESS for orderId={} method={} amount={} {} txn={}",
                orderId, method, amount, currency, mockTxnId);
        return GatewayChargeResult.builder()
                .provider(GatewayProvider.MOCK)
                .transactionId(mockTxnId)
                .success(true)
                .build();
    }

    @Override
    public boolean supports(PaymentMethod method) {
        return SUPPORTED.contains(method);
    }
}

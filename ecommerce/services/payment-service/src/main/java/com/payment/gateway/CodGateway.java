package com.payment.gateway;

import com.payment.enums.GatewayProvider;
import com.payment.enums.PaymentMethod;
import com.payment.gateway.dto.GatewayChargeResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Cash-on-delivery gateway — auto-confirms immediately, no external API call.
 * Always active regardless of profile.
 */
@Component
@Slf4j
public class CodGateway implements PaymentGateway {

    @Override
    public GatewayChargeResult charge(Long orderId, String orderNumber, BigDecimal amount,
                                      String currency, PaymentMethod method) {
        log.info("COD payment auto-confirmed for orderId={} orderNumber={} amount={} {}",
                orderId, orderNumber, amount, currency);
        return GatewayChargeResult.builder()
                .provider(GatewayProvider.COD)
                .transactionId("COD-" + orderId)
                .success(true)
                .build();
    }

    @Override
    public boolean supports(PaymentMethod method) {
        return method == PaymentMethod.CASH_ON_DELIVERY;
    }
}

package com.payment.gateway;

import com.payment.enums.GatewayProvider;
import com.payment.enums.PaymentMethod;
import com.payment.exception.PaymentGatewayException;
import com.payment.gateway.dto.GatewayChargeResult;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Production Razorpay gateway.
 *
 * Active when payment.gateway.razorpay.enabled=true (non-local environments).
 * Disabled in "local" profile — MockGateway handles non-COD methods there.
 *
 * Flow:
 *   1. Create a Razorpay Order (server-side) — returns razorpay_order_id
 *   2. Frontend renders Razorpay Checkout with the razorpay_order_id
 *   3. User completes payment — Razorpay calls webhook with payment.captured event
 *   4. WebhookController handles the async confirmation
 *
 * For the Choreography Saga, step 1 is what this method does.
 * The PAYMENT_COMPLETED event is published from WebhookController, not here.
 */
@Component
@ConditionalOnProperty(name = "payment.gateway.razorpay.enabled", havingValue = "true")
@Profile("!local")
@Slf4j
public class RazorpayGateway implements PaymentGateway {

    private static final Set<PaymentMethod> SUPPORTED = Set.of(
            PaymentMethod.CARD, PaymentMethod.CREDIT_CARD, PaymentMethod.DEBIT_CARD,
            PaymentMethod.UPI, PaymentMethod.NET_BANKING, PaymentMethod.WALLET
    );

    private final RazorpayClient client;

    public RazorpayGateway(
            @Value("${payment.gateway.razorpay.key-id}") String keyId,
            @Value("${payment.gateway.razorpay.key-secret}") String keySecret) throws RazorpayException {
        this.client = new RazorpayClient(keyId, keySecret);
        log.info("RazorpayGateway initialised with keyId={}", keyId);
    }

    @Override
    public GatewayChargeResult charge(Long orderId, String orderNumber, BigDecimal amount,
                                      String currency, PaymentMethod method) {
        try {
            JSONObject options = new JSONObject();
            // Razorpay requires amount in paise (₹1 = 100 paise)
            long amountPaise = amount.multiply(BigDecimal.valueOf(100)).longValueExact();
            options.put("amount", amountPaise);
            options.put("currency", currency);
            options.put("receipt", orderNumber);
            options.put("payment_capture", 1);  // auto-capture on payment success

            JSONObject notes = new JSONObject();
            notes.put("order_id", orderId);
            notes.put("order_number", orderNumber);
            options.put("notes", notes);

            com.razorpay.Order razorpayOrder = client.orders.create(options);
            String razorpayOrderId = razorpayOrder.get("id");

            log.info("Razorpay order created: razorpayOrderId={} orderId={} amount={}p",
                    razorpayOrderId, orderId, amountPaise);

            return GatewayChargeResult.builder()
                    .provider(GatewayProvider.RAZORPAY)
                    .transactionId(razorpayOrderId)
                    .success(true)
                    .build();

        } catch (RazorpayException ex) {
            log.error("Razorpay order creation failed for orderId={}: {}", orderId, ex.getMessage());
            throw new PaymentGatewayException("Razorpay error: " + ex.getMessage(), ex);
        }
    }

    @Override
    public boolean supports(PaymentMethod method) {
        return SUPPORTED.contains(method);
    }
}

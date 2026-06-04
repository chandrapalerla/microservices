package com.payment.dto;

import com.payment.enums.GatewayProvider;
import com.payment.enums.PaymentMethod;
import com.payment.enums.PaymentStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
public class PaymentResponse {

    private Long id;
    private Long orderId;
    private String orderNumber;
    private Long userId;
    private PaymentMethod paymentMethod;
    private GatewayProvider gatewayProvider;
    private String gatewayTxnId;
    private BigDecimal amount;
    private String currency;
    private PaymentStatus status;
    private String failureReason;
    private int retryCount;
    private Instant createdAt;
    private Instant updatedAt;
}

package com.payment.gateway.dto;

import com.payment.enums.GatewayProvider;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GatewayChargeResult {
    private GatewayProvider provider;
    private String transactionId;
    private boolean success;
    private String failureReason;
}

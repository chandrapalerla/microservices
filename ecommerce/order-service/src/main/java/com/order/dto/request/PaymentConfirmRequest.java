package com.order.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Confirms payment for a PENDING order, transitioning it to CONFIRMED. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentConfirmRequest {

    private String paymentReference;

    private String notes;
}

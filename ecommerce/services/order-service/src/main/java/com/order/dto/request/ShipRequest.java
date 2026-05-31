package com.order.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Marks an order as SHIPPED, capturing courier tracking details. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShipRequest {

    @NotBlank(message = "Tracking number is required when shipping an order")
    private String trackingNumber;

    private String courierName;

    private String notes;
}

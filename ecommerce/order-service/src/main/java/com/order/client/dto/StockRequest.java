package com.order.client.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Request body for deducting or restoring stock on product-service. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockRequest {
    private Integer quantity;
}

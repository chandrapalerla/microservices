package com.product.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Used for deduct-stock, restore-stock (delta) and set-stock (absolute) endpoints. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateStockRequest {

    @NotNull(message = "quantity is required")
    @Min(value = 1, message = "quantity must be at least 1")
    private Integer quantity;
}

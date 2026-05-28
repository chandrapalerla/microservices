package com.product.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Partial update — changes price and/or originalPrice (for sale pricing). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePriceRequest {

    @NotNull(message = "price is required")
    @DecimalMin(value = "0.01", message = "Price must be greater than 0")
    @Digits(integer = 8, fraction = 2)
    private BigDecimal price;

    /** Set to null to remove a sale price; set to a value > price to show a discount. */
    @DecimalMin(value = "0.01", message = "Original price must be greater than 0")
    @Digits(integer = 8, fraction = 2)
    private BigDecimal originalPrice;
}

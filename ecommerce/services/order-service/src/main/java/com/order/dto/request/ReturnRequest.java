package com.order.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Request to initiate a return on a DELIVERED order. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReturnRequest {

    @NotBlank(message = "Return reason is required")
    private String reason;

    private String description;

    /** Specific items to return; if null or empty the entire order is returned. */
    private List<ReturnItemRequest> items;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReturnItemRequest {

        @NotNull(message = "orderItemId is required")
        private Long orderItemId;

        @NotNull(message = "quantity is required")
        @Min(value = 1, message = "return quantity must be at least 1")
        private Integer quantity;

        private String reason;
    }
}

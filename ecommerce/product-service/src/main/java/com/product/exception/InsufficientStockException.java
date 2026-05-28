package com.product.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(Long productId, int requested, int available) {
        super("Insufficient stock for product " + productId
                + ": requested " + requested + ", available " + available);
    }

    public InsufficientStockException(String message) {
        super(message);
    }
}

package com.order.exception;

import com.order.enums.OrderStatus;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidOrderTransitionException extends RuntimeException {

    public InvalidOrderTransitionException(OrderStatus from, OrderStatus to) {
        super("Invalid order status transition from " + from + " to " + to);
    }

    public InvalidOrderTransitionException(String message) {
        super(message);
    }
}

package com.product.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class CategoryInUseException extends RuntimeException {

    public CategoryInUseException(Long categoryId) {
        super("Category " + categoryId + " cannot be deleted because it has associated products. "
                + "Re-assign or discontinue all products first.");
    }

    public CategoryInUseException(Long categoryId, long productCount) {
        super("Category " + categoryId + " cannot be deleted: " + productCount
                + " product(s) are assigned to it. Re-assign or discontinue all products first.");
    }
}

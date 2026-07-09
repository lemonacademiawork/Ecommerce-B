package com.lemonacademy.ecommerce.shipping.exception;

public class ShippingException extends RuntimeException {
    public ShippingException(String message) {
        super(message);
    }

    public ShippingException(String message, Throwable cause) {
        super(message, cause);
    }
}

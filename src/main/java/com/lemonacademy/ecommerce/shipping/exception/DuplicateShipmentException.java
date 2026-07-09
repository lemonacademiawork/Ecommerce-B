package com.lemonacademy.ecommerce.shipping.exception;

public class DuplicateShipmentException extends ShippingException {
    public DuplicateShipmentException(String message) {
        super(message);
    }
}

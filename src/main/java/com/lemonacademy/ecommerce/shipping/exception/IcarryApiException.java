package com.lemonacademy.ecommerce.shipping.exception;

public class IcarryApiException extends ShippingException {
    private final int statusCode;

    public IcarryApiException(String message) {
        super(message);
        this.statusCode = 500;
    }

    public IcarryApiException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public IcarryApiException(String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}

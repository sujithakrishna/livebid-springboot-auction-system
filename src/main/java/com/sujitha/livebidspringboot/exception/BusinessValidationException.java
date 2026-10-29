package com.sujitha.livebidspringboot.exception;

public class BusinessValidationException
        extends RuntimeException {

    public BusinessValidationException(String message) {
        super(message);
    }
}
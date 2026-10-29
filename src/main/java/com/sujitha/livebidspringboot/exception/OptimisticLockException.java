package com.sujitha.livebidspringboot.exception;

public class OptimisticLockException
        extends RuntimeException {

    public OptimisticLockException(String message) {
        super(message);
    }
}
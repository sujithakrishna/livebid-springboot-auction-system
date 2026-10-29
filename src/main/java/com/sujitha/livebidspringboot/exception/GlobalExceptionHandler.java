package com.sujitha.livebidspringboot.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, Object> handleIllegalArgumentException(
            IllegalArgumentException exception) {

        Map<String, Object> response =
                new LinkedHashMap<>();

        response.put("status", 404);
        response.put("message", exception.getMessage());
        response.put("error", "Not Found");

        return response;
    }

    @ExceptionHandler(BusinessValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleBusinessValidationException(
            BusinessValidationException exception) {

        Map<String, Object> response =
                new LinkedHashMap<>();

        response.put("status", 400);
        response.put("message", exception.getMessage());
        response.put("error", "Bad Request");

        return response;
    }

    @ExceptionHandler(OptimisticLockException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, Object> handleOptimisticLockException(
            OptimisticLockException exception) {

        Map<String, Object> response =
                new LinkedHashMap<>();

        response.put("status", 409);
        response.put("message", exception.getMessage());
        response.put("error", "Concurrent Update");

        return response;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleValidationException(
            MethodArgumentNotValidException exception) {

        Map<String, String> validationErrors =
                new LinkedHashMap<>();

        exception.getBindingResult()
                .getFieldErrors()
                .forEach(error ->
                        validationErrors.put(
                                error.getField(),
                                error.getDefaultMessage()
                        )
                );

        Map<String, Object> response =
                new LinkedHashMap<>();

        response.put("status", 400);
        response.put("error", "Bad Request");
        response.put("message", "Validation failed.");
        response.put("errors", validationErrors);

        return response;
    }
}
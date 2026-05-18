package com.ejbtestjava.controller;

import com.endpointblank.UnauthorizedException;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates well-known exceptions to HTTP responses.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UnauthorizedException.class)
    ProblemDetail handleUnauthorized(UnauthorizedException ex) {
        ProblemDetail detail = ProblemDetail.forStatus(ex.getStatusCode());
        detail.setTitle("Unauthorized");
        detail.setDetail(ex.getMessage());
        return detail;
    }
}

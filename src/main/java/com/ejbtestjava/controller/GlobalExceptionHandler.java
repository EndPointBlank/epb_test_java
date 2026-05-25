package com.ejbtestjava.controller;

import com.endpointblank.UnauthorizedException;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates exceptions to ProblemDetail JSON responses so the test app
 * never returns Spring Boot's whitelabel HTML error page.
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

    @ExceptionHandler(Exception.class)
    ProblemDetail handleException(Exception ex) {
        ProblemDetail detail = ProblemDetail.forStatus(500);
        detail.setTitle(ex.getClass().getSimpleName());
        detail.setDetail(ex.getMessage());
        return detail;
    }
}

package com.ejbtestjava.controller;

import com.endpointblank.spring.Authorized;
import com.endpointblank.spring.Versioned;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Intentionally raises an exception to exercise EndPointBlank error tracking.
 *
 * GET /errors
 *
 * Equivalent to ErrorsController in epb_test_rails.
 */
@RestController
@RequestMapping("/errors")
@Authorized
public class ErrorsController {

    @GetMapping
    @Versioned(versions = {"1"}, state = "Current")
    public void index() {
        throw new RuntimeException("This is a test error for error tracking.");
    }
}

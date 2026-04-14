package com.ejbtestjava.controller;

import com.ejbtestjava.model.Student;
import com.endpointblank.spring.Authorized;
import com.endpointblank.spring.Versioned;
import com.endpointblank.writers.LogWriter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Returns a list of students.
 *
 * GET /students
 *
 * Equivalent to StudentsController in epb_test_rails.
 */
@RestController
@RequestMapping("/students")
@Authorized
public class StudentsController {

    private static final List<Student> STUDENTS = List.of(
            new Student("Pete Daniels", "1241234"),
            new Student("Jack Vigneau",  "1241235")
    );

    @GetMapping
    @Versioned(versions = {"1"}, state = "Current")
    public ResponseEntity<Map<String, Object>> index() {
        LogWriter.info("Fetching students list");
        return ResponseEntity.ok(Map.of("students", STUDENTS));
    }
}

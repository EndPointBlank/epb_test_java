package com.ejbtestjava.controller;

import com.ejbtestjava.model.Teacher;
import com.endpointblank.spring.Authorized;
import com.endpointblank.spring.Versioned;
import com.endpointblank.writers.LogWriter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Returns a list of teachers or a single teacher by ID.
 *
 * GET /teachers        → all teachers
 * GET /teachers/{id}   → single teacher, 404 if not found
 *
 * Equivalent to TeachersController in epb_test_rails.
 */
@RestController
@RequestMapping("/teachers")
@Authorized
public class TeachersController {

    private static final List<Teacher> TEACHERS = List.of(
            new Teacher(1L, "Sam Smith", "12"),
            new Teacher(2L, "Joy Jones", "13")
    );

    @GetMapping
    @Versioned(versions = {"1"}, state = "Current")
    @Versioned(versions = {"2"}, state = "In Development")
    public ResponseEntity<Map<String, Object>> index() {
        LogWriter.info("Fetching teachers list");
        return ResponseEntity.ok(Map.of("teachers", TEACHERS));
    }

    @GetMapping("/{id}")
    @Versioned(versions = {"1"}, state = "Current")
    @Versioned(versions = {"2"}, state = "In Development")
    public ResponseEntity<?> show(@PathVariable Long id) {
        Optional<Teacher> teacher = TEACHERS.stream()
                .filter(t -> t.getId().equals(id))
                .findFirst();

        if (teacher.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(teacher.get());
    }
}

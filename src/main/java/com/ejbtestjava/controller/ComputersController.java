package com.ejbtestjava.controller;

import com.ejbtestjava.model.Computer;
import com.endpointblank.spring.Authorized;
import com.endpointblank.spring.Versioned;
import com.endpointblank.writers.LogWriter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages computers.
 *
 * GET    /computers       - list all computers
 * POST   /computers       - add a computer
 * DELETE /computers/{id}  - remove a computer
 */
@RestController
@RequestMapping("/computers")
@Authorized
public class ComputersController {

    private final CopyOnWriteArrayList<Computer> computers = new CopyOnWriteArrayList<>(List.of(
            new Computer(1L, "Dell Chromebook 3100", 1L, 1L, "Dell"),
            new Computer(2L, "HP Desktop 400 G9",   2L, 1L, "HP")
    ));

    private final AtomicLong nextId = new AtomicLong(3L);

    @GetMapping
    @Versioned(versions = {"1"}, state = "Current")
    public ResponseEntity<Map<String, Object>> index() {
        LogWriter.info("Fetching computers list");
        return ResponseEntity.ok(Map.of("computers", new ArrayList<>(computers)));
    }

    @PostMapping
    @Versioned(versions = {"1"}, state = "Current")
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body) {
        String description = (String) body.get("description");
        String brand       = (String) body.get("brand");

        if (description == null || description.isBlank()) {
            return ResponseEntity.unprocessableEntity()
                    .body(Map.of("error", "description is required"));
        }

        Long studentId = body.get("student_id") != null
                ? Long.valueOf(body.get("student_id").toString()) : null;
        Long teacherId = body.get("teacher_id") != null
                ? Long.valueOf(body.get("teacher_id").toString()) : null;

        Computer computer = new Computer(nextId.getAndIncrement(), description, studentId, teacherId, brand);
        computers.add(computer);
        LogWriter.info("Added computer: " + description);
        return ResponseEntity.status(201).body(Map.of("computer", computer));
    }

    @DeleteMapping("/{id}")
    @Versioned(versions = {"1"}, state = "Current")
    public ResponseEntity<?> destroy(@PathVariable Long id) {
        boolean removed = computers.removeIf(c -> c.getId().equals(id));
        if (!removed) {
            return ResponseEntity.status(404).body(Map.of("error", "Computer not found"));
        }
        LogWriter.info("Removed computer id: " + id);
        return ResponseEntity.ok(Map.of("message", "Computer removed"));
    }
}

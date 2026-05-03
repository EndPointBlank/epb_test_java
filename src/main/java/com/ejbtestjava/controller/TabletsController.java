package com.ejbtestjava.controller;

import com.ejbtestjava.model.Tablet;
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
 * Manages tablets.
 *
 * GET    /tablets       - list all tablets
 * POST   /tablets       - add a tablet
 * DELETE /tablets/{id}  - remove a tablet
 */
@RestController
@RequestMapping("/tablets")
@Authorized
public class TabletsController {

    private final CopyOnWriteArrayList<Tablet> tablets = new CopyOnWriteArrayList<>(List.of(
            new Tablet(1L, "Apple iPad (10th gen)", 1L, 1L, "Apple"),
            new Tablet(2L, "Microsoft Surface Pro 9", 2L, 2L, "Microsoft")
    ));

    private final AtomicLong nextId = new AtomicLong(3L);

    @GetMapping
    @Versioned(versions = {"1"}, state = "Current")
    public ResponseEntity<Map<String, Object>> index() {
        LogWriter.info("Fetching tablets list");
        return ResponseEntity.ok(Map.of("tablets", new ArrayList<>(tablets)));
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

        Tablet tablet = new Tablet(nextId.getAndIncrement(), description, studentId, teacherId, brand);
        tablets.add(tablet);
        LogWriter.info("Added tablet: " + description);
        return ResponseEntity.status(201).body(Map.of("tablet", tablet));
    }

    @DeleteMapping("/{id}")
    @Versioned(versions = {"1"}, state = "Current")
    public ResponseEntity<?> destroy(@PathVariable Long id) {
        boolean removed = tablets.removeIf(t -> t.getId().equals(id));
        if (!removed) {
            return ResponseEntity.status(404).body(Map.of("error", "Tablet not found"));
        }
        LogWriter.info("Removed tablet id: " + id);
        return ResponseEntity.ok(Map.of("message", "Tablet removed"));
    }
}

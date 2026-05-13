package com.ejbtestjava.controller;

import com.ejbtestjava.model.Tablet;
import com.ejbtestjava.repository.TabletRepository;
import com.endpointblank.spring.Authorized;
import com.endpointblank.spring.Versioned;
import com.endpointblank.writers.LogWriter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/tablets")
@Authorized
public class TabletsController {

    private final TabletRepository tabletRepository;

    public TabletsController(TabletRepository tabletRepository) {
        this.tabletRepository = tabletRepository;
    }

    @GetMapping
    @Versioned(versions = {"1"}, state = "Current")
    public ResponseEntity<Map<String, Object>> index() {
        LogWriter.info("Fetching tablets list");
        return ResponseEntity.ok(Map.of("tablets", tabletRepository.findAll()));
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

        Tablet tablet = new Tablet(description, studentId, teacherId, brand);
        tabletRepository.save(tablet);
        LogWriter.info("Added tablet: " + description);
        return ResponseEntity.status(201).body(Map.of("tablet", tablet));
    }

    @DeleteMapping("/{id}")
    @Versioned(versions = {"1"}, state = "Current")
    public ResponseEntity<?> destroy(@PathVariable Long id) {
        if (!tabletRepository.existsById(id)) {
            return ResponseEntity.status(404).body(Map.of("error", "Tablet not found"));
        }
        tabletRepository.deleteById(id);
        LogWriter.info("Removed tablet id: " + id);
        return ResponseEntity.ok(Map.of("message", "Tablet removed"));
    }
}

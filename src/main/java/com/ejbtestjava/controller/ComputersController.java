package com.ejbtestjava.controller;

import com.ejbtestjava.model.Computer;
import com.ejbtestjava.repository.ComputerRepository;
import com.endpointblank.spring.Authorized;
import com.endpointblank.spring.Versioned;
import com.endpointblank.writers.LogWriter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/computers")
@Authorized
public class ComputersController {

    private final ComputerRepository computerRepository;

    public ComputersController(ComputerRepository computerRepository) {
        this.computerRepository = computerRepository;
    }

    @GetMapping
    @Versioned(versions = {"1"}, state = "Current")
    public ResponseEntity<Map<String, Object>> index() {
        LogWriter.info("Fetching computers list");
        return ResponseEntity.ok(Map.of("computers", computerRepository.findAll()));
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

        Computer computer = new Computer(description, studentId, teacherId, brand);
        computerRepository.save(computer);
        LogWriter.info("Added computer: " + description);
        return ResponseEntity.status(201).body(Map.of("computer", computer));
    }

    @DeleteMapping("/{id}")
    @Versioned(versions = {"1"}, state = "Current")
    public ResponseEntity<?> destroy(@PathVariable Long id) {
        if (!computerRepository.existsById(id)) {
            return ResponseEntity.status(404).body(Map.of("error", "Computer not found"));
        }
        computerRepository.deleteById(id);
        LogWriter.info("Removed computer id: " + id);
        return ResponseEntity.ok(Map.of("message", "Computer removed"));
    }
}

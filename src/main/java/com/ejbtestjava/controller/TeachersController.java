package com.ejbtestjava.controller;

import com.ejbtestjava.repository.TeacherRepository;
import com.endpointblank.spring.Authorized;
import com.endpointblank.spring.Versioned;
import com.endpointblank.writers.LogWriter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/teachers")
@Authorized
public class TeachersController {

    private final TeacherRepository teacherRepository;

    public TeachersController(TeacherRepository teacherRepository) {
        this.teacherRepository = teacherRepository;
    }

    @GetMapping
    @Versioned(versions = {"1"}, state = "Current")
    @Versioned(versions = {"2"}, state = "In Development")
    public ResponseEntity<Map<String, Object>> index() {
        LogWriter.info("Fetching teachers list");
        return ResponseEntity.ok(Map.of("teachers", teacherRepository.findAll()));
    }

    @GetMapping("/{id}")
    @Versioned(versions = {"1"}, state = "Current")
    @Versioned(versions = {"2"}, state = "In Development")
    public ResponseEntity<?> show(@PathVariable Long id) {
        return teacherRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}

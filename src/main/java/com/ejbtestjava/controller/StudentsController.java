package com.ejbtestjava.controller;

import com.ejbtestjava.repository.StudentRepository;
import com.endpointblank.spring.Authorized;
import com.endpointblank.spring.Versioned;
import com.endpointblank.writers.LogWriter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/students")
@Authorized
public class StudentsController {

    private final StudentRepository studentRepository;

    public StudentsController(StudentRepository studentRepository) {
        this.studentRepository = studentRepository;
    }

    @GetMapping
    @Versioned(versions = {"1"}, state = "Current")
    public ResponseEntity<Map<String, Object>> index() {
        LogWriter.info("Fetching students list");
        return ResponseEntity.ok(Map.of("students", studentRepository.findAll()));
    }
}

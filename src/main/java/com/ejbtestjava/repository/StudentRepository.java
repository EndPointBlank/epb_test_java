package com.ejbtestjava.repository;

import com.ejbtestjava.model.Student;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentRepository extends JpaRepository<Student, Long> {}

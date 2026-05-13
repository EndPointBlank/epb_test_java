package com.ejbtestjava.repository;

import com.ejbtestjava.model.Computer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ComputerRepository extends JpaRepository<Computer, Long> {}

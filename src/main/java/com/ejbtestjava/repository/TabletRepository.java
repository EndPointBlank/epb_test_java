package com.ejbtestjava.repository;

import com.ejbtestjava.model.Tablet;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TabletRepository extends JpaRepository<Tablet, Long> {}

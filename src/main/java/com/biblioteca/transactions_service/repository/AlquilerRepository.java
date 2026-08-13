package com.biblioteca.transactions_service.repository;

import com.biblioteca.transactions_service.model.Alquiler;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlquilerRepository extends JpaRepository<Alquiler, Long> {
}
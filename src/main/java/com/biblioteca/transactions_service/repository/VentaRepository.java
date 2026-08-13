package com.biblioteca.transactions_service.repository;

import com.biblioteca.transactions_service.model.Venta;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VentaRepository extends JpaRepository<Venta, Long> {
}
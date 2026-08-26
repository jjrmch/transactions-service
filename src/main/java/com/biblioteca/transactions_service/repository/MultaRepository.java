package com.biblioteca.transactions_service.repository;

import com.biblioteca.transactions_service.model.EstadoMulta;
import com.biblioteca.transactions_service.model.Multa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MultaRepository extends JpaRepository<Multa, Long> {

    List<Multa> findByClienteIdOrderByFechaCreacionDesc(Long clienteId);

    boolean existsByClienteIdAndEstado(Long clienteId, EstadoMulta estado);
}
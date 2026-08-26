package com.biblioteca.transactions_service.repository;

import com.biblioteca.transactions_service.model.EstadoReserva;
import com.biblioteca.transactions_service.model.Reserva;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ReservaRepository extends JpaRepository<Reserva, Long> {

    Optional<Reserva> findFirstByLibroIdAndEstadoOrderByFechaReservaAsc(Long libroId, EstadoReserva estado);

    boolean existsByClienteIdAndLibroIdAndEstado(Long clienteId, Long libroId, EstadoReserva estado);

    List<Reserva> findByClienteIdOrderByFechaReservaDesc(Long clienteId);

    List<Reserva> findByEstadoAndFechaExpiracionBefore(EstadoReserva estado, LocalDateTime fecha);
}
package com.biblioteca.transactions_service.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Reserva {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long libroId;

    private Long clienteId;

    private LocalDateTime fechaReserva;

    private LocalDateTime fechaExpiracion;

    @Enumerated(EnumType.STRING)
    private EstadoReserva estado;
}
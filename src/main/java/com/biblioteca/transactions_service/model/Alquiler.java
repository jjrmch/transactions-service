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
public class Alquiler {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long libroId;          

    private String cliente;

    private LocalDateTime fechaAlquiler;

    private LocalDateTime fechaDevolucion;

    @Enumerated(EnumType.STRING)
    private EstadoAlquiler estado;
}
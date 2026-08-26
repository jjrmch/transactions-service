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
public class Multa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long alquilerId;

    private Long clienteId;

    private Double monto;

    private Integer diasRetraso;

    private String motivo;

    private LocalDateTime fechaCreacion;

    @Enumerated(EnumType.STRING)
    private EstadoMulta estado;
}
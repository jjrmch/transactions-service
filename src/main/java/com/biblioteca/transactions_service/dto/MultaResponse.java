package com.biblioteca.transactions_service.dto;

import com.biblioteca.transactions_service.model.EstadoMulta;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MultaResponse {
    private Long id;
    private Long alquilerId;
    private Long clienteId;
    private String nombreCliente;
    private Double monto;
    private Integer diasRetraso;
    private String motivo;
    private LocalDateTime fechaCreacion;
    private EstadoMulta estado;
}
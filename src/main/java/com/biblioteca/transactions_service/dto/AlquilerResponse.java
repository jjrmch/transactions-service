package com.biblioteca.transactions_service.dto;

import com.biblioteca.transactions_service.model.EstadoAlquiler;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AlquilerResponse {
    private Long id;
    private Long libroId;
    private String tituloLibro;   
    private Long clienteId;
    private String nombreCliente;
    private LocalDateTime fechaAlquiler;
    private LocalDateTime fechaLimiteDevolucion;
    private LocalDateTime fechaDevolucion;
    private EstadoAlquiler estado;
    private Integer renovaciones;
}
package com.biblioteca.transactions_service.dto;

import com.biblioteca.transactions_service.model.EstadoReserva;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReservaResponse {
    private Long id;
    private Long libroId;
    private String tituloLibro;
    private Long clienteId;
    private String nombreCliente;
    private LocalDateTime fechaReserva;
    private LocalDateTime fechaExpiracion;
    private EstadoReserva estado;
}
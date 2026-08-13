package com.biblioteca.transactions_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VentaResponse {
    private Long id;
    private Long libroId;
    private String tituloLibro;  
    private Integer cantidad;
    private Double precioTotal;
    private String cliente;
    private LocalDateTime fecha;
}
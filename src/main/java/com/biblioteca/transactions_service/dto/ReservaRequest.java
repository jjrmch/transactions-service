package com.biblioteca.transactions_service.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ReservaRequest {

    @NotNull(message = "El id del libro es obligatorio")
    private Long libroId;

    @NotNull(message = "El cliente es obligatorio")
    private Long clienteId;
}
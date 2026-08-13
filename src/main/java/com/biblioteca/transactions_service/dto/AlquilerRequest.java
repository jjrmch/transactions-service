package com.biblioteca.transactions_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AlquilerRequest {

    @NotNull(message = "El id del libro es obligatorio")
    private Long libroId;

    @NotBlank(message = "El cliente es obligatorio")
    private String cliente;
}
package com.biblioteca.transactions_service.dto;

import lombok.Data;

@Data
public class ClienteDto {
    private Long id;
    private String nombre;
    private String email;
    private String telefono;
}
    
package com.biblioteca.transactions_service.dto;

import lombok.Data;

@Data
public class LibroDto {
    private Long id;
    private String titulo;
    private String autor;
    private String isbn;
    private Double precio;
    private Integer stock;
}
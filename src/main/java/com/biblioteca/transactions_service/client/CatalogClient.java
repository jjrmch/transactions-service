package com.biblioteca.transactions_service.client;

import com.biblioteca.transactions_service.dto.AjusteStockDto;
import com.biblioteca.transactions_service.dto.LibroDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "catalog-service")
public interface CatalogClient {

    @GetMapping("/libros/{id}")
    LibroDto obtenerLibro(@PathVariable Long id);

    @PatchMapping("/libros/{id}/stock")
    LibroDto ajustarStock(@PathVariable Long id, @RequestBody AjusteStockDto ajuste);
}
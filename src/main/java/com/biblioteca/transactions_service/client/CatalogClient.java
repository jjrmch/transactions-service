package com.biblioteca.transactions_service.client;

import com.biblioteca.transactions_service.dto.LibroDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import com.biblioteca.transactions_service.dto.AjusteStockDto;

@FeignClient(name = "catalog-service", url = "http://localhost:8081")
public interface CatalogClient {

    @GetMapping("/libros/{id}")
    LibroDto obtenerLibro(@PathVariable Long id);

    @PatchMapping("/libros/{id}/stock")
    LibroDto ajustarStock(@PathVariable Long id, @RequestBody AjusteStockDto ajuste);
}
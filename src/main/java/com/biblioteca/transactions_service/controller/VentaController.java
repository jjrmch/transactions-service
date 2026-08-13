package com.biblioteca.transactions_service.controller;

import com.biblioteca.transactions_service.dto.VentaRequest;
import com.biblioteca.transactions_service.dto.VentaResponse;
import com.biblioteca.transactions_service.service.VentaService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/ventas")
public class VentaController {

    private final VentaService ventaService;

    public VentaController(VentaService ventaService) {
        this.ventaService = ventaService;
    }

    @GetMapping
    public List<VentaResponse> listarVentas() {
        return ventaService.listarTodas();
    }

    @PostMapping
    public ResponseEntity<VentaResponse> vender(@Valid @RequestBody VentaRequest request) {
        VentaResponse venta = ventaService.vender(request);
        return ResponseEntity.status(201).body(venta);
    }
}
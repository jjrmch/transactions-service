package com.biblioteca.transactions_service.controller;

import com.biblioteca.transactions_service.dto.MultaResponse;
import com.biblioteca.transactions_service.service.MultaService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/multas")
public class MultaController {

    private final MultaService multaService;

    public MultaController(MultaService multaService) {
        this.multaService = multaService;
    }

    @GetMapping
    public List<MultaResponse> listarMultas() {
        return multaService.listarTodas();
    }

    @GetMapping("/cliente/{clienteId}")
    public List<MultaResponse> listarPorCliente(@PathVariable Long clienteId) {
        return multaService.listarPorCliente(clienteId);
    }

    @PostMapping("/{id}/pago")
    public MultaResponse pagar(@PathVariable Long id) {
        return multaService.pagar(id);
    }
}
package com.biblioteca.transactions_service.controller;

import com.biblioteca.transactions_service.dto.ReservaRequest;
import com.biblioteca.transactions_service.dto.ReservaResponse;
import com.biblioteca.transactions_service.service.ReservaService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/reservas")
public class ReservaController {

    private final ReservaService reservaService;

    public ReservaController(ReservaService reservaService) {
        this.reservaService = reservaService;
    }

    @GetMapping
    public List<ReservaResponse> listarReservas() {
        return reservaService.listarTodas();
    }

    @GetMapping("/cliente/{clienteId}")
    public List<ReservaResponse> listarPorCliente(@PathVariable Long clienteId) {
        return reservaService.listarPorCliente(clienteId);
    }

    @PostMapping
    public ResponseEntity<ReservaResponse> reservar(@Valid @RequestBody ReservaRequest request) {
        ReservaResponse reserva = reservaService.reservar(request);
        return ResponseEntity.status(201).body(reserva);
    }

    @PostMapping("/{id}/confirmar")
    public ReservaResponse confirmar(@PathVariable Long id) {
        return reservaService.confirmar(id);
    }

    @DeleteMapping("/{id}")
    public ReservaResponse cancelar(@PathVariable Long id) {
        return reservaService.cancelar(id);
    }
}
package com.biblioteca.transactions_service.controller;

import com.biblioteca.transactions_service.dto.AlquilerRequest;
import com.biblioteca.transactions_service.dto.AlquilerResponse;
import com.biblioteca.transactions_service.service.AlquilerService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/alquileres")
public class AlquilerController {

    private final AlquilerService alquilerService;

    public AlquilerController(AlquilerService alquilerService) {
        this.alquilerService = alquilerService;
    }

    @GetMapping
    public List<AlquilerResponse> listarAlquileres() {
        return alquilerService.listarTodos();
    }

    @PostMapping
    public ResponseEntity<AlquilerResponse> alquilar(@Valid @RequestBody AlquilerRequest request) {
        AlquilerResponse alquiler = alquilerService.alquilar(request);
        return ResponseEntity.status(201).body(alquiler);
    }

    @PostMapping("/{id}/devolucion")
    public AlquilerResponse devolver(@PathVariable Long id) {
        return alquilerService.devolver(id);
    }

    @PostMapping("/{id}/renovacion")
    public AlquilerResponse renovar(@PathVariable Long id) {
        return alquilerService.renovar(id);
    }
}
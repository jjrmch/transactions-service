package com.biblioteca.transactions_service.service;

import com.biblioteca.transactions_service.client.CustomerClient;
import com.biblioteca.transactions_service.dto.MultaResponse;
import com.biblioteca.transactions_service.exception.EstadoInvalidoException;
import com.biblioteca.transactions_service.exception.RecursoNoEncontradoException;
import com.biblioteca.transactions_service.model.Alquiler;
import com.biblioteca.transactions_service.model.EstadoMulta;
import com.biblioteca.transactions_service.model.Multa;
import com.biblioteca.transactions_service.repository.MultaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class MultaService {

    private final MultaRepository multaRepository;
    private final CustomerClient customerClient;

    @Value("${biblioteca.multa.precio-por-dia:1.0}")
    private double precioPorDia;

    @Value("${biblioteca.multa.dias-gracia:0}")
    private int diasGracia;

    public MultaService(MultaRepository multaRepository, CustomerClient customerClient) {
        this.multaRepository = multaRepository;
        this.customerClient = customerClient;
    }

    public List<MultaResponse> listarTodas() {
        return multaRepository.findAll()
                .stream()
                .map(multa -> aResponse(multa, obtenerNombreClienteSeguro(multa.getClienteId())))
                .toList();
    }

    public List<MultaResponse> listarPorCliente(Long clienteId) {
        return multaRepository.findByClienteIdOrderByFechaCreacionDesc(clienteId)
                .stream()
                .map(multa -> aResponse(multa, obtenerNombreClienteSeguro(clienteId)))
                .toList();
    }

    public boolean tieneMultasPendientes(Long clienteId) {
        return multaRepository.existsByClienteIdAndEstado(clienteId, EstadoMulta.PENDIENTE);
    }

    @Transactional
    public MultaResponse pagar(Long multaId) {
        Multa multa = multaRepository.findById(multaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Multa no encontrada con id: " + multaId));

        if (multa.getEstado() == EstadoMulta.PAGADA) {
            throw new EstadoInvalidoException("Esta multa ya fue pagada");
        }

        multa.setEstado(EstadoMulta.PAGADA);

        return aResponse(multaRepository.save(multa), obtenerNombreClienteSeguro(multa.getClienteId()));
    }

    @Transactional
    public Multa generarMulta(Alquiler alquiler) {
        if (alquiler.getFechaLimiteDevolucion() == null) {
            return null;
        }

        long diasRetraso = ChronoUnit.DAYS.between(
                alquiler.getFechaLimiteDevolucion().toLocalDate(), LocalDate.now());

        if (diasRetraso <= diasGracia) {
            return null;
        }

        Multa multa = new Multa();
        multa.setAlquilerId(alquiler.getId());
        multa.setClienteId(alquiler.getClienteId());
        multa.setMonto(diasRetraso * precioPorDia);
        multa.setDiasRetraso((int) diasRetraso);
        multa.setMotivo("Devolución fuera de plazo");
        multa.setFechaCreacion(LocalDateTime.now());
        multa.setEstado(EstadoMulta.PENDIENTE);

        return multaRepository.save(multa);
    }

    private MultaResponse aResponse(Multa multa, String nombreCliente) {
        return new MultaResponse(
                multa.getId(),
                multa.getAlquilerId(),
                multa.getClienteId(),
                nombreCliente,
                multa.getMonto(),
                multa.getDiasRetraso(),
                multa.getMotivo(),
                multa.getFechaCreacion(),
                multa.getEstado()
        );
    }

    private String obtenerNombreClienteSeguro(Long clienteId) {
        try {
            return customerClient.obtenerCliente(clienteId).getNombre();
        } catch (Exception e) {
            return null;
        }
    }
}
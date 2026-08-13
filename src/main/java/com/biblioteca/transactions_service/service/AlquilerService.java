package com.biblioteca.transactions_service.service;

import com.biblioteca.transactions_service.dto.AlquilerRequest;
import com.biblioteca.transactions_service.dto.AlquilerResponse;
import com.biblioteca.transactions_service.exception.EstadoInvalidoException;
import com.biblioteca.transactions_service.exception.RecursoNoEncontradoException;
import com.biblioteca.transactions_service.model.Alquiler;
import com.biblioteca.transactions_service.model.EstadoAlquiler;
import com.biblioteca.transactions_service.repository.AlquilerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AlquilerService {

    private final AlquilerRepository alquilerRepository;

    public AlquilerService(AlquilerRepository alquilerRepository) {
        this.alquilerRepository = alquilerRepository;
    }

    @Transactional
    public AlquilerResponse alquilar(AlquilerRequest request) {
        // TODO (con OpenFeign):
        //   1. Pedir el libro al catalog-service
        //   2. Comprobar que hay stock (>= 1)
        //   3. Pedir al catalog que baje el stock en 1

        Alquiler alquiler = new Alquiler();
        alquiler.setLibroId(request.getLibroId());
        alquiler.setCliente(request.getCliente());
        alquiler.setFechaAlquiler(LocalDateTime.now());
        alquiler.setFechaDevolucion(null);
        alquiler.setEstado(EstadoAlquiler.ACTIVO);

        return aResponse(alquilerRepository.save(alquiler));
    }

    @Transactional
    public AlquilerResponse devolver(Long alquilerId) {
        Alquiler alquiler = alquilerRepository.findById(alquilerId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "Alquiler no encontrado con id: " + alquilerId));

        if (alquiler.getEstado() == EstadoAlquiler.DEVUELTO) {
            throw new EstadoInvalidoException("Este alquiler ya fue devuelto");
        }

        // TODO (con OpenFeign): pedir al catalog que suba el stock en 1

        alquiler.setEstado(EstadoAlquiler.DEVUELTO);
        alquiler.setFechaDevolucion(LocalDateTime.now());

        return aResponse(alquilerRepository.save(alquiler));
    }

    public List<AlquilerResponse> listarTodos() {
        return alquilerRepository.findAll()
                .stream()
                .map(Alquiler -> aResponse(Alquiler))
                .toList();
    }

    private AlquilerResponse aResponse(Alquiler alquiler) {
        return new AlquilerResponse(
                alquiler.getId(),
                alquiler.getLibroId(),
                null,                 
                alquiler.getCliente(),
                alquiler.getFechaAlquiler(),
                alquiler.getFechaDevolucion(),
                alquiler.getEstado()
        );
    }
}
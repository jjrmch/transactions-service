package com.biblioteca.transactions_service.service;

import com.biblioteca.transactions_service.client.CatalogClient;
import com.biblioteca.transactions_service.client.CustomerClient;
import com.biblioteca.transactions_service.dto.AjusteStockDto;
import com.biblioteca.transactions_service.dto.AlquilerRequest;
import com.biblioteca.transactions_service.dto.AlquilerResponse;
import com.biblioteca.transactions_service.dto.ClienteDto;
import com.biblioteca.transactions_service.dto.LibroDto;
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
    private final CatalogClient catalogClient;
    private final CustomerClient customerClient;

    public AlquilerService(AlquilerRepository alquilerRepository, CatalogClient catalogClient, CustomerClient customerClient) {
        this.alquilerRepository = alquilerRepository;
        this.catalogClient = catalogClient;
        this.customerClient = customerClient;
    }

    @Transactional
    public AlquilerResponse alquilar(AlquilerRequest request) {
        // Leer el libro (para el título) y bajar el stock en 1.
        // El catalog valida el stock y responde 409 si no hay ejemplares.
        LibroDto libro = catalogClient.obtenerLibro(request.getLibroId());
        catalogClient.ajustarStock(request.getLibroId(), new AjusteStockDto(-1));

        Alquiler alquiler = new Alquiler();
        alquiler.setLibroId(request.getLibroId());
        alquiler.setClienteId(obtenerClienteSeguro(request.getClienteId()).getId());
        alquiler.setFechaAlquiler(LocalDateTime.now());
        alquiler.setFechaDevolucion(null);
        alquiler.setEstado(EstadoAlquiler.ACTIVO);

        return aResponse(alquilerRepository.save(alquiler), libro.getTitulo());
    }

    @Transactional
    public AlquilerResponse devolver(Long alquilerId) {
        Alquiler alquiler = alquilerRepository.findById(alquilerId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "Alquiler no encontrado con id: " + alquilerId));

        if (alquiler.getEstado() == EstadoAlquiler.DEVUELTO) {
            throw new EstadoInvalidoException("Este alquiler ya fue devuelto");
        }

        // Devolver el ejemplar: subir el stock en 1
        catalogClient.ajustarStock(alquiler.getLibroId(), new AjusteStockDto(1));

        alquiler.setEstado(EstadoAlquiler.DEVUELTO);
        alquiler.setFechaDevolucion(LocalDateTime.now());

        return aResponse(alquilerRepository.save(alquiler), null);
    }

    public List<AlquilerResponse> listarTodos() {
    return alquilerRepository.findAll()
            .stream()
            .map(alquiler -> {
                String titulo = obtenerTituloSeguro(alquiler.getLibroId());
                return aResponse(alquiler, titulo);
            })
            .toList();
}

    private AlquilerResponse aResponse(Alquiler alquiler, String tituloLibro) {
        return new AlquilerResponse(
                alquiler.getId(),
                alquiler.getLibroId(),
                tituloLibro,
                alquiler.getClienteId(),
                obtenerNombreClienteSeguro(alquiler.getClienteId()),
                alquiler.getFechaAlquiler(),
                alquiler.getFechaDevolucion(),
                alquiler.getEstado()
        );
    }

    private String obtenerTituloSeguro(Long libroId) {
    try {
        LibroDto libro = catalogClient.obtenerLibro(libroId);
        return libro.getTitulo();
    } catch (Exception e) {
        return null;
    }
}

private ClienteDto obtenerClienteSeguro(Long clienteId) {
    try {
        return customerClient.obtenerCliente(clienteId);
    } catch (Exception e) {
        throw new RecursoNoEncontradoException("Cliente no encontrado con id: " + clienteId);
    }
}

private String obtenerNombreClienteSeguro(Long clienteId) {
    try {
        return customerClient.obtenerCliente(clienteId).getNombre();
    } catch (Exception e) {
        return null;
    }
}
}
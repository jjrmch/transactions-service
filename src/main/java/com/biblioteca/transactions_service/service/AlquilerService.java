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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AlquilerService {

    private final AlquilerRepository alquilerRepository;
    private final CatalogClient catalogClient;
    private final CustomerClient customerClient;
    private final MultaService multaService;
    private final ReservaService reservaService;

    @Value("${biblioteca.alquiler.dias-prestamo:15}")
    private int diasPrestamo;

    @Value("${biblioteca.alquiler.dias-renovacion:7}")
    private int diasRenovacion;

    @Value("${biblioteca.alquiler.max-renovaciones:2}")
    private int maxRenovaciones;

    public AlquilerService(AlquilerRepository alquilerRepository, CatalogClient catalogClient,
                           CustomerClient customerClient, MultaService multaService,
                           ReservaService reservaService) {
        this.alquilerRepository = alquilerRepository;
        this.catalogClient = catalogClient;
        this.customerClient = customerClient;
        this.multaService = multaService;
        this.reservaService = reservaService;
    }

    @Transactional
    public AlquilerResponse alquilar(AlquilerRequest request) {
        // Leer el libro (para el título)
        LibroDto libro = catalogClient.obtenerLibro(request.getLibroId());

        // Validar el cliente ANTES de tocar el stock
        ClienteDto cliente = obtenerClienteSeguro(request.getClienteId());

        // No permitir alquilar a clientes con multas pendientes
        if (multaService.tieneMultasPendientes(cliente.getId())) {
            throw new EstadoInvalidoException("El cliente tiene multas pendientes y no puede alquilar");
        }

        // Bajar el stock en 1. El catalog valida y responde 409 si no hay ejemplares.
        catalogClient.ajustarStock(request.getLibroId(), new AjusteStockDto(-1));

        Alquiler alquiler = new Alquiler();
        alquiler.setLibroId(request.getLibroId());
        alquiler.setClienteId(cliente.getId());
        alquiler.setFechaAlquiler(LocalDateTime.now());
        alquiler.setFechaLimiteDevolucion(LocalDateTime.now().plusDays(diasPrestamo));
        alquiler.setFechaDevolucion(null);
        alquiler.setEstado(EstadoAlquiler.ACTIVO);
        alquiler.setRenovaciones(0);

        return aResponse(alquilerRepository.save(alquiler), libro.getTitulo(), cliente.getNombre());
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
        alquilerRepository.save(alquiler);

        // Generar multa si la devolución se hizo fuera de plazo
        multaService.generarMulta(alquiler);

        // Si hay reservas activas, entregar el ejemplar al primer cliente en cola
        reservaService.materializarProxima(alquiler.getLibroId());

        return aResponse(alquiler, null,
                obtenerNombreClienteSeguro(alquiler.getClienteId()));
    }

    @Transactional
    public AlquilerResponse renovar(Long alquilerId) {
        Alquiler alquiler = alquilerRepository.findById(alquilerId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "Alquiler no encontrado con id: " + alquilerId));

        if (alquiler.getEstado() == EstadoAlquiler.DEVUELTO) {
            throw new EstadoInvalidoException("No se puede renovar un alquiler ya devuelto");
        }

        if (alquiler.getRenovaciones() != null && alquiler.getRenovaciones() >= maxRenovaciones) {
            throw new EstadoInvalidoException("Se alcanzó el máximo de renovaciones permitidas");
        }

        if (alquiler.getFechaLimiteDevolucion() != null
                && alquiler.getFechaLimiteDevolucion().isBefore(LocalDateTime.now())) {
            throw new EstadoInvalidoException("No se puede renovar un alquiler vencido");
        }

        alquiler.setFechaLimiteDevolucion(alquiler.getFechaLimiteDevolucion().plusDays(diasRenovacion));
        alquiler.setRenovaciones(alquiler.getRenovaciones() == null ? 1 : alquiler.getRenovaciones() + 1);

        return aResponse(alquilerRepository.save(alquiler),
                obtenerTituloSeguro(alquiler.getLibroId()),
                obtenerNombreClienteSeguro(alquiler.getClienteId()));
    }

    public List<AlquilerResponse> listarTodos() {
        return alquilerRepository.findAll()
                .stream()
                .map(alquiler -> aResponse(alquiler,
                        obtenerTituloSeguro(alquiler.getLibroId()),
                        obtenerNombreClienteSeguro(alquiler.getClienteId())))
                .toList();
    }

    private AlquilerResponse aResponse(Alquiler alquiler, String tituloLibro, String nombreCliente) {
        return new AlquilerResponse(
                alquiler.getId(),
                alquiler.getLibroId(),
                tituloLibro,
                alquiler.getClienteId(),
                nombreCliente,
                alquiler.getFechaAlquiler(),
                alquiler.getFechaLimiteDevolucion(),
                alquiler.getFechaDevolucion(),
                alquiler.getEstado(),
                alquiler.getRenovaciones()
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
package com.biblioteca.transactions_service.service;

import com.biblioteca.transactions_service.client.CatalogClient;
import com.biblioteca.transactions_service.client.CustomerClient;
import com.biblioteca.transactions_service.dto.AjusteStockDto;
import com.biblioteca.transactions_service.dto.AlquilerResponse;
import com.biblioteca.transactions_service.dto.ClienteDto;
import com.biblioteca.transactions_service.dto.LibroDto;
import com.biblioteca.transactions_service.dto.ReservaRequest;
import com.biblioteca.transactions_service.dto.ReservaResponse;
import com.biblioteca.transactions_service.exception.EstadoInvalidoException;
import com.biblioteca.transactions_service.exception.RecursoNoEncontradoException;
import com.biblioteca.transactions_service.model.Alquiler;
import com.biblioteca.transactions_service.model.EstadoAlquiler;
import com.biblioteca.transactions_service.model.EstadoReserva;
import com.biblioteca.transactions_service.model.Reserva;
import com.biblioteca.transactions_service.repository.AlquilerRepository;
import com.biblioteca.transactions_service.repository.ReservaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class ReservaService {

    private final ReservaRepository reservaRepository;
    private final AlquilerRepository alquilerRepository;
    private final CatalogClient catalogClient;
    private final CustomerClient customerClient;

    @Value("${biblioteca.reserva.horas-expiracion:48}")
    private long horasExpiracion;

    @Value("${biblioteca.alquiler.dias-prestamo:15}")
    private int diasPrestamo;

    public ReservaService(ReservaRepository reservaRepository, AlquilerRepository alquilerRepository,
                          CatalogClient catalogClient, CustomerClient customerClient) {
        this.reservaRepository = reservaRepository;
        this.alquilerRepository = alquilerRepository;
        this.catalogClient = catalogClient;
        this.customerClient = customerClient;
    }

    @Transactional
    public ReservaResponse reservar(ReservaRequest request) {
        LibroDto libro = catalogClient.obtenerLibro(request.getLibroId());
        ClienteDto cliente = obtenerClienteSeguro(request.getClienteId());

        if (reservaRepository.existsByClienteIdAndLibroIdAndEstado(
                cliente.getId(), libro.getId(), EstadoReserva.ACTIVA)) {
            throw new EstadoInvalidoException("El cliente ya tiene una reserva activa para este libro");
        }

        Reserva reserva = new Reserva();
        reserva.setLibroId(libro.getId());
        reserva.setClienteId(cliente.getId());
        reserva.setFechaReserva(LocalDateTime.now());
        reserva.setFechaExpiracion(LocalDateTime.now().plusHours(horasExpiracion));
        reserva.setEstado(EstadoReserva.ACTIVA);

        return aResponse(reservaRepository.save(reserva), libro.getTitulo(), cliente.getNombre());
    }

    @Transactional
    public ReservaResponse confirmar(Long reservaId) {
        Reserva reserva = buscarActiva(reservaId);
        LibroDto libro = catalogClient.obtenerLibro(reserva.getLibroId());

        if (libro.getStock() == null || libro.getStock() <= 0) {
            throw new EstadoInvalidoException("No hay stock disponible para materializar la reserva");
        }

        catalogClient.ajustarStock(libro.getId(), new AjusteStockDto(-1));

        crearAlquiler(reserva, libro.getId());
        reserva.setEstado(EstadoReserva.CUMPLIDA);
        reservaRepository.save(reserva);

        return aResponse(reserva, libro.getTitulo(), obtenerNombreClienteSeguro(reserva.getClienteId()));
    }

    @Transactional
    public ReservaResponse cancelar(Long reservaId) {
        Reserva reserva = buscarActiva(reservaId);
        reserva.setEstado(EstadoReserva.CANCELADA);

        return aResponse(reservaRepository.save(reserva),
                obtenerTituloSeguro(reserva.getLibroId()),
                obtenerNombreClienteSeguro(reserva.getClienteId()));
    }

    public List<ReservaResponse> listarTodas() {
        return reservaRepository.findAll()
                .stream()
                .map(reserva -> aResponse(reserva,
                        obtenerTituloSeguro(reserva.getLibroId()),
                        obtenerNombreClienteSeguro(reserva.getClienteId())))
                .toList();
    }

    public List<ReservaResponse> listarPorCliente(Long clienteId) {
        return reservaRepository.findByClienteIdOrderByFechaReservaDesc(clienteId)
                .stream()
                .map(reserva -> aResponse(reserva,
                        obtenerTituloSeguro(reserva.getLibroId()),
                        obtenerNombreClienteSeguro(clienteId)))
                .toList();
    }

    @Transactional
    public Optional<AlquilerResponse> materializarProxima(Long libroId) {
        Optional<Reserva> proxima = reservaRepository
                .findFirstByLibroIdAndEstadoOrderByFechaReservaAsc(libroId, EstadoReserva.ACTIVA);
        if (proxima.isEmpty()) {
            return Optional.empty();
        }

        Reserva reserva = proxima.get();
        LibroDto libro = catalogClient.obtenerLibro(libroId);
        catalogClient.ajustarStock(libroId, new AjusteStockDto(-1));

        Alquiler alquiler = crearAlquiler(reserva, libro.getId());
        reserva.setEstado(EstadoReserva.CUMPLIDA);
        reservaRepository.save(reserva);

        return Optional.of(new AlquilerResponse(
                alquiler.getId(),
                alquiler.getLibroId(),
                libro.getTitulo(),
                alquiler.getClienteId(),
                obtenerNombreClienteSeguro(alquiler.getClienteId()),
                alquiler.getFechaAlquiler(),
                alquiler.getFechaLimiteDevolucion(),
                alquiler.getFechaDevolucion(),
                alquiler.getEstado(),
                alquiler.getRenovaciones()
        ));
    }

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void expirarReservas() {
        List<Reserva> vencidas = reservaRepository
                .findByEstadoAndFechaExpiracionBefore(EstadoReserva.ACTIVA, LocalDateTime.now());
        for (Reserva reserva : vencidas) {
            reserva.setEstado(EstadoReserva.EXPIRADA);
            reservaRepository.save(reserva);
        }
    }

    private Alquiler crearAlquiler(Reserva reserva, Long libroId) {
        Alquiler alquiler = new Alquiler();
        alquiler.setLibroId(libroId);
        alquiler.setClienteId(reserva.getClienteId());
        alquiler.setFechaAlquiler(LocalDateTime.now());
        alquiler.setFechaLimiteDevolucion(LocalDateTime.now().plusDays(diasPrestamo));
        alquiler.setFechaDevolucion(null);
        alquiler.setEstado(EstadoAlquiler.ACTIVO);
        alquiler.setRenovaciones(0);
        return alquilerRepository.save(alquiler);
    }

    private Reserva buscarActiva(Long reservaId) {
        Reserva reserva = reservaRepository.findById(reservaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Reserva no encontrada con id: " + reservaId));
        if (reserva.getEstado() != EstadoReserva.ACTIVA) {
            throw new EstadoInvalidoException("La reserva no está activa y no se puede modificar");
        }
        return reserva;
    }

    private ReservaResponse aResponse(Reserva reserva, String tituloLibro, String nombreCliente) {
        return new ReservaResponse(
                reserva.getId(),
                reserva.getLibroId(),
                tituloLibro,
                reserva.getClienteId(),
                nombreCliente,
                reserva.getFechaReserva(),
                reserva.getFechaExpiracion(),
                reserva.getEstado()
        );
    }

    private ClienteDto obtenerClienteSeguro(Long clienteId) {
        try {
            return customerClient.obtenerCliente(clienteId);
        } catch (Exception e) {
            throw new RecursoNoEncontradoException("Cliente no encontrado con id: " + clienteId);
        }
    }

    private String obtenerTituloSeguro(Long libroId) {
        try {
            return catalogClient.obtenerLibro(libroId).getTitulo();
        } catch (Exception e) {
            return null;
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
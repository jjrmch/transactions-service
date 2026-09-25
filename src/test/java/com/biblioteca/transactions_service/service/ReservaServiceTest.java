package com.biblioteca.transactions_service.service;

import com.biblioteca.transactions_service.client.CatalogClient;
import com.biblioteca.transactions_service.client.CustomerClient;
import com.biblioteca.transactions_service.dto.AjusteStockDto;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservaServiceTest {

    @Mock
    private ReservaRepository reservaRepository;

    @Mock
    private AlquilerRepository alquilerRepository;

    @Mock
    private CatalogClient catalogClient;

    @Mock
    private CustomerClient customerClient;

    @InjectMocks
    private ReservaService reservaService;

    @BeforeEach
    void configurarReglas() {
        ReflectionTestUtils.setField(reservaService, "horasExpiracion", 48L);
        ReflectionTestUtils.setField(reservaService, "diasPrestamo", 15);
    }

    private LibroDto libro(Integer stock) {
        LibroDto libro = new LibroDto();
        libro.setId(1L);
        libro.setTitulo("El Quijote");
        libro.setStock(stock);
        return libro;
    }

    private ClienteDto cliente() {
        ClienteDto cliente = new ClienteDto();
        cliente.setId(2L);
        cliente.setNombre("Ana");
        return cliente;
    }

    private ReservaRequest request() {
        ReservaRequest request = new ReservaRequest();
        request.setLibroId(1L);
        request.setClienteId(2L);
        return request;
    }

    private Reserva reservaActiva() {
        Reserva reserva = new Reserva();
        reserva.setId(5L);
        reserva.setLibroId(1L);
        reserva.setClienteId(2L);
        reserva.setFechaReserva(LocalDateTime.now());
        reserva.setFechaExpiracion(LocalDateTime.now().plusHours(48));
        reserva.setEstado(EstadoReserva.ACTIVA);
        return reserva;
    }

    @Test
    void reservarCreaUnaReservaActivaConSuExpiracion() {
        when(catalogClient.obtenerLibro(1L)).thenReturn(libro(0));
        when(customerClient.obtenerCliente(2L)).thenReturn(cliente());
        when(reservaRepository.existsByClienteIdAndLibroIdAndEstado(2L, 1L, EstadoReserva.ACTIVA)).thenReturn(false);
        when(reservaRepository.save(any(Reserva.class))).thenAnswer(inv -> inv.getArgument(0));

        ReservaResponse response = reservaService.reservar(request());

        assertEquals(EstadoReserva.ACTIVA, response.getEstado());
        assertEquals(LocalDateTime.now().plusHours(48).toLocalDate(),
                response.getFechaExpiracion().toLocalDate());
    }

    @Test
    void noSePuedeReservarDosVecesElMismoLibro() {
        when(catalogClient.obtenerLibro(1L)).thenReturn(libro(0));
        when(customerClient.obtenerCliente(2L)).thenReturn(cliente());
        when(reservaRepository.existsByClienteIdAndLibroIdAndEstado(2L, 1L, EstadoReserva.ACTIVA)).thenReturn(true);

        assertThrows(EstadoInvalidoException.class, () -> reservaService.reservar(request()));
        verify(reservaRepository, never()).save(any());
    }

    @Test
    void confirmarUnaReservaDescuentaStockYCreaElAlquiler() {
        Reserva reserva = reservaActiva();
        when(reservaRepository.findById(5L)).thenReturn(Optional.of(reserva));
        when(catalogClient.obtenerLibro(1L)).thenReturn(libro(1));
        when(alquilerRepository.save(any(Alquiler.class))).thenAnswer(inv -> inv.getArgument(0));
        when(reservaRepository.save(any(Reserva.class))).thenAnswer(inv -> inv.getArgument(0));

        ReservaResponse response = reservaService.confirmar(5L);

        assertEquals(EstadoReserva.CUMPLIDA, response.getEstado());
        verify(catalogClient).ajustarStock(1L, new AjusteStockDto(-1));
        ArgumentCaptor<Alquiler> alquiler = ArgumentCaptor.forClass(Alquiler.class);
        verify(alquilerRepository).save(alquiler.capture());
        assertEquals(EstadoAlquiler.ACTIVO, alquiler.getValue().getEstado());
    }

    @Test
    void confirmarSinStockLanzaConflicto() {
        Reserva reserva = reservaActiva();
        when(reservaRepository.findById(5L)).thenReturn(Optional.of(reserva));
        when(catalogClient.obtenerLibro(1L)).thenReturn(libro(0));

        assertThrows(EstadoInvalidoException.class, () -> reservaService.confirmar(5L));
        verify(alquilerRepository, never()).save(any());
    }

    @Test
    void confirmarUnaReservaNoActivaLanzaConflicto() {
        Reserva reserva = reservaActiva();
        reserva.setEstado(EstadoReserva.CANCELADA);
        when(reservaRepository.findById(5L)).thenReturn(Optional.of(reserva));

        assertThrows(EstadoInvalidoException.class, () -> reservaService.confirmar(5L));
    }

    @Test
    void confirmarUnaReservaInexistenteLanza404() {
        when(reservaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class, () -> reservaService.confirmar(99L));
    }

    @Test
    void cancelarUnaReservaActivaLaDejaCancelada() {
        Reserva reserva = reservaActiva();
        when(reservaRepository.findById(5L)).thenReturn(Optional.of(reserva));
        when(reservaRepository.save(any(Reserva.class))).thenAnswer(inv -> inv.getArgument(0));

        ReservaResponse response = reservaService.cancelar(5L);

        assertEquals(EstadoReserva.CANCELADA, response.getEstado());
    }

    @Test
    void materializarProximaSinReservasNoHaceNada() {
        when(reservaRepository.findFirstByLibroIdAndEstadoOrderByFechaReservaAsc(1L, EstadoReserva.ACTIVA))
                .thenReturn(Optional.empty());

        assertTrue(reservaService.materializarProxima(1L).isEmpty());
    }

    @Test
    void materializarProximaConvierteLaReservaEnAlquiler() {
        when(reservaRepository.findFirstByLibroIdAndEstadoOrderByFechaReservaAsc(1L, EstadoReserva.ACTIVA))
                .thenReturn(Optional.of(reservaActiva()));
        when(catalogClient.obtenerLibro(1L)).thenReturn(libro(1));
        when(alquilerRepository.save(any(Alquiler.class))).thenAnswer(inv -> inv.getArgument(0));
        when(reservaRepository.save(any(Reserva.class))).thenAnswer(inv -> inv.getArgument(0));

        assertTrue(reservaService.materializarProxima(1L).isPresent());
        verify(catalogClient).ajustarStock(1L, new AjusteStockDto(-1));
    }

    @Test
    void expirarReservasMarcaLasVencidas() {
        Reserva vencida = reservaActiva();
        vencida.setFechaExpiracion(LocalDateTime.now().minusHours(1));
        when(reservaRepository.findByEstadoAndFechaExpiracionBefore(any(), any()))
                .thenReturn(List.of(vencida));

        reservaService.expirarReservas();

        assertEquals(EstadoReserva.EXPIRADA, vencida.getEstado());
        verify(reservaRepository).save(vencida);
    }
}

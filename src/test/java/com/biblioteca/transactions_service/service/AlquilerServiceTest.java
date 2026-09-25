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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlquilerServiceTest {

    @Mock
    private AlquilerRepository alquilerRepository;

    @Mock
    private CatalogClient catalogClient;

    @Mock
    private CustomerClient customerClient;

    @Mock
    private MultaService multaService;

    @Mock
    private ReservaService reservaService;

    @InjectMocks
    private AlquilerService alquilerService;

    @BeforeEach
    void configurarReglasDePrestamo() {
        ReflectionTestUtils.setField(alquilerService, "diasPrestamo", 15);
        ReflectionTestUtils.setField(alquilerService, "diasRenovacion", 7);
        ReflectionTestUtils.setField(alquilerService, "maxRenovaciones", 2);
    }

    private LibroDto libro() {
        LibroDto libro = new LibroDto();
        libro.setId(1L);
        libro.setTitulo("El Quijote");
        libro.setPrecio(20.0);
        return libro;
    }

    private ClienteDto cliente() {
        ClienteDto cliente = new ClienteDto();
        cliente.setId(2L);
        cliente.setNombre("Ana");
        return cliente;
    }

    private AlquilerRequest request() {
        AlquilerRequest request = new AlquilerRequest();
        request.setLibroId(1L);
        request.setClienteId(2L);
        return request;
    }

    private Alquiler alquiler(LocalDateTime fechaLimite, int renovaciones) {
        Alquiler alquiler = new Alquiler();
        alquiler.setId(10L);
        alquiler.setLibroId(1L);
        alquiler.setClienteId(2L);
        alquiler.setFechaAlquiler(LocalDateTime.now().minusDays(1));
        alquiler.setFechaLimiteDevolucion(fechaLimite);
        alquiler.setEstado(EstadoAlquiler.ACTIVO);
        alquiler.setRenovaciones(renovaciones);
        return alquiler;
    }

    @Test
    void alquilarCreaElPrestamoYDescuentaStock() {
        when(catalogClient.obtenerLibro(1L)).thenReturn(libro());
        when(customerClient.obtenerCliente(2L)).thenReturn(cliente());
        when(multaService.tieneMultasPendientes(2L)).thenReturn(false);
        when(alquilerRepository.save(any(Alquiler.class))).thenAnswer(inv -> inv.getArgument(0));

        AlquilerResponse response = alquilerService.alquilar(request());

        assertEquals(EstadoAlquiler.ACTIVO, response.getEstado());
        assertEquals(0, response.getRenovaciones());
        assertEquals(LocalDateTime.now().plusDays(15).toLocalDate(),
                response.getFechaLimiteDevolucion().toLocalDate());
        verify(catalogClient).ajustarStock(1L, new AjusteStockDto(-1));
    }

    @Test
    void unClienteConMultasPendientesNoPuedeAlquilar() {
        when(catalogClient.obtenerLibro(1L)).thenReturn(libro());
        when(customerClient.obtenerCliente(2L)).thenReturn(cliente());
        when(multaService.tieneMultasPendientes(2L)).thenReturn(true);

        assertThrows(EstadoInvalidoException.class, () -> alquilerService.alquilar(request()));

        verify(catalogClient, never()).ajustarStock(any(), any());
        verify(alquilerRepository, never()).save(any());
    }

    @Test
    void devolverElLibroSubeElStockYPasaPorMultaYReserva() {
        Alquiler alquiler = alquiler(LocalDateTime.now().plusDays(5), 0);
        when(alquilerRepository.findById(10L)).thenReturn(Optional.of(alquiler));
        when(alquilerRepository.save(any(Alquiler.class))).thenAnswer(inv -> inv.getArgument(0));

        AlquilerResponse response = alquilerService.devolver(10L);

        assertEquals(EstadoAlquiler.DEVUELTO, response.getEstado());
        verify(catalogClient).ajustarStock(1L, new AjusteStockDto(1));
        verify(multaService).generarMulta(alquiler);
        verify(reservaService).materializarProxima(1L);
    }

    @Test
    void noSePuedeDevolverDosVecesElMismoAlquiler() {
        Alquiler alquiler = alquiler(LocalDateTime.now().plusDays(5), 0);
        alquiler.setEstado(EstadoAlquiler.DEVUELTO);
        when(alquilerRepository.findById(10L)).thenReturn(Optional.of(alquiler));

        assertThrows(EstadoInvalidoException.class, () -> alquilerService.devolver(10L));
        verify(catalogClient, never()).ajustarStock(any(), any());
    }

    @Test
    void devolverUnAlquilerInexistenteLanza404() {
        when(alquilerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class, () -> alquilerService.devolver(99L));
    }

    @Test
    void renovarAmpliaElPlazoYSumaUnaRenovacion() {
        Alquiler alquiler = alquiler(LocalDateTime.now().plusDays(2), 0);
        when(alquilerRepository.findById(10L)).thenReturn(Optional.of(alquiler));
        when(alquilerRepository.save(any(Alquiler.class))).thenAnswer(inv -> inv.getArgument(0));

        AlquilerResponse response = alquilerService.renovar(10L);

        assertEquals(1, response.getRenovaciones());
        assertEquals(LocalDateTime.now().plusDays(9).toLocalDate(),
                response.getFechaLimiteDevolucion().toLocalDate());
    }

    @Test
    void noSePuedeRenovarMasAllaDelMaximo() {
        Alquiler alquiler = alquiler(LocalDateTime.now().plusDays(2), 2);
        when(alquilerRepository.findById(10L)).thenReturn(Optional.of(alquiler));

        assertThrows(EstadoInvalidoException.class, () -> alquilerService.renovar(10L));
        verify(alquilerRepository, never()).save(any());
    }

    @Test
    void noSePuedeRenovarUnAlquilerVencido() {
        Alquiler alquiler = alquiler(LocalDateTime.now().minusDays(1), 0);
        when(alquilerRepository.findById(10L)).thenReturn(Optional.of(alquiler));

        assertThrows(EstadoInvalidoException.class, () -> alquilerService.renovar(10L));
    }

    @Test
    void noSePuedeRenovarUnAlquilerYaDevuelto() {
        Alquiler alquiler = alquiler(LocalDateTime.now().plusDays(2), 0);
        alquiler.setEstado(EstadoAlquiler.DEVUELTO);
        when(alquilerRepository.findById(10L)).thenReturn(Optional.of(alquiler));

        assertThrows(EstadoInvalidoException.class, () -> alquilerService.renovar(10L));
    }
}

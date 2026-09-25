package com.biblioteca.transactions_service.service;

import com.biblioteca.transactions_service.client.CustomerClient;
import com.biblioteca.transactions_service.dto.ClienteDto;
import com.biblioteca.transactions_service.dto.MultaResponse;
import com.biblioteca.transactions_service.exception.EstadoInvalidoException;
import com.biblioteca.transactions_service.exception.RecursoNoEncontradoException;
import com.biblioteca.transactions_service.model.Alquiler;
import com.biblioteca.transactions_service.model.EstadoMulta;
import com.biblioteca.transactions_service.model.Multa;
import com.biblioteca.transactions_service.repository.MultaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MultaServiceTest {

    @Mock
    private MultaRepository multaRepository;

    @Mock
    private CustomerClient customerClient;

    @InjectMocks
    private MultaService multaService;

    @BeforeEach
    void configurarPrecioYDiasDeGracia() {
        ReflectionTestUtils.setField(multaService, "precioPorDia", 1.5);
        ReflectionTestUtils.setField(multaService, "diasGracia", 0);
    }

    private Alquiler alquilerConLimite(LocalDateTime fechaLimite) {
        Alquiler alquiler = new Alquiler();
        alquiler.setId(10L);
        alquiler.setLibroId(1L);
        alquiler.setClienteId(2L);
        alquiler.setFechaLimiteDevolucion(fechaLimite);
        return alquiler;
    }

    @Test
    void generaUnaMultaPorCadaDiaDeRetraso() {
        when(multaRepository.save(any(Multa.class))).thenAnswer(inv -> inv.getArgument(0));

        Multa multa = multaService.generarMulta(alquilerConLimite(LocalDateTime.now().minusDays(5)));

        assertNotNull(multa);
        assertEquals(5, multa.getDiasRetraso());
        assertEquals(5 * 1.5, multa.getMonto());
        assertEquals(EstadoMulta.PENDIENTE, multa.getEstado());
        assertEquals(10L, multa.getAlquilerId());
        assertEquals(2L, multa.getClienteId());
    }

    @Test
    void devolverDentroDePlazoNoGeneraMulta() {
        Multa multa = multaService.generarMulta(alquilerConLimite(LocalDateTime.now().plusDays(3)));

        assertNull(multa);
        verify(multaRepository, never()).save(any(Multa.class));
    }

    @Test
    void losDiasDeGraciaNoCuentanComoRetraso() {
        ReflectionTestUtils.setField(multaService, "diasGracia", 2);

        Multa multa = multaService.generarMulta(alquilerConLimite(LocalDateTime.now().minusDays(1)));

        assertNull(multa);
    }

    @Test
    void sinFechaLimiteNoSeGeneraMulta() {
        assertNull(multaService.generarMulta(alquilerConLimite(null)));
        verify(multaRepository, never()).save(any(Multa.class));
    }

    @Test
    void pagarUnaMultaPendienteLaDejaPagada() {
        Multa multa = new Multa();
        multa.setId(7L);
        multa.setClienteId(2L);
        multa.setEstado(EstadoMulta.PENDIENTE);
        when(multaRepository.findById(7L)).thenReturn(Optional.of(multa));
        when(multaRepository.save(any(Multa.class))).thenAnswer(inv -> inv.getArgument(0));

        MultaResponse response = multaService.pagar(7L);

        assertEquals(EstadoMulta.PAGADA, response.getEstado());
    }

    @Test
    void pagarUnaMultaYaPagadaLanzaConflicto() {
        Multa multa = new Multa();
        multa.setId(7L);
        multa.setEstado(EstadoMulta.PAGADA);
        when(multaRepository.findById(7L)).thenReturn(Optional.of(multa));

        assertThrows(EstadoInvalidoException.class, () -> multaService.pagar(7L));
    }

    @Test
    void pagarUnaMultaInexistenteLanza404() {
        when(multaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class, () -> multaService.pagar(99L));
    }

    @Test
    void tieneMultasPendientesDelegaEnElRepositorio() {
        when(multaRepository.existsByClienteIdAndEstado(2L, EstadoMulta.PENDIENTE)).thenReturn(true);

        assertEquals(true, multaService.tieneMultasPendientes(2L));
    }

    @Test
    void listarPorClienteRellenaElNombreDelCliente() {
        Multa multa = new Multa();
        multa.setId(1L);
        multa.setClienteId(2L);
        multa.setEstado(EstadoMulta.PENDIENTE);
        when(multaRepository.findByClienteIdOrderByFechaCreacionDesc(2L)).thenReturn(List.of(multa));
        ClienteDto cliente = new ClienteDto();
        cliente.setId(2L);
        cliente.setNombre("Ana");
        when(customerClient.obtenerCliente(2L)).thenReturn(cliente);

        List<MultaResponse> multas = multaService.listarPorCliente(2L);

        assertEquals(1, multas.size());
        assertEquals("Ana", multas.get(0).getNombreCliente());
    }
}

package com.biblioteca.transactions_service.service;

import com.biblioteca.transactions_service.client.CatalogClient;
import com.biblioteca.transactions_service.client.CustomerClient;
import com.biblioteca.transactions_service.dto.AjusteStockDto;
import com.biblioteca.transactions_service.dto.ClienteDto;
import com.biblioteca.transactions_service.dto.LibroDto;
import com.biblioteca.transactions_service.dto.VentaRequest;
import com.biblioteca.transactions_service.dto.VentaResponse;
import com.biblioteca.transactions_service.exception.RecursoNoEncontradoException;
import com.biblioteca.transactions_service.model.Venta;
import com.biblioteca.transactions_service.repository.VentaRepository;
import feign.FeignException;
import feign.Request;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VentaServiceTest {

    @Mock
    private VentaRepository ventaRepository;

    @Mock
    private CatalogClient catalogClient;

    @Mock
    private CustomerClient customerClient;

    @InjectMocks
    private VentaService ventaService;

    private VentaRequest request() {
        VentaRequest request = new VentaRequest();
        request.setLibroId(1L);
        request.setCantidad(2);
        request.setClienteId(2L);
        return request;
    }

    private LibroDto libro() {
        LibroDto libro = new LibroDto();
        libro.setId(1L);
        libro.setTitulo("El Quijote");
        libro.setPrecio(20.0);
        libro.setStock(5);
        return libro;
    }

    private ClienteDto cliente() {
        ClienteDto cliente = new ClienteDto();
        cliente.setId(2L);
        cliente.setNombre("Ana");
        return cliente;
    }

    private FeignException.NotFound clienteNoEncontrado() {
        Request request = Request.create(Request.HttpMethod.GET, "/clientes/2", Map.of(),
                (byte[]) null, (java.nio.charset.Charset) null);
        return new FeignException.NotFound("Cliente no encontrado", request, null, Map.of());
    }

    @Test
    void venderCalculaElTotalYDescuentaStock() {
        when(catalogClient.obtenerLibro(1L)).thenReturn(libro());
        when(customerClient.obtenerCliente(2L)).thenReturn(cliente());
        when(ventaRepository.save(any(Venta.class))).thenAnswer(inv -> inv.getArgument(0));

        VentaResponse response = ventaService.vender(request());

        assertEquals(40.0, response.getPrecioTotal());
        assertEquals("El Quijote", response.getTituloLibro());
        assertEquals("Ana", response.getNombreCliente());

        ArgumentCaptor<AjusteStockDto> ajuste = ArgumentCaptor.forClass(AjusteStockDto.class);
        verify(catalogClient).ajustarStock(eq(1L), ajuste.capture());
        assertEquals(-2, ajuste.getValue().getCantidad());
    }

    @Test
    void venderConClienteInexistenteLanza404SinTocarElStock() {
        when(catalogClient.obtenerLibro(1L)).thenReturn(libro());
        when(customerClient.obtenerCliente(2L)).thenThrow(clienteNoEncontrado());

        assertThrows(RecursoNoEncontradoException.class, () -> ventaService.vender(request()));

        verify(catalogClient, never()).ajustarStock(any(), any());
        verify(ventaRepository, never()).save(any(Venta.class));
    }

    @Test
    void siElCatalogoRechazaElStockNoSeRegistraLaVenta() {
        when(catalogClient.obtenerLibro(1L)).thenReturn(libro());
        when(customerClient.obtenerCliente(2L)).thenReturn(cliente());
        when(catalogClient.ajustarStock(eq(1L), any())).thenThrow(new RuntimeException("409 stock insuficiente"));

        assertThrows(RuntimeException.class, () -> ventaService.vender(request()));

        verify(ventaRepository, never()).save(any(Venta.class));
    }

    @Test
    void listarTodasRellenaElTituloYElNombreDesdeLosMicroservicios() {
        Venta venta = new Venta();
        venta.setId(1L);
        venta.setLibroId(1L);
        venta.setCantidad(2);
        venta.setPrecioTotal(40.0);
        venta.setClienteId(2L);
        venta.setFecha(LocalDateTime.now());
        when(ventaRepository.findAll()).thenReturn(List.of(venta));
        when(catalogClient.obtenerLibro(1L)).thenReturn(libro());
        when(customerClient.obtenerCliente(2L)).thenReturn(cliente());

        List<VentaResponse> ventas = ventaService.listarTodas();

        assertEquals(1, ventas.size());
        assertEquals("El Quijote", ventas.get(0).getTituloLibro());
        assertEquals("Ana", ventas.get(0).getNombreCliente());
    }

    @Test
    void listarTodasNoFallaSiUnServicioRemotoNoResponde() {
        Venta venta = new Venta();
        venta.setId(1L);
        venta.setLibroId(1L);
        venta.setCantidad(1);
        venta.setPrecioTotal(20.0);
        venta.setClienteId(2L);
        venta.setFecha(LocalDateTime.now());
        when(ventaRepository.findAll()).thenReturn(List.of(venta));
        when(catalogClient.obtenerLibro(1L)).thenThrow(new RuntimeException("catalog caido"));
        when(customerClient.obtenerCliente(2L)).thenThrow(new RuntimeException("customer caido"));

        List<VentaResponse> ventas = ventaService.listarTodas();

        assertEquals(1, ventas.size());
        assertEquals(null, ventas.get(0).getTituloLibro());
        assertEquals(null, ventas.get(0).getNombreCliente());
    }
}

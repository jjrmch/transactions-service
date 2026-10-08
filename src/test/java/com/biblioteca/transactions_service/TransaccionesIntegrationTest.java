package com.biblioteca.transactions_service;

import com.biblioteca.transactions_service.client.CatalogClient;
import com.biblioteca.transactions_service.client.CustomerClient;
import com.biblioteca.transactions_service.dto.ClienteDto;
import com.biblioteca.transactions_service.dto.LibroDto;
import com.biblioteca.transactions_service.model.Alquiler;
import com.biblioteca.transactions_service.model.EstadoAlquiler;
import com.biblioteca.transactions_service.model.EstadoMulta;
import com.biblioteca.transactions_service.model.EstadoReserva;
import com.biblioteca.transactions_service.model.Multa;
import com.biblioteca.transactions_service.model.Reserva;
import com.biblioteca.transactions_service.repository.AlquilerRepository;
import com.biblioteca.transactions_service.repository.MultaRepository;
import com.biblioteca.transactions_service.repository.ReservaRepository;
import com.biblioteca.transactions_service.support.TestJwtFactory;
import feign.FeignException;
import feign.Request;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class TransaccionesIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AlquilerRepository alquilerRepository;

    @Autowired
    private ReservaRepository reservaRepository;

    @Autowired
    private MultaRepository multaRepository;

    @MockitoBean
    private CatalogClient catalogClient;

    @MockitoBean
    private CustomerClient customerClient;

    private final String tokenAdmin = TestJwtFactory.token("admin@test.com", "ADMIN");
    private final String tokenCliente = TestJwtFactory.token("cliente@test.com", "CLIENTE");

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

    private LibroDto libro(Long id, int stock) {
        LibroDto libro = new LibroDto();
        libro.setId(id);
        libro.setTitulo("Libro " + id);
        libro.setPrecio(20.0);
        libro.setStock(stock);
        return libro;
    }

    private ClienteDto cliente(Long id) {
        ClienteDto cliente = new ClienteDto();
        cliente.setId(id);
        cliente.setNombre("Cliente " + id);
        return cliente;
    }

    private Long idUnico() {
        return ThreadLocalRandom.current().nextLong(100_000, 1_000_000);
    }

    private Alquiler guardarAlquiler(Long libroId, Long clienteId, EstadoAlquiler estado) {
        Alquiler alquiler = new Alquiler();
        alquiler.setLibroId(libroId);
        alquiler.setClienteId(clienteId);
        alquiler.setFechaAlquiler(LocalDateTime.now());
        alquiler.setFechaLimiteDevolucion(LocalDateTime.now().plusDays(15));
        alquiler.setEstado(estado);
        alquiler.setRenovaciones(0);
        return alquilerRepository.save(alquiler);
    }

    private Reserva guardarReserva(Long libroId, Long clienteId) {
        Reserva reserva = new Reserva();
        reserva.setLibroId(libroId);
        reserva.setClienteId(clienteId);
        reserva.setFechaReserva(LocalDateTime.now());
        reserva.setFechaExpiracion(LocalDateTime.now().plusHours(48));
        reserva.setEstado(EstadoReserva.ACTIVA);
        return reservaRepository.save(reserva);
    }

    private Multa guardarMulta(Long alquilerId, Long clienteId, EstadoMulta estado) {
        Multa multa = new Multa();
        multa.setAlquilerId(alquilerId);
        multa.setClienteId(clienteId);
        multa.setMonto(5.0);
        multa.setDiasRetraso(5);
        multa.setMotivo("Devolución fuera de plazo");
        multa.setFechaCreacion(LocalDateTime.now());
        multa.setEstado(estado);
        return multaRepository.save(multa);
    }

    private FeignException.NotFound clienteNoEncontrado(Long clienteId) {
        Request request = Request.create(Request.HttpMethod.GET, "/clientes/" + clienteId, Map.of(),
                (byte[]) null, (Charset) null);
        return new FeignException.NotFound("Cliente no encontrado", request, null, Map.of());
    }

    private FeignException.Conflict stockInsuficiente() {
        Request request = Request.create(Request.HttpMethod.PATCH, "/libros/1/stock", Map.of(),
                (byte[]) null, (Charset) null);
        byte[] body = "{\"mensaje\":\"No hay stock suficiente para el libro\"}".getBytes(StandardCharsets.UTF_8);
        return new FeignException.Conflict("conflict", request, body, Map.of());
    }

    @Test
    void laVentaRecorreCatalogYCustomerConElTokenDelUsuario() throws Exception {
        when(catalogClient.obtenerLibro(1L)).thenReturn(libro());
        when(customerClient.obtenerCliente(2L)).thenReturn(cliente());
        when(catalogClient.ajustarStock(eq(1L), any())).thenReturn(libro());

        mockMvc.perform(post("/ventas")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"libroId":1,"cantidad":2,"clienteId":2}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.precioTotal").value(40.0))
                .andExpect(jsonPath("$.tituloLibro").value("El Quijote"))
                .andExpect(jsonPath("$.nombreCliente").value("Ana"));

        verify(catalogClient).ajustarStock(eq(1L), argThat(ajuste -> ajuste.getCantidad() == -2));
    }

    @Test
    void venderSinTokenDevuelve401() throws Exception {
        mockMvc.perform(post("/ventas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"libroId":1,"cantidad":1,"clienteId":2}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void venderConRolClienteDevuelve403() throws Exception {
        mockMvc.perform(post("/ventas")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenCliente)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"libroId":1,"cantidad":1,"clienteId":2}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void listarVentasExigeTokenDePersonal() throws Exception {
        mockMvc.perform(get("/ventas")).andExpect(status().isUnauthorized());

        mockMvc.perform(get("/ventas").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
                .andExpect(status().isOk());
    }

    @Test
    void listarMultasConAdminDevuelve200() throws Exception {
        mockMvc.perform(get("/multas").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void venderConClienteInexistenteDevuelve404() throws Exception {
        Long libroId = idUnico();
        Long clienteId = idUnico();
        when(catalogClient.obtenerLibro(libroId)).thenReturn(libro(libroId, 5));
        when(customerClient.obtenerCliente(clienteId)).thenThrow(clienteNoEncontrado(clienteId));

        mockMvc.perform(post("/ventas")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"libroId":%d,"cantidad":1,"clienteId":%d}
                                """.formatted(libroId, clienteId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value(containsString("Cliente no encontrado")));
    }

    @Test
    void venderConStockInsuficientePropagaEl409DelCatalogo() throws Exception {
        Long libroId = idUnico();
        Long clienteId = idUnico();
        when(catalogClient.obtenerLibro(libroId)).thenReturn(libro(libroId, 1));
        when(customerClient.obtenerCliente(clienteId)).thenReturn(cliente(clienteId));
        when(catalogClient.ajustarStock(eq(libroId), any())).thenThrow(stockInsuficiente());

        mockMvc.perform(post("/ventas")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"libroId":%d,"cantidad":1,"clienteId":%d}
                                """.formatted(libroId, clienteId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("No hay stock suficiente para el libro"));
    }

    @Test
    void alquilarCreaElAlquilerYDescuentaStock() throws Exception {
        Long libroId = idUnico();
        Long clienteId = idUnico();
        when(catalogClient.obtenerLibro(libroId)).thenReturn(libro(libroId, 5));
        when(customerClient.obtenerCliente(clienteId)).thenReturn(cliente(clienteId));
        when(catalogClient.ajustarStock(eq(libroId), any())).thenReturn(libro(libroId, 4));

        mockMvc.perform(post("/alquileres")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"libroId":%d,"clienteId":%d}
                                """.formatted(libroId, clienteId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("ACTIVO"))
                .andExpect(jsonPath("$.renovaciones").value(0))
                .andExpect(jsonPath("$.tituloLibro").value("Libro " + libroId))
                .andExpect(jsonPath("$.nombreCliente").value("Cliente " + clienteId));

        verify(catalogClient).ajustarStock(eq(libroId), argThat(ajuste -> ajuste.getCantidad() == -1));
    }

    @Test
    void renovarUnAlquilerAmpliaLaFechaYCuentaLaRenovacion() throws Exception {
        Long libroId = idUnico();
        Long clienteId = idUnico();
        Alquiler alquiler = guardarAlquiler(libroId, clienteId, EstadoAlquiler.ACTIVO);
        when(catalogClient.obtenerLibro(libroId)).thenReturn(libro(libroId, 5));
        when(customerClient.obtenerCliente(clienteId)).thenReturn(cliente(clienteId));

        mockMvc.perform(post("/alquileres/" + alquiler.getId() + "/renovacion")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.renovaciones").value(1))
                .andExpect(jsonPath("$.fechaLimiteDevolucion").exists());
    }

    @Test
    void renovarUnAlquilerYaDevueltoDevuelve409() throws Exception {
        Alquiler alquiler = guardarAlquiler(idUnico(), idUnico(), EstadoAlquiler.DEVUELTO);

        mockMvc.perform(post("/alquileres/" + alquiler.getId() + "/renovacion")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value(containsString("renovar")));
    }

    @Test
    void devolverUnAlquilerActivoSumaStockYLoMarcaComoDevuelto() throws Exception {
        Long libroId = idUnico();
        Long clienteId = idUnico();
        Alquiler alquiler = guardarAlquiler(libroId, clienteId, EstadoAlquiler.ACTIVO);
        when(catalogClient.ajustarStock(eq(libroId), any())).thenReturn(libro(libroId, 6));
        when(customerClient.obtenerCliente(clienteId)).thenReturn(cliente(clienteId));

        mockMvc.perform(post("/alquileres/" + alquiler.getId() + "/devolucion")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("DEVUELTO"))
                .andExpect(jsonPath("$.fechaDevolucion").exists());

        verify(catalogClient).ajustarStock(eq(libroId), argThat(ajuste -> ajuste.getCantidad() == 1));
        assertTrue(multaRepository.findByClienteIdOrderByFechaCreacionDesc(clienteId).isEmpty());
    }

    @Test
    void devolverUnAlquilerYaDevueltoDevuelve409() throws Exception {
        Alquiler alquiler = guardarAlquiler(idUnico(), idUnico(), EstadoAlquiler.DEVUELTO);

        mockMvc.perform(post("/alquileres/" + alquiler.getId() + "/devolucion")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value(containsString("ya fue devuelto")));
    }

    @Test
    void reservarCreaLaReservaActiva() throws Exception {
        Long libroId = idUnico();
        Long clienteId = idUnico();
        when(catalogClient.obtenerLibro(libroId)).thenReturn(libro(libroId, 0));
        when(customerClient.obtenerCliente(clienteId)).thenReturn(cliente(clienteId));

        mockMvc.perform(post("/reservas")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"libroId":%d,"clienteId":%d}
                                """.formatted(libroId, clienteId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("ACTIVA"))
                .andExpect(jsonPath("$.tituloLibro").value("Libro " + libroId));
    }

    @Test
    void confirmarUnaReservaSinStockDevuelve409() throws Exception {
        Long libroId = idUnico();
        Reserva reserva = guardarReserva(libroId, idUnico());
        when(catalogClient.obtenerLibro(libroId)).thenReturn(libro(libroId, 0));

        mockMvc.perform(post("/reservas/" + reserva.getId() + "/confirmar")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value(containsString("stock")));
    }

    @Test
    void confirmarUnaReservaConStockCreaElAlquiler() throws Exception {
        Long libroId = idUnico();
        Long clienteId = idUnico();
        Reserva reserva = guardarReserva(libroId, clienteId);
        when(catalogClient.obtenerLibro(libroId)).thenReturn(libro(libroId, 3));
        when(catalogClient.ajustarStock(eq(libroId), any())).thenReturn(libro(libroId, 2));
        when(customerClient.obtenerCliente(clienteId)).thenReturn(cliente(clienteId));

        mockMvc.perform(post("/reservas/" + reserva.getId() + "/confirmar")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CUMPLIDA"));

        verify(catalogClient).ajustarStock(eq(libroId), argThat(ajuste -> ajuste.getCantidad() == -1));
    }

    @Test
    void cancelarUnaReservaLaMarcaComoCancelada() throws Exception {
        Reserva reserva = guardarReserva(idUnico(), idUnico());

        mockMvc.perform(delete("/reservas/" + reserva.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADA"));
    }

    @Test
    void listarMultasPorClienteDevuelveLasDelCliente() throws Exception {
        Long clienteId = idUnico();
        guardarMulta(idUnico(), clienteId, EstadoMulta.PENDIENTE);

        mockMvc.perform(get("/multas/cliente/" + clienteId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].clienteId").value(clienteId));
    }

    @Test
    void pagarUnaMultaLaMarcaComoPagadaYNoPermitePagarDosVeces() throws Exception {
        Multa multa = guardarMulta(idUnico(), idUnico(), EstadoMulta.PENDIENTE);

        mockMvc.perform(post("/multas/" + multa.getId() + "/pago")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PAGADA"));

        mockMvc.perform(post("/multas/" + multa.getId() + "/pago")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value(containsString("pagada")));
    }
}

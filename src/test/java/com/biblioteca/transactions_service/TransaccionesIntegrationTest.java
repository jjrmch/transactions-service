package com.biblioteca.transactions_service;

import com.biblioteca.transactions_service.client.CatalogClient;
import com.biblioteca.transactions_service.client.CustomerClient;
import com.biblioteca.transactions_service.dto.ClienteDto;
import com.biblioteca.transactions_service.dto.LibroDto;
import com.biblioteca.transactions_service.support.TestJwtFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class TransaccionesIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

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
}

package com.biblioteca.transactions_service.service;

import com.biblioteca.transactions_service.client.CatalogClient;
import com.biblioteca.transactions_service.dto.AjusteStockDto;
import com.biblioteca.transactions_service.dto.LibroDto;
import com.biblioteca.transactions_service.dto.VentaRequest;
import com.biblioteca.transactions_service.dto.VentaResponse;
import com.biblioteca.transactions_service.model.Venta;
import com.biblioteca.transactions_service.repository.VentaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class VentaService {

    private final VentaRepository ventaRepository;
    private final CatalogClient catalogClient;

    public VentaService(VentaRepository ventaRepository, CatalogClient catalogClient) {
        this.ventaRepository = ventaRepository;
        this.catalogClient = catalogClient;
    }

    @Transactional
    public VentaResponse vender(VentaRequest request) {
        // 1. Pedir el libro al catalog-service 
        LibroDto libro = catalogClient.obtenerLibro(request.getLibroId());

        // 2. Pedir al catalog que baje el stock (negativo = restar).
        //    El propio catalog valida si hay stock suficiente y responde 409 si no.
        catalogClient.ajustarStock(request.getLibroId(),
                new AjusteStockDto(-request.getCantidad()));

        // 3. Registrar la venta con el precio real del libro
        Venta venta = new Venta();
        venta.setLibroId(request.getLibroId());
        venta.setCantidad(request.getCantidad());
        venta.setPrecioTotal(libro.getPrecio() * request.getCantidad());
        venta.setCliente(request.getCliente());
        venta.setFecha(LocalDateTime.now());

        Venta guardada = ventaRepository.save(venta);
        return aResponse(guardada, libro.getTitulo());
    }

    public List<VentaResponse> listarTodas() {
    return ventaRepository.findAll()
            .stream()
            .map(venta -> {
                String titulo = obtenerTituloSeguro(venta.getLibroId());
                return aResponse(venta, titulo);
            })
            .toList();
    }

    private VentaResponse aResponse(Venta venta, String tituloLibro) {
        return new VentaResponse(
                venta.getId(),
                venta.getLibroId(),
                tituloLibro,
                venta.getCantidad(),
                venta.getPrecioTotal(),
                venta.getCliente(),
                venta.getFecha()
        );
    }

    private String obtenerTituloSeguro(Long libroId) {
    try {
        LibroDto libro = catalogClient.obtenerLibro(libroId);
        return libro.getTitulo();
    } catch (Exception e) {
        // Si el catalog no responde o el libro ya no existe,
        // devolvemos null en vez de romper el listado entero.
        return null;
    }
}
}
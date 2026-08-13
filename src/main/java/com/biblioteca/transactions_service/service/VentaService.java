package com.biblioteca.transactions_service.service;

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

    public VentaService(VentaRepository ventaRepository) {
        this.ventaRepository = ventaRepository;
    }

    @Transactional
    public VentaResponse vender(VentaRequest request) {
        // TODO (siguiente paso, con OpenFeign):
        //   1. Pedir el libro al catalog-service
        //   2. Comprobar que hay stock suficiente
        //   3. Pedir al catalog-service que baje el stock
        // Por ahora registramos la venta con un precio provisional.

        Venta venta = new Venta();
        venta.setLibroId(request.getLibroId());
        venta.setCantidad(request.getCantidad());
        venta.setPrecioTotal(0.0);   // provisional: lo calcularemos con el precio real del libro
        venta.setCliente(request.getCliente());
        venta.setFecha(LocalDateTime.now());

        return aResponse(ventaRepository.save(venta));
    }

    public List<VentaResponse> listarTodas() {
        return ventaRepository.findAll()
                .stream()
                .map(this::aResponse)
                .toList();
    }

    private VentaResponse aResponse(Venta venta) {
        return new VentaResponse(
                venta.getId(),
                venta.getLibroId(),
                null,                 // tituloLibro: lo rellenaremos pidiéndolo al catalog
                venta.getCantidad(),
                venta.getPrecioTotal(),
                venta.getCliente(),
                venta.getFecha()
        );
    }
}
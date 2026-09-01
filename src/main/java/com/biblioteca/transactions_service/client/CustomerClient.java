package com.biblioteca.transactions_service.client;

import com.biblioteca.transactions_service.dto.ClienteDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "customer-service")
public interface CustomerClient {

    @GetMapping("/clientes/{id}")
    ClienteDto obtenerCliente(@PathVariable Long id);

}
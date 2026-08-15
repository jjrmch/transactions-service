package com.biblioteca.transactions_service.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI transactionsOpenAPI() {
        Server server = new Server();
        server.setUrl("/");

        return new OpenAPI()
                .servers(List.of(server))
                .info(new Info()
                        .title("Transactions Service API")
                        .description("Gestión de ventas y alquileres de libros")
                        .version("1.0"));
    }
}

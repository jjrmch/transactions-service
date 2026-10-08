# Changelog

Todos los cambios relevantes de este proyecto se documentan en este archivo.

El formato está basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/)
y este proyecto sigue [Semantic Versioning](https://semver.org/lang/es/).

## [1.1.0] - 2026-10-08

### Añadido

- 13 tests de integración HTTP: alquiler, renovación y devolución, reservas (crear, confirmar y cancelar), multas (listado por cliente y pago) y propagación de errores remotos 404/409

### Cambiado

- Los tests de integración comparten un único contenedor PostgreSQL
- README actualizado con el total de tests (54)

## [1.0.0] - 2026-10-05

### Añadido

- Ventas: cálculo del precio total y descuento de stock en catalog-service
- Alquileres: creación de préstamos, renovación (+7 días, máximo 2) y devolución con generación automática de multa por retraso
- Reservas: cola de espera para libros sin stock, confirmación (crea el alquiler) y cancelación
- Multas: listado, filtro por cliente y registro de pago
- Comunicación con catalog-service y customer-service vía OpenFeign, propagando el `Authorization` del usuario
- Propagación al frontend de los errores remotos reales (409 por stock, 404 por cliente inexistente)
- Seguridad JWT (HS256) con roles `ADMIN` / `BIBLIOTECARIO` en todos los endpoints
- 41 tests: unitarios de la lógica de negocio y de integración con Spring Boot + MockMvc + Testcontainers (PostgreSQL)
- Documentación OpenAPI/Swagger y registro en Eureka

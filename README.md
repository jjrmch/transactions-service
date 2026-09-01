# Transactions Service

Microservicio de transacciones de la plataforma de gestión de biblioteca. Gestiona ventas, alquileres, reservas y multas. Es el servicio que más lógica tiene: no guarda los libros ni los clientes, sino que los obtiene de los otros microservicios a través de OpenFeign, por lo que funciona como orquestador entre catalog-service y customer-service.

## Qué hace

- **Ventas**: registra una venta, calcula el precio total (precio del libro x cantidad) y descuenta el stock del catálogo
- **Alquileres**: crea préstamos, permite renovarlos (+7 días) y devolverlos (al devolver se suma stock y, si hay retraso, se genera una multa)
- **Reservas**: cola de espera para libros sin stock; se pueden confirmar (crea el alquiler) o cancelar
- **Multas**: listado, filtro por cliente y registro de pago
- **Errores remotos propagados**: si el catálogo devuelve 409 por stock insuficiente o el cliente no existe (404), el error llega al frontend con el mensaje real del servicio remoto

## Stack

- Java 17
- Spring Boot 4.1
- Spring Cloud 2025.1.2 (Eureka client, LoadBalancer)
- OpenFeign (comunicación con catalog-service y customer-service)
- Spring Data JPA
- PostgreSQL
- springdoc-openapi

## Cómo ejecutarlo

Necesitas PostgreSQL y el discovery-service (Eureka) levantados. Puedes levantar todo el stack con docker-compose desde `biblioteca-deploy`, o ejecutar este servicio solo:

```bash
./mvnw spring-boot:run
```

La configuración de la base de datos se hace por variables de entorno:

| Variable | Descripción |
|---|---|
| `DB_URL` | JDBC URL de PostgreSQL (default `jdbc:postgresql://localhost:5432/transacciones`) |
| `DB_USER` | Usuario de PostgreSQL |
| `DB_PASSWORD` | Contraseña de PostgreSQL |
| `EUREKA_URL` | URL del servidor Eureka (default `http://localhost:8761/eureka/`) |

El servicio necesita que catalog-service y customer-service estén registrados en Eureka, porque los resuelve por nombre con LoadBalancer.

## Endpoints

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/ventas` | Lista ventas (con título del libro y nombre del cliente) |
| POST | `/ventas` | Registra una venta y descuenta stock |
| GET | `/alquileres` | Lista alquileres |
| POST | `/alquileres` | Crea un alquiler (descuenta 1 de stock) |
| POST | `/alquileres/{id}/renovacion` | Renueva un alquiler +7 días |
| POST | `/alquileres/{id}/devolucion` | Devuelve el libro (suma stock y genera multa si hay retraso) |
| GET | `/reservas` | Lista reservas |
| GET | `/reservas/cliente/{clienteId}` | Reservas de un cliente |
| POST | `/reservas` | Crea una reserva para un libro sin stock |
| POST | `/reservas/{id}/confirmar` | Confirma la reserva (crea el alquiler) |
| DELETE | `/reservas/{id}` | Cancela la reserva |
| GET | `/multas` | Lista multas |
| GET | `/multas/cliente/{clienteId}` | Multas de un cliente |
| POST | `/multas/{id}/pago` | Registra el pago de una multa |

## Detalles de implementación

- La venta valida primero que el cliente exista y después descuenta stock; si el catálogo rechaza la operación (stock insuficiente), la venta no se registra
- Los listados hacen llamadas Feign "seguras": si un libro o cliente ya no existe, se muestra el dato como vacío en lugar de romper la lista
- Al arrancar, el servicio refresca el registro de Eureka cada 5 segundos y desactiva la caché negativa del LoadBalancer para evitar el 503 "No servers available"

## Parte de un sistema más grande

La plataforma completa se compone de:

- [discovery-service](https://github.com/jjrmch/discovery-service) — servidor Eureka
- [gateway-service](https://github.com/jjrmch/gateway-service) — API Gateway (punto de entrada, `localhost:8080`)
- [catalog-service](https://github.com/jjrmch/catalog-service) — catálogo de libros y stock
- [customer-service](https://github.com/jjrmch/customer-service) — clientes
- [biblioteca-frontend](https://github.com/jjrmch/biblioteca-frontend) — panel web en React
- [biblioteca-deploy](https://github.com/jjrmch/biblioteca-deploy) — docker-compose con el stack completo

## Por mejorar

- No hay tests de negocio todavía, solo el test de contexto de Spring.
- La lógica de multas por retraso depende de que el servicio se ejecute en la zona horaria local.

## Licencia

MIT

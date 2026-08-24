package com.biblioteca.transactions_service.exception;

import com.biblioteca.transactions_service.dto.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private final ObjectMapper objectMapper;

    public GlobalExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // Maneja el "recurso no encontrado" tipo 404
    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<ErrorResponse> manejarNoEncontrado(RecursoNoEncontradoException ex) {
        ErrorResponse error = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.NOT_FOUND.value(),
                ex.getMessage(),
                null
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    // Maneja los errores de validación (@Valid) tipo 400
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> manejarValidacion(MethodArgumentNotValidException ex) {
        Map<String, String> errores = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errores.put(error.getField(), error.getDefaultMessage());
        }

        ErrorResponse respuesta = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                "Error de validación",
                errores
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(respuesta);
    }

    @ExceptionHandler(StockInsuficienteException.class)
    public ResponseEntity<ErrorResponse> manejarStockInsuficiente(StockInsuficienteException ex) {
        ErrorResponse error = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.CONFLICT.value(),
                ex.getMessage(),
                null
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(EstadoInvalidoException.class)
    public ResponseEntity<ErrorResponse> manejarEstadoInvalido(EstadoInvalidoException ex) {
        ErrorResponse error = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.CONFLICT.value(),
                ex.getMessage(),
                null
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    // Propaga el error de catalog/customer tal cual (404, 409, ...)
    @ExceptionHandler(FeignException.class)
    public ResponseEntity<ErrorResponse> manejarErrorFeign(FeignException ex) {
        HttpStatus status = HttpStatus.resolve(ex.status());
        if (status == null) {
            status = HttpStatus.BAD_GATEWAY;
        }

        ErrorResponse error = new ErrorResponse(
                LocalDateTime.now(),
                status.value(),
                extraerMensajeRemoto(ex),
                null
        );
        return ResponseEntity.status(status).body(error);
    }

    // El servicio remoto devuelve un ErrorResponse; intentamos extraer su mensaje real
    private String extraerMensajeRemoto(FeignException ex) {
        // 1) Intentar parsear el body directo de la respuesta
        String cuerpo = obtenerCuerpo(ex);
        ErrorResponse remoto = parsearErrorResponse(cuerpo);
        if (remoto != null && remoto.getMensaje() != null) {
            return remoto.getMensaje();
        }

        // 2) Fallback: Feign embebe el JSON de la respuesta dentro del mensaje
        String jsonEmbebido = extraerJsonDelMensaje(ex.getMessage());
        if (jsonEmbebido != null && !jsonEmbebido.equals(cuerpo)) {
            ErrorResponse remoto2 = parsearErrorResponse(jsonEmbebido);
            if (remoto2 != null && remoto2.getMensaje() != null) {
                return remoto2.getMensaje();
            }
        }

        return ex.getMessage();
    }

    private String obtenerCuerpo(FeignException ex) {
        try {
            return ex.contentUTF8();
        } catch (Exception ignored) {
            return null;
        }
    }

    private ErrorResponse parsearErrorResponse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, ErrorResponse.class);
        } catch (Exception e) {
            return null;
        }
    }

    private String extraerJsonDelMensaje(String mensaje) {
        if (mensaje == null) {
            return null;
        }
        int inicio = mensaje.indexOf('{');
        int fin = mensaje.lastIndexOf('}');
        if (inicio >= 0 && fin > inicio) {
            return mensaje.substring(inicio, fin + 1);
        }
        return null;
    }
}
package edu.biblioteca;

import jakarta.servlet.http.*;
import jakarta.validation.ConstraintViolationException;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import com.fasterxml.jackson.databind.ObjectMapper;

@RestControllerAdvice
class Errores {
    private final ObjectMapper json;

    Errores(ObjectMapper json) {
        this.json = json;
    }

    record Problema(int estado, String mensaje, Map<String, String> campos) {}

    @ExceptionHandler(Fallo.class)
    ResponseEntity<Problema> negocio(Fallo e) {
        return respuesta(e.estado, e.getMessage(), Map.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Problema> campos(MethodArgumentNotValidException e) {
        Map<String, String> campos = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(error -> campos.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return respuesta(400, "Datos inválidos", campos);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            HandlerMethodValidationException.class, ConstraintViolationException.class})
    ResponseEntity<Problema> formato() {
        return respuesta(400, "Formato o parámetros inválidos", Map.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Problema> integridad() {
        return respuesta(409, "Los datos entran en conflicto con un registro existente", Map.of());
    }

    void sinToken(HttpServletRequest req, HttpServletResponse res, AuthenticationException ex) throws IOException {
        res.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        escribir(res, 401, "Se requiere un token válido");
    }

    void escribir(HttpServletResponse res, int estado, String mensaje) throws IOException {
        res.setStatus(estado);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.getOutputStream().write(json.writeValueAsBytes(new Problema(estado, mensaje, Map.of())));
    }

    private ResponseEntity<Problema> respuesta(int estado, String mensaje, Map<String, String> campos) {
        return ResponseEntity.status(estado).body(new Problema(estado, mensaje, campos));
    }
}

class Fallo extends RuntimeException {
    final int estado;

    Fallo(int estado, String mensaje) {
        super(mensaje);
        this.estado = estado;
    }
}

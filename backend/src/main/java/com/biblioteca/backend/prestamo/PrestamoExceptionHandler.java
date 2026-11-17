package com.biblioteca.backend.prestamo;

import com.biblioteca.backend.catalogo.LibroNoEncontradoException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Errores de préstamos con el formato de siempre (message y errors) más un código para el frontend. */
@RestControllerAdvice(assignableTypes = PrestamoController.class)
public class PrestamoExceptionHandler {

    public record ErrorResponse(String message, String codigo, Map<String, String> errors) {}

    private static final String DATOS_NO_VALIDOS = "Revisa los datos del préstamo.";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> datosNoValidos(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return ResponseEntity.badRequest()
            .body(new ErrorResponse(DATOS_NO_VALIDOS, "DATOS_NO_VALIDOS", errors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> peticionNoValida() {
        return ResponseEntity.badRequest()
            .body(new ErrorResponse(DATOS_NO_VALIDOS, "DATOS_NO_VALIDOS", Map.of()));
    }

    @ExceptionHandler(LibroNoEncontradoException.class)
    public ResponseEntity<ErrorResponse> libroNoEncontrado(LibroNoEncontradoException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse(ex.getMessage(), "LIBRO_NO_ENCONTRADO", Map.of()));
    }

    @ExceptionHandler(PrestamoRechazadoException.class)
    public ResponseEntity<ErrorResponse> prestamoRechazado(PrestamoRechazadoException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ErrorResponse(ex.getMessage(), ex.getCodigo(), Map.of()));
    }

    @ExceptionHandler(PrestamoNoEncontradoException.class)
    public ResponseEntity<ErrorResponse> prestamoNoEncontrado(PrestamoNoEncontradoException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse(ex.getMessage(), "PRESTAMO_NO_ENCONTRADO", Map.of()));
    }

    @ExceptionHandler(PrestamoYaDevueltoException.class)
    public ResponseEntity<ErrorResponse> prestamoYaDevuelto(PrestamoYaDevueltoException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ErrorResponse(ex.getMessage(), "PRESTAMO_YA_DEVUELTO", Map.of()));
    }
}

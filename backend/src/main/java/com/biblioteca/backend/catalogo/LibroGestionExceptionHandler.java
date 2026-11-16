package com.biblioteca.backend.catalogo;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Errores de la gestión del catálogo (#51), con el mismo formato que el registro. */
@RestControllerAdvice(assignableTypes = LibroGestionController.class)
public class LibroGestionExceptionHandler {

    public record ErrorResponse(String message, Map<String, String> errors) {}

    @ExceptionHandler(MethodArgumentNotValidException.class)           // datos no válidos -> 400
    public ResponseEntity<ErrorResponse> datosNoValidos(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            if ("NotBlank".equals(error.getCode())) {
                errors.put(error.getField(), error.getDefaultMessage());
            } else {
                errors.putIfAbsent(error.getField(), error.getDefaultMessage());
            }
        }
        return ResponseEntity.badRequest()
            .body(new ErrorResponse("Revisa los datos del libro.", errors));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class,
                       MethodArgumentTypeMismatchException.class})  // JSON roto o id no numérico -> 400
    public ResponseEntity<ErrorResponse> peticionNoValida() {
        return ResponseEntity.badRequest()
            .body(new ErrorResponse("Revisa los datos del libro.", Map.of()));
    }

    @ExceptionHandler(LibroNoEncontradoException.class)                 // -> 404
    public ResponseEntity<ErrorResponse> noEncontrado(LibroNoEncontradoException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse(ex.getMessage(), Map.of()));
    }

    @ExceptionHandler(IsbnDuplicadoException.class)                     // -> 409
    public ResponseEntity<ErrorResponse> isbnDuplicado(IsbnDuplicadoException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ErrorResponse(ex.getMessage(),
                Map.of("isbn", "Este ISBN ya está registrado en otro libro.")));
    }
}

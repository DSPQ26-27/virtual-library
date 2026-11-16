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

/** Mismo formato de errores que la gestión de libros: message y errors. */
@RestControllerAdvice(assignableTypes = EjemplarGestionController.class)
public class EjemplarGestionExceptionHandler {

    public record ErrorResponse(String message, Map<String, String> errors) {}

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> datosNoValidos(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return ResponseEntity.badRequest()
            .body(new ErrorResponse("Revisa los datos del ejemplar.", errors));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> peticionNoValida() {
        return ResponseEntity.badRequest()
            .body(new ErrorResponse("Revisa los datos del ejemplar.", Map.of()));
    }

    @ExceptionHandler({LibroNoEncontradoException.class, EjemplarNoEncontradoException.class})
    public ResponseEntity<ErrorResponse> noEncontrado(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse(ex.getMessage(), Map.of()));
    }

    @ExceptionHandler(CodigoEjemplarDuplicadoException.class)
    public ResponseEntity<ErrorResponse> codigoDuplicado(CodigoEjemplarDuplicadoException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ErrorResponse(ex.getMessage(),
                Map.of("codigo", "Este código ya está registrado en otro ejemplar.")));
    }

    @ExceptionHandler({EjemplarRetiradoException.class, EjemplarConPrestamoActivoException.class})
    public ResponseEntity<ErrorResponse> conflicto(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ErrorResponse(ex.getMessage(), Map.of()));
    }
}

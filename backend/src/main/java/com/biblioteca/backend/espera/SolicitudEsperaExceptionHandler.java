package com.biblioteca.backend.espera;

import com.biblioteca.backend.catalogo.LibroNoEncontradoException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = SolicitudEsperaController.class)
public class SolicitudEsperaExceptionHandler {

    public record ErrorResponse(String message, String codigo, Map<String, String> errors) {}


    @ExceptionHandler(SolicitudEsperaRechazadaException.class)
    public ResponseEntity<ErrorResponse> solicitudRechazada(SolicitudEsperaRechazadaException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(ex.getMessage(), ex.getCodigo(), Map.of()));
    }

    @ExceptionHandler(LibroNoEncontradoException.class)
    public ResponseEntity<ErrorResponse> libroNoEncontrado(LibroNoEncontradoException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(ex.getMessage(), "LIBRO_NO_ENCONTRADO", Map.of()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> datosNoValidos(MethodArgumentNotValidException ex) {
        Map<String, String> errores = new LinkedHashMap<>();
        for (var error : ex.getBindingResult().getFieldErrors()) {
            errores.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return ResponseEntity.badRequest().body(
                new ErrorResponse("Revisa los datos de la solicitud.", "DATOS_NO_VALIDOS", errores));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> peticionNoValida() {
        return ResponseEntity.badRequest().body(new ErrorResponse(
                "Revisa los datos de la solicitud.", "DATOS_NO_VALIDOS", Map.of()));
    }
}

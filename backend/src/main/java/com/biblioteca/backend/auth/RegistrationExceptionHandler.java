package com.biblioteca.backend.auth;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Formato de errores del registro según docs/contrato-registro.md. */
@RestControllerAdvice(assignableTypes = RegistrationController.class)
public class RegistrationExceptionHandler {

    public record ErrorResponse(String message, Map<String, String> errors) {}

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> invalidData(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            // Un solo mensaje por campo; si está vacío, prima "es obligatorio".
            if ("NotBlank".equals(error.getCode())) {
                errors.put(error.getField(), error.getDefaultMessage());
            } else {
                errors.putIfAbsent(error.getField(), error.getDefaultMessage());
            }
        }
        return ResponseEntity.badRequest()
            .body(new ErrorResponse("Revisa los datos del formulario.", errors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> unreadableBody() {
        // JSON mal formado: 400 sin detalles técnicos.
        return ResponseEntity.badRequest()
            .body(new ErrorResponse("Revisa los datos del formulario.", Map.of()));
    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ResponseEntity<ErrorResponse> duplicateEmail(EmailAlreadyRegisteredException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ErrorResponse(ex.getMessage(),
                Map.of("email", "Este correo ya está registrado.")));
    }
}

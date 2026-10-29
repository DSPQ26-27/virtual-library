package com.biblioteca.backend.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** DTO de entrada del registro. Reglas según docs/contrato-registro.md. */
public record RegisterRequest(
        @NotBlank(message = "El nombre es obligatorio.")
        @Size(min = 2, max = 100, message = "El nombre debe tener entre 2 y 100 caracteres.")
        String nombre,

        @NotBlank(message = "El correo es obligatorio.")
        @Email(message = "Correo inválido.")
        @Size(max = 254, message = "El correo no puede superar 254 caracteres.")
        String email,

        @NotBlank(message = "La contraseña es obligatoria.")
        @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres.")
        String password) {

    public RegisterRequest {
        // El contrato pide recortar espacios exteriores en nombre y email...
        if (nombre != null) nombre = nombre.trim();
        if (email != null) email = email.trim();
        // ...pero NO en la contraseña: los espacios forman parte de ella.
    }
}

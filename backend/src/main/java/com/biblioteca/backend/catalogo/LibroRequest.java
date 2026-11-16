package com.biblioteca.backend.catalogo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Datos bibliográficos para crear o editar un libro (#51). */
public record LibroRequest(
        @Pattern(regexp = "^(?:\\d[- ]?){9}[\\dXx]$|^(?:\\d[- ]?){12}\\d$",
                 message = "ISBN no válido: debe tener 10 o 13 dígitos.")
        String isbn,

        @NotBlank(message = "El título es obligatorio.")
        @Size(max = 255, message = "El título no puede superar 255 caracteres.")
        String titulo,

        @NotBlank(message = "El autor es obligatorio.")
        @Size(max = 255, message = "El autor no puede superar 255 caracteres.")
        String autor,

        @Size(max = 150, message = "La editorial no puede superar 150 caracteres.")
        String editorial,

        @Size(max = 100, message = "La categoría no puede superar 100 caracteres.")
        String categoria,

        @Min(value = 0, message = "Año de publicación no válido.")
        @Max(value = 2100, message = "Año de publicación no válido.")
        Integer anioPublicacion,

        @Size(max = 5000, message = "La descripción no puede superar 5000 caracteres.")
        String descripcion,

        @Pattern(regexp = "^https?://\\S+$", message = "La portada debe ser una URL http o https.")
        @Size(max = 1000, message = "La URL de la portada no puede superar 1000 caracteres.")
        String portadaUrl) {

    public LibroRequest {
        // Recortar espacios exteriores; un campo opcional vacío se guarda como null
        isbn = limpiar(isbn);
        titulo = titulo == null ? null : titulo.trim();
        autor = autor == null ? null : autor.trim();
        editorial = limpiar(editorial);
        categoria = limpiar(categoria);
        descripcion = limpiar(descripcion);
        portadaUrl = limpiar(portadaUrl);
    }

    private static String limpiar(String valor) {
        if (valor == null) return null;
        String recortado = valor.trim();
        return recortado.isEmpty() ? null : recortado;
    }
}

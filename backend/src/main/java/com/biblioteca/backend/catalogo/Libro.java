
package com.biblioteca.backend.catalogo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * Representa un registro bibliográfico del catálogo.
 *
 * Un libro puede existir aunque la biblioteca no tenga
 * ejemplares físicos de él.
 */
@Entity
@Table(name = "libros")
public class Libro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 17, unique = true)
    private String isbn;

    @Column(nullable = false, length = 255)
    private String titulo;

    @Column(nullable = false, length = 255)
    private String autor;

    @Column(length = 150)
    private String editorial;

    @Column(length = 100)
    private String categoria;

    @Column(name = "anio_publicacion")
    private Integer anioPublicacion;

    // Nuevos campos incorporados mediante Flyway V5

    @Column(name = "descripcion", columnDefinition = "TEXT")
    private String descripcion;

    @Column(name = "portada_url", length = 1000)
    private String portadaUrl;

    /**
     * Identificador único de la edición de Open Library.
     * Ejemplo: /books/OL123456M
     */
    @Column(name = "openlibrary_key", length = 50, unique = true)
    private String openlibraryKey;

    /**
     * Identificador de la obra.
     * Varias ediciones pueden compartir la misma obra.
     */
    @Column(name = "openlibrary_work_key", length = 50)
    private String openlibraryWorkKey;

    /**
     * Origen del registro bibliográfico:
     * MANUAL u OPEN_LIBRARY.
     */
    @Column(nullable = false, length = 20)
    private String origen = "MANUAL";

    // Retirada lógica (#51, Flyway V7)

    @Column(nullable = false)
    private boolean retirado = false;

    @Column(name = "fecha_retirada")
    private LocalDateTime fechaRetirada;

    // Constructor requerido por JPA
    protected Libro() {
    }

    // Constructor original: mantener compatibilidad
    public Libro(
            String isbn,
            String titulo,
            String autor,
            String editorial,
            String categoria,
            Integer anioPublicacion) {

        this.isbn = isbn;
        this.titulo = titulo;
        this.autor = autor;
        this.editorial = editorial;
        this.categoria = categoria;
        this.anioPublicacion = anioPublicacion;
    }

    // Getters

    public Long getId() {
        return id;
    }

    public String getIsbn() {
        return isbn;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getAutor() {
        return autor;
    }

    public String getEditorial() {
        return editorial;
    }

    public String getCategoria() {
        return categoria;
    }

    public Integer getAnioPublicacion() {
        return anioPublicacion;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public String getPortadaUrl() {
        return portadaUrl;
    }

    public String getOpenlibraryKey() {
        return openlibraryKey;
    }

    public String getOpenlibraryWorkKey() {
        return openlibraryWorkKey;
    }

    public String getOrigen() {
        return origen;
    }

    public boolean isRetirado() {
        return retirado;
    }

    public LocalDateTime getFechaRetirada() {
        return fechaRetirada;
    }

    // Gestión del catálogo (#51)

    /** Actualiza los datos bibliográficos que puede editar el personal (#51). */
    public void actualizarDatos(String isbn, String titulo, String autor, String editorial,
                                String categoria, Integer anioPublicacion,
                                String descripcion, String portadaUrl) {
        this.isbn = isbn;
        this.titulo = titulo;
        this.autor = autor;
        this.editorial = editorial;
        this.categoria = categoria;
        this.anioPublicacion = anioPublicacion;
        this.descripcion = descripcion;
        this.portadaUrl = portadaUrl;
    }

    /** Retirada lógica (#51): deja de mostrarse en el catálogo, pero no se borra. */
    public void retirar() {
        if (!retirado) {
            retirado = true;
            fechaRetirada = LocalDateTime.now();
        }
    }
}

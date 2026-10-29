package com.biblioteca.backend.catalogo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Libro del catálogo (#27). Sus copias físicas simuladas son los Ejemplar. */
@Entity
@Table(name = "libros")
public class Libro {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 17, unique = true)
    private String isbn;

    @Column(nullable = false)
    private String titulo;

    @Column(nullable = false)
    private String autor;

    @Column(length = 150)
    private String editorial;

    @Column(length = 100)
    private String categoria;

    @Column(name = "anio_publicacion")
    private Integer anioPublicacion;

    /** Constructor vacío exigido por JPA. */
    protected Libro() {}

    public Libro(String isbn, String titulo, String autor, String editorial,
                 String categoria, Integer anioPublicacion) {
        this.isbn = isbn;
        this.titulo = titulo;
        this.autor = autor;
        this.editorial = editorial;
        this.categoria = categoria;
        this.anioPublicacion = anioPublicacion;
    }

    public Long getId() { return id; }
    public String getIsbn() { return isbn; }
    public String getTitulo() { return titulo; }
    public String getAutor() { return autor; }
    public String getEditorial() { return editorial; }
    public String getCategoria() { return categoria; }
    public Integer getAnioPublicacion() { return anioPublicacion; }
}

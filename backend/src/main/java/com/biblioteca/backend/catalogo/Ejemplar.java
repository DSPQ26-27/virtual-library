package com.biblioteca.backend.catalogo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Copia individual de un libro; es lo que se prestará en el Sprint 3. */
@Entity
@Table(name = "ejemplares")
public class Ejemplar {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "libro_id", nullable = false)
    private Libro libro;

    @Column(nullable = false, unique = true, length = 50)
    private String codigo;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR) // la columna es VARCHAR, no un tipo ENUM de la base de datos
    @Column(nullable = false, length = 20)
    private EstadoEjemplar estado = EstadoEjemplar.DISPONIBLE;

    @Column(nullable = false)
    private boolean activo = true;

    protected Ejemplar() {}

    public Ejemplar(Libro libro, String codigo) {
        this.libro = libro;
        this.codigo = codigo;
    }

    public Long getId() { return id; }
    public Libro getLibro() { return libro; }
    public String getCodigo() { return codigo; }
    public EstadoEjemplar getEstado() { return estado; }
    public boolean isActivo() { return activo; }

    /** Se puede prestar si está activo (no dado de baja) y no está prestado. */
    public boolean isPrestable() {
        return activo && estado == EstadoEjemplar.DISPONIBLE;
    }
}

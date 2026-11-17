package com.biblioteca.backend.prestamo;

import com.biblioteca.backend.catalogo.Ejemplar;
import com.biblioteca.backend.usuario.Usuario;
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
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Registro de préstamo necesario para proteger la retirada y conservar su historial. */
@Entity
@Table(name = "prestamos")
public class Prestamo {

    /** Días que dura un préstamo (US07). */
    public static final int DIAS_PRESTAMO = 30;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ejemplar_id", nullable = false)
    private Ejemplar ejemplar;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "fecha_prestamo", nullable = false)
    private LocalDateTime fechaPrestamo;

    @Column(name = "fecha_devolucion")
    private LocalDateTime fechaDevolucion;

    @Column(name = "fecha_vencimiento", nullable = false)
    private LocalDateTime fechaVencimiento;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR) // la columna es VARCHAR, no un tipo ENUM de la base de datos
    @Column(nullable = false, length = 20)
    private EstadoPrestamo estado = EstadoPrestamo.ACTIVO;

    protected Prestamo() {}

    public Prestamo(Ejemplar ejemplar, Usuario usuario) {
        this.ejemplar = ejemplar;
        this.usuario = usuario;
        this.fechaPrestamo = LocalDateTime.now();
        this.fechaVencimiento = fechaPrestamo.plusDays(DIAS_PRESTAMO);
        this.estado = EstadoPrestamo.ACTIVO;
    }

    public Long getId() { return id; }
    public Ejemplar getEjemplar() { return ejemplar; }
    public Usuario getUsuario() { return usuario; }
    public LocalDateTime getFechaPrestamo() { return fechaPrestamo; }
    public LocalDateTime getFechaDevolucion() { return fechaDevolucion; }
    public LocalDateTime getFechaVencimiento() { return fechaVencimiento; }
    public EstadoPrestamo getEstado() { return estado; }
    public boolean isActivo() { return estado == EstadoPrestamo.ACTIVO; }

    /** Cierra el préstamo; si ya estaba devuelto no cambia nada. */
    public void devolver() {
        if (estado == EstadoPrestamo.ACTIVO) {
            fechaDevolucion = LocalDateTime.now();
            estado = EstadoPrestamo.DEVUELTO;
        }
    }
}

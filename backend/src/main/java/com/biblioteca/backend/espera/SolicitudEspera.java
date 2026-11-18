package com.biblioteca.backend.espera;

import com.biblioteca.backend.catalogo.Libro;
import com.biblioteca.backend.usuario.Usuario;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Solicitud de un estudiante para esperar un ejemplar de un libro (US10). */
@Entity
@Table(name = "solicitudes_espera")
public class SolicitudEspera {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "libro_id", nullable = false)
    private Libro libro;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "fecha_solicitud", nullable = false)
    private LocalDateTime fechaSolicitud = LocalDateTime.now();

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private EstadoSolicitudEspera estado = EstadoSolicitudEspera.PENDIENTE;

    protected SolicitudEspera() {}

    public SolicitudEspera(Libro libro, Usuario usuario) {
        this.libro = libro;
        this.usuario = usuario;
    }

    public Long getId() { return id; }
    public Libro getLibro() { return libro; }
    public Usuario getUsuario() { return usuario; }
    public LocalDateTime getFechaSolicitud() { return fechaSolicitud; }
    public EstadoSolicitudEspera getEstado() { return estado; }

    public void asignar() {
        estado = EstadoSolicitudEspera.ASIGNADA;
    }

}

package com.biblioteca.backend.prestamo;

import com.biblioteca.backend.catalogo.Ejemplar;
import com.biblioteca.backend.catalogo.EjemplarRepository;
import com.biblioteca.backend.catalogo.Libro;
import com.biblioteca.backend.catalogo.LibroNoEncontradoException;
import com.biblioteca.backend.catalogo.LibroRepository;
import com.biblioteca.backend.espera.EstadoSolicitudEspera;
import com.biblioteca.backend.espera.SolicitudEsperaRepository;
import com.biblioteca.backend.usuario.Usuario;
import com.biblioteca.backend.usuario.UsuarioRepository;
import java.time.LocalDateTime;
import java.util.Comparator;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Préstamo de un ejemplar disponible a un estudiante (US07). */
@Service
public class PrestamoService {

    /** Máximo de préstamos activos por estudiante (US07). */
    public static final int MAX_PRESTAMOS_ACTIVOS = 5;

    private final PrestamoRepository prestamos;
    private final UsuarioRepository usuarios;
    private final LibroRepository libros;
    private final EjemplarRepository ejemplares;
    private final SolicitudEsperaRepository solicitudesEspera;

    public PrestamoService(PrestamoRepository prestamos, UsuarioRepository usuarios,
            LibroRepository libros, EjemplarRepository ejemplares, SolicitudEsperaRepository solicitudesEspera) {
        this.prestamos = prestamos;
        this.usuarios = usuarios;
        this.libros = libros;
        this.ejemplares = ejemplares;
        this.solicitudesEspera = solicitudesEspera;
    }

    @Transactional(readOnly = true)
    public MisPrestamosResponse misPrestamos(long usuarioId) {
        var propios = prestamos.listarPropios(usuarioId);
        var activos = propios.stream().filter(p -> p.estado() == EstadoPrestamo.ACTIVO)
            .sorted(Comparator.comparing(PrestamoListadoResponse::fechaVencimiento)
                .thenComparing(PrestamoListadoResponse::id)).toList();
        var historial = propios.stream().filter(p -> p.estado() != EstadoPrestamo.ACTIVO)
            .sorted(Comparator.comparing(PrestamoListadoResponse::fechaDevolucion,
                    Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(PrestamoListadoResponse::id)).toList();
        return new MisPrestamosResponse(activos, historial);
    }

    @Transactional
    public PrestamoResponse prestar(long usuarioId, long libroId) {
        solicitudesEspera.bloquearOperaciones();
        // 1. Bloquear al estudiante: sus peticiones simultáneas van de una en una
        Usuario estudiante = usuarios.findParaPrestamo(usuarioId)
            .orElseThrow(() -> new IllegalStateException("El usuario de la sesión no existe."));

        // 2. Reglas del estudiante
        if (prestamos.tienePrestamosVencidos(usuarioId)) {
            throw new PrestamosVencidosException();
        }
        if (prestamos.countByUsuarioIdAndEstado(usuarioId, EstadoPrestamo.ACTIVO) >= MAX_PRESTAMOS_ACTIVOS) {
            throw new LimitePrestamosException();
        }

        // 3. Bloquear el libro: las peticiones del mismo libro van de una en una
        Libro libro = libros.findParaPrestamo(libroId)
            .orElseThrow(LibroNoEncontradoException::new);

        // 4. Elegir automáticamente el primer ejemplar libre
        Ejemplar ejemplar = ejemplares.findPrestables(libro.getId()).stream().findFirst()
            .orElseThrow(SinEjemplaresDisponiblesException::new);

        // 5. Registrar el préstamo y marcar el ejemplar como prestado (misma transacción)
        ejemplar.prestar();
        try {
            return PrestamoResponse.from(prestamos.saveAndFlush(new Prestamo(ejemplar, estudiante)));
        } catch (DataIntegrityViolationException ex) {
            // Red de seguridad: un solo préstamo activo por ejemplar (V10)
            throw new SinEjemplaresDisponiblesException();
        }
    }

    @Transactional
    public DevolucionResponse devolver(Long usuarioId, Long prestamoId) {
        solicitudesEspera.bloquearOperaciones();
        usuarios.findParaPrestamo(usuarioId).orElseThrow(PrestamoNoEncontradoException::new);
        var ubicacion = prestamos.localizarPropio(prestamoId, usuarioId)
            .orElseThrow(PrestamoNoEncontradoException::new);
        libros.findParaDevolucion(ubicacion.getLibroId()).orElseThrow(PrestamoNoEncontradoException::new);
        Ejemplar ejemplar = ejemplares.findByIdAndLibroId(ubicacion.getEjemplarId(), ubicacion.getLibroId())
            .orElseThrow(PrestamoNoEncontradoException::new);
        Prestamo prestamo = prestamos.findPropioParaActualizar(prestamoId, usuarioId)
            .orElseThrow(PrestamoNoEncontradoException::new);
        if (!prestamo.isActivo()) throw new PrestamoYaDevueltoException();
        if (!prestamo.getEjemplar().getId().equals(ejemplar.getId())) {
            throw new IllegalStateException("El ejemplar del préstamo ha cambiado.");
        }
        prestamo.devolver();
        ejemplar.liberarTrasDevolucion();
        // El préstamo anterior debe estar cerrado en SQL antes de insertar el nuevo.
        prestamos.flush();
        asignarSiguiente(ejemplar);
        return new DevolucionResponse(prestamo.getId(), prestamo.getEstado(), prestamo.getFechaDevolucion());
    }

    @Transactional
    public RenovacionResponse renovar(Long usuarioId, Long prestamoId) {
        solicitudesEspera.bloquearOperaciones();
        Usuario estudiante = usuarios.findParaPrestamo(usuarioId)
            .orElseThrow(PrestamoNoEncontradoException::new);
        var ubicacion = prestamos.localizarPropio(prestamoId, usuarioId)
            .orElseThrow(PrestamoNoEncontradoException::new);
        // Mismo orden que US07/US08: serializa con los préstamos y las devoluciones del libro.
        libros.findParaDevolucion(ubicacion.getLibroId()).orElseThrow(PrestamoNoEncontradoException::new);
        Prestamo prestamo = prestamos.findPropioParaActualizar(prestamoId, usuarioId)
            .orElseThrow(PrestamoNoEncontradoException::new);

        if (!prestamo.isActivo() || prestamo.getFechaDevolucion() != null) {
            throw new RenovacionRechazadaException("PRESTAMO_NO_ACTIVO", "Solo puedes renovar préstamos activos.");
        }
        if (!prestamo.getFechaVencimiento().isAfter(LocalDateTime.now())) {
            throw new RenovacionRechazadaException("PRESTAMO_VENCIDO", "No puedes renovar un préstamo vencido.");
        }
        if (prestamo.getNumeroRenovaciones() >= Prestamo.MAX_RENOVACIONES) {
            throw new RenovacionRechazadaException("LIMITE_RENOVACIONES", "Has alcanzado el límite de 2 renovaciones.");
        }
        if (!estudiante.isActivo() || prestamos.tienePrestamosVencidos(usuarioId)) {
            throw new RenovacionRechazadaException("ESTUDIANTE_BLOQUEADO", "No puedes renovar mientras estés bloqueado.");
        }
        if (solicitudesEspera.existsByLibroIdAndEstado(ubicacion.getLibroId(), EstadoSolicitudEspera.PENDIENTE)
                && ejemplares.findPrestables(ubicacion.getLibroId()).isEmpty()) {
            throw new RenovacionRechazadaException("LISTA_ESPERA_PENDIENTE",
                "Hay solicitudes pendientes para este libro y no quedan ejemplares libres.");
        }

        prestamo.renovar();
        return new RenovacionResponse(prestamo.getId(), prestamo.getEstado(),
            prestamo.getNumeroRenovaciones(), prestamo.getFechaVencimiento());
    }

    private void asignarSiguiente(Ejemplar ejemplar) {
        if (!ejemplar.isPrestable() || ejemplar.getLibro().isRetirado()) return;

        var cola = solicitudesEspera.findByLibroIdAndEstadoOrderByFechaSolicitudAscIdAsc(
                ejemplar.getLibro().getId(), EstadoSolicitudEspera.PENDIENTE);

        for (var solicitud : cola) {
            Usuario estudiante = usuarios.findParaPrestamo(solicitud.getUsuario().getId()).orElseThrow();
            if (!esElegible(estudiante)) continue;

            ejemplar.prestar();
            prestamos.saveAndFlush(new Prestamo(ejemplar, estudiante));
            solicitud.asignar();
            return;
        }
    }

    private boolean esElegible(Usuario estudiante) {
        return estudiante.isActivo() && "ESTUDIANTE".equals(estudiante.getRol())
                && !prestamos.tienePrestamosVencidos(estudiante.getId())
                && prestamos.countByUsuarioIdAndEstado(estudiante.getId(), EstadoPrestamo.ACTIVO)
                    < MAX_PRESTAMOS_ACTIVOS;
    }
}

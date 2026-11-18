package com.biblioteca.backend.espera;

import com.biblioteca.backend.catalogo.EjemplarRepository;
import com.biblioteca.backend.catalogo.Libro;
import com.biblioteca.backend.catalogo.LibroNoEncontradoException;
import com.biblioteca.backend.catalogo.LibroRepository;
import com.biblioteca.backend.usuario.Usuario;
import com.biblioteca.backend.usuario.UsuarioRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SolicitudEsperaService {

    private final SolicitudEsperaRepository solicitudes;
    private final UsuarioRepository usuarios;
    private final LibroRepository libros;
    private final EjemplarRepository ejemplares;

    public SolicitudEsperaService(SolicitudEsperaRepository solicitudes,
            UsuarioRepository usuarios, LibroRepository libros, EjemplarRepository ejemplares) {
        this.solicitudes = solicitudes;
        this.usuarios = usuarios;
        this.libros = libros;
        this.ejemplares = ejemplares;
    }

    @Transactional
    public SolicitudEsperaResponse apuntarse(Long usuarioId, Long libroId) {
        solicitudes.bloquearOperaciones();
        // Mismo orden de bloqueo que los préstamos: primero usuario y después libro.
        Usuario estudiante = usuarios.findParaPrestamo(usuarioId)
                .orElseThrow(() -> new IllegalStateException("El usuario de la sesión no existe."));

        if (!estudiante.isActivo()) {
            throw new SolicitudEsperaRechazadaException(
                    "ESTUDIANTE_INACTIVO", "Tu cuenta no está activa.");
        }

        Libro libro = libros.findParaPrestamo(libroId)
                .orElseThrow(LibroNoEncontradoException::new);

        if (solicitudes.existsByLibroIdAndUsuarioIdAndEstado(
                libroId, usuarioId, EstadoSolicitudEspera.PENDIENTE)) {
            throw new SolicitudEsperaRechazadaException(
                    "SOLICITUD_DUPLICADA", "Ya estás en la lista de espera de este libro.");
        }

        if (!ejemplares.findPrestables(libroId).isEmpty()) {
            throw new SolicitudEsperaRechazadaException(
                    "EJEMPLARES_DISPONIBLES", "Hay ejemplares disponibles. Puedes solicitar un préstamo.");
        }

        SolicitudEspera solicitud = new SolicitudEspera(libro, estudiante);
        solicitudes.saveAndFlush(solicitud);
        return SolicitudEsperaResponse.from(solicitud);
    }

    @Transactional(readOnly = true)
    public List<SolicitudEsperaResponse> misSolicitudes(Long usuarioId) {
        return solicitudes.findByUsuarioIdOrderByFechaSolicitudAscIdAsc(usuarioId)
                .stream()
                .map(SolicitudEsperaResponse::from)
                .toList();
    }

}


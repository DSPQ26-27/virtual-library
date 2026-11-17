package com.biblioteca.backend.prestamo;

import com.biblioteca.backend.catalogo.Ejemplar;
import com.biblioteca.backend.catalogo.EjemplarRepository;
import com.biblioteca.backend.catalogo.Libro;
import com.biblioteca.backend.catalogo.LibroNoEncontradoException;
import com.biblioteca.backend.catalogo.LibroRepository;
import com.biblioteca.backend.usuario.Usuario;
import com.biblioteca.backend.usuario.UsuarioRepository;
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

    public PrestamoService(PrestamoRepository prestamos, UsuarioRepository usuarios,
            LibroRepository libros, EjemplarRepository ejemplares) {
        this.prestamos = prestamos;
        this.usuarios = usuarios;
        this.libros = libros;
        this.ejemplares = ejemplares;
    }

    @Transactional
    public PrestamoResponse prestar(long usuarioId, long libroId) {
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
}

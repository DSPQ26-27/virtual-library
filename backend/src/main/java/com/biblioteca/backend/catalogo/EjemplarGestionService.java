package com.biblioteca.backend.catalogo;

import com.biblioteca.backend.prestamo.PrestamoRepository;
import java.util.List;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EjemplarGestionService {

    private final LibroRepository libros;
    private final EjemplarRepository ejemplares;
    private final PrestamoRepository prestamos;

    public EjemplarGestionService(LibroRepository libros, EjemplarRepository ejemplares,
            PrestamoRepository prestamos) {
        this.libros = libros;
        this.ejemplares = ejemplares;
        this.prestamos = prestamos;
    }

    @Transactional(readOnly = true)
    public EjemplaresResponse listar(Long libroId) {
        if (!libros.existsById(libroId)) throw new LibroNoEncontradoException();
        List<Ejemplar> lista = ejemplares.findByLibroIdOrderByIdAsc(libroId);
        List<Long> ids = lista.stream().map(Ejemplar::getId).toList();
        Set<Long> prestados = ids.isEmpty() ? Set.of() : prestamos.findEjemplarIdsConPrestamoActivo(ids);
        List<EjemplarResponse> respuesta = lista.stream()
            .map(e -> prestados.contains(e.getId())
                ? new EjemplarResponse(e.getId(), e.getCodigo(), EstadoEjemplar.PRESTADO, e.isActivo())
                : EjemplarResponse.from(e))
            .toList();
        return new EjemplaresResponse(respuesta, lista.size(),
            lista.stream().filter(Ejemplar::isPrestable).filter(e -> !prestados.contains(e.getId())).count());
    }

    @Transactional
    public EjemplarResponse crear(Long libroId, EjemplarRequest datos) {
        Libro libro = libros.findById(libroId).orElseThrow(LibroNoEncontradoException::new);
        comprobarCodigoLibre(datos.codigo(), null);
        Ejemplar ejemplar = new Ejemplar(libro, datos.codigo());
        ejemplar.actualizarDatos(datos.codigo(), datos.estado());
        return guardar(ejemplar);
    }

    @Transactional
    public EjemplarResponse editar(Long libroId, Long ejemplarId, EjemplarRequest datos) {
        Ejemplar ejemplar = buscar(libroId, ejemplarId);
        if (!ejemplar.isActivo()) throw new EjemplarRetiradoException();
        if (datos.estado() != EstadoEjemplar.PRESTADO
                && prestamos.existsByEjemplarIdAndFechaDevolucionIsNull(ejemplarId)) {
            throw new EjemplarConPrestamoActivoException(
                "No se puede cambiar el estado de un ejemplar con un préstamo activo.");
        }
        comprobarCodigoLibre(datos.codigo(), ejemplarId);
        ejemplar.actualizarDatos(datos.codigo(), datos.estado());
        return guardar(ejemplar);
    }

    @Transactional
    public void retirar(Long libroId, Long ejemplarId) {
        Ejemplar ejemplar = buscar(libroId, ejemplarId);
        if (!ejemplar.isActivo()) return;
        if (ejemplar.getEstado() == EstadoEjemplar.PRESTADO
                || prestamos.existsByEjemplarIdAndFechaDevolucionIsNull(ejemplarId)) {
            throw new EjemplarConPrestamoActivoException(
                "No se puede retirar un ejemplar con un préstamo activo.");
        }
        ejemplar.retirar();
    }

    private Ejemplar buscar(Long libroId, Long ejemplarId) {
        return ejemplares.findByIdAndLibroId(ejemplarId, libroId)
            .orElseThrow(EjemplarNoEncontradoException::new);
    }

    private void comprobarCodigoLibre(String codigo, Long idActual) {
        if (ejemplares.findByCodigo(codigo).filter(e -> !e.getId().equals(idActual)).isPresent()) {
            throw new CodigoEjemplarDuplicadoException();
        }
    }

    private EjemplarResponse guardar(Ejemplar ejemplar) {
        try {
            return EjemplarResponse.from(ejemplares.saveAndFlush(ejemplar));
        } catch (DataIntegrityViolationException ex) {
            // La restricción UNIQUE protege también frente a altas simultáneas.
            throw new CodigoEjemplarDuplicadoException();
        }
    }
}

package com.biblioteca.backend.catalogo;

import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EjemplarGestionService {

    private final LibroRepository libros;
    private final EjemplarRepository ejemplares;

    public EjemplarGestionService(LibroRepository libros, EjemplarRepository ejemplares) {
        this.libros = libros;
        this.ejemplares = ejemplares;
    }

    @Transactional(readOnly = true)
    public EjemplaresResponse listar(Long libroId) {
        if (!libros.existsById(libroId)) throw new LibroNoEncontradoException();
        List<Ejemplar> lista = ejemplares.findByLibroIdOrderByIdAsc(libroId);
        return new EjemplaresResponse(lista.stream().map(EjemplarResponse::from).toList(),
            lista.size(), lista.stream().filter(Ejemplar::isPrestable).count());
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
        comprobarCodigoLibre(datos.codigo(), ejemplarId);
        ejemplar.actualizarDatos(datos.codigo(), datos.estado());
        return guardar(ejemplar);
    }

    @Transactional
    public void retirar(Long libroId, Long ejemplarId) {
        buscar(libroId, ejemplarId).retirar();
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

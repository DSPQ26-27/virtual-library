package com.biblioteca.backend.catalogo;

import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Búsqueda paginada del catálogo (#47). */
@Service
public class LibroService {

    static final int TAMANO_MAXIMO = 100;
    private static final Set<String> CAMPOS_ORDENABLES =
        Set.of("titulo", "autor", "anioPublicacion", "categoria", "editorial");

    private final LibroRepository libros;
    private final EjemplarRepository ejemplares;

    public LibroService(LibroRepository libros, EjemplarRepository ejemplares) {
        this.libros = libros;
        this.ejemplares = ejemplares;
    }

    @Transactional(readOnly = true)
    public List<String> listarCategorias() {
        return libros.findCategorias();
    }

    @Transactional(readOnly = true)
    public PaginaLibrosResponse buscar(String texto, String categoria, VistaCatalogo vista, Pageable pedida) {
        // 1. PostgreSQL devuelve solo la página pedida (LIMIT/OFFSET) y el total (COUNT)
        Page<Libro> pagina = libros.findAll(
            LibroSpecifications.filtrar(texto, categoria, vista), paginaSegura(pedida));

        // 2. Disponibilidad real, solo para los libros de esta página (una consulta)
        List<Long> ids = pagina.getContent().stream().map(Libro::getId).toList();
        Set<Long> prestables = ids.isEmpty() ? Set.of() : ejemplares.findLibrosConEjemplarPrestable(ids);

        // 3. Entidad -> DTO
        List<LibroResponse> contenido = pagina.getContent().stream()
            .map(libro -> new LibroResponse(libro.getId(), libro.getTitulo(), libro.getAutor(),
                prestables.contains(libro.getId())))
            .toList();

        return new PaginaLibrosResponse(contenido, pagina.getNumber(), pagina.getSize(),
            pagina.getTotalElements(), pagina.getTotalPages());
    }

    /** Limita el tamaño de página, solo permite ordenar por campos conocidos y desempata por id. */
    private Pageable paginaSegura(Pageable pedida) {
        int tamano = Math.min(Math.max(pedida.getPageSize(), 1), TAMANO_MAXIMO);
        Sort orden = Sort.by(pedida.getSort().stream()
            .filter(o -> CAMPOS_ORDENABLES.contains(o.getProperty()))
            .toList());
        if (orden.isUnsorted()) {
            orden = Sort.by("titulo");
        }
        return PageRequest.of(pedida.getPageNumber(), tamano, orden.and(Sort.by("id")));
    }
}

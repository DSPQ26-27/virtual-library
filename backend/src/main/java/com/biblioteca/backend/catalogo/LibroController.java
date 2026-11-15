package com.biblioteca.backend.catalogo;

import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * GET /api/libros: catálogo paginado con búsqueda y filtros (#28, ampliado en #47).
 * Ejemplo: /api/libros?q=cervantes&categoria=Novela&vista=DISPONIBLES&page=0&size=20&sort=titulo,asc
 */
@RestController
@RequestMapping("/api/libros")
public class LibroController {

    private final LibroService libroService;

    public LibroController(LibroService libroService) {
        this.libroService = libroService;
    }

    @GetMapping("/categorias")
    public List<String> listarCategorias() {
        return libroService.listarCategorias();
    }

    @GetMapping
    public PaginaLibrosResponse listarLibros(
            @RequestParam(name = "q", required = false) String texto,
            @RequestParam(required = false) String categoria,
            @RequestParam(defaultValue = "TODOS") VistaCatalogo vista,
            @PageableDefault(size = 20, sort = "titulo") Pageable pageable) {

        return libroService.buscar(texto, categoria, vista, pageable);
    }

    /** Un valor no válido (p. ej. vista=OTRA) responde 400 en JSON, sin detalles técnicos. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> parametroNoValido() {
        return ResponseEntity.badRequest().body(Map.of("message", "Parámetro de búsqueda no válido."));
    }
}

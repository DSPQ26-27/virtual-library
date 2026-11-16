package com.biblioteca.backend.catalogo;

import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gestión del catálogo (#51). Solo LIBRARIAN y ADMIN pueden crear, editar y retirar
 * (lo exige SecurityConfig para los métodos distintos de GET en /api/libros/**).
 */
@RestController
@RequestMapping("/api/libros")
public class LibroGestionController {

    private final LibroGestionService gestion;

    public LibroGestionController(LibroGestionService gestion) {
        this.gestion = gestion;
    }

    @GetMapping("/{id}")
    public LibroDetalleResponse obtener(@PathVariable Long id) {
        return gestion.obtener(id);
    }

    @PostMapping
    public ResponseEntity<LibroDetalleResponse> crear(@Valid @RequestBody LibroRequest datos) {
        LibroDetalleResponse creado = gestion.crear(datos);
        return ResponseEntity.created(URI.create("/api/libros/" + creado.id())).body(creado);
    }

    @PutMapping("/{id}")
    public LibroDetalleResponse editar(@PathVariable Long id, @Valid @RequestBody LibroRequest datos) {
        return gestion.editar(id, datos);
    }

    /** Retirada lógica: el libro no se borra, deja de aparecer en el catálogo. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> retirar(@PathVariable Long id) {
        gestion.retirar(id);
        return ResponseEntity.noContent().build();
    }
}

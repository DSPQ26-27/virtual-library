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

/** SecurityConfig restringe las escrituras a LIBRARIAN y ADMIN y exige CSRF. */
@RestController
@RequestMapping("/api/libros/{libroId}/ejemplares")
public class EjemplarGestionController {

    private final EjemplarGestionService gestion;

    public EjemplarGestionController(EjemplarGestionService gestion) {
        this.gestion = gestion;
    }

    @GetMapping
    public EjemplaresResponse listar(@PathVariable Long libroId) {
        return gestion.listar(libroId);
    }

    @PostMapping
    public ResponseEntity<EjemplarResponse> crear(@PathVariable Long libroId,
            @Valid @RequestBody EjemplarRequest datos) {
        EjemplarResponse creado = gestion.crear(libroId, datos);
        return ResponseEntity.created(URI.create("/api/libros/" + libroId + "/ejemplares/" + creado.id()))
            .body(creado);
    }

    @PutMapping("/{ejemplarId}")
    public EjemplarResponse editar(@PathVariable Long libroId, @PathVariable Long ejemplarId,
            @Valid @RequestBody EjemplarRequest datos) {
        return gestion.editar(libroId, ejemplarId, datos);
    }

    @DeleteMapping("/{ejemplarId}")
    public ResponseEntity<Void> retirar(@PathVariable Long libroId, @PathVariable Long ejemplarId) {
        gestion.retirar(libroId, ejemplarId);
        return ResponseEntity.noContent().build();
    }
}

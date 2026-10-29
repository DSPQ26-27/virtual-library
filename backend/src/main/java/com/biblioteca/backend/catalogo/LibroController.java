
package com.biblioteca.backend.catalogo;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/libros")
public class LibroController {

    private final LibroRepository libroRepository;

    public LibroController(LibroRepository libroRepository) {
        this.libroRepository = libroRepository;
    }

    @GetMapping
    public List<LibroResponse> listarLibros() {

        return libroRepository.findAll()
            .stream()
            .map(libro -> new LibroResponse(
                libro.getId(),
                libro.getTitulo(),
                libro.getAutor(),
                false // Temporal: pendiente de consultar ejemplares.
            ))
            .toList();
    }
}

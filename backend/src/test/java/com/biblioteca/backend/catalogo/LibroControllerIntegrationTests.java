
package com.biblioteca.backend.catalogo;

import com.biblioteca.backend.BackendApplication;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = BackendApplication.class)
@ActiveProfiles("test")
@AutoConfigureMockMvc
public class LibroControllerIntegrationTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private LibroRepository libroRepository;

    @Autowired
    private JdbcTemplate jdbc;

    // PRUEBA 1: Un usuario sin autenticar no puede ver el catálogo
    @Test
    void usuarioNoAutenticadoNoPuedeVerCatalogo() throws Exception {

        mvc.perform(get("/api/libros"))
            .andExpect(status().isUnauthorized());
    }

    // PRUEBA 2: Un usuario autenticado puede consultar libros
    @Test
    void usuarioAutenticadoPuedeVerLibros() throws Exception {

        Libro libro = new Libro(
            "9788420412146",
            "Don Quijote de la Mancha",
            "Miguel de Cervantes",
            "Alfaguara",
            "Novela",
            1605
        );

        libro = libroRepository.saveAndFlush(libro);

        try {
            mvc.perform(get("/api/libros")
                    .with(user("alumno").roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + libro.getId() + ")]").exists())
                .andExpect(jsonPath("$[?(@.titulo == 'Don Quijote de la Mancha')]").exists())
                .andExpect(jsonPath("$[?(@.autor == 'Miguel de Cervantes')]").exists())
                .andExpect(jsonPath("$[?(@.disponible == false)]").exists());

        } finally {
            libroRepository.deleteById(libro.getId());
        }
    }

    // PRUEBA 3: Si no hay libros, devuelve una lista vacía
    @Test
    void catalogoVacioDevuelveListaVacia() throws Exception {

        // Primero eliminamos los ejemplares asociados.
        jdbc.update("DELETE FROM ejemplares");

        // Después eliminamos los libros.
        libroRepository.deleteAll();

        // Comprobamos la respuesta del endpoint.
        mvc.perform(get("/api/libros")
                .with(user("alumno").roles("STUDENT")))
            .andExpect(status().isOk())
            .andExpect(content().json("[]"));
    }
}

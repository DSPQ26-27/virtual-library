package com.biblioteca.backend.catalogo;

import com.biblioteca.backend.BackendApplication;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Endpoint real, servicio y consulta JPA sobre la H2 aislada del perfil test. */
@SpringBootTest(classes = BackendApplication.class)
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class CategoriasControllerIntegrationTests {

    @Autowired private MockMvc mvc;
    @Autowired private LibroRepository libros;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void aislarCategorias() {
        // Solo datos de H2; la transacción restaura las categorías al terminar cada prueba.
        jdbc.update("UPDATE libros SET categoria = NULL");
    }

    private void categoria(String categoria) {
        libros.saveAndFlush(new Libro(null, "Libro de prueba", "Autor", null, categoria, null));
    }

    @Test
    void devuelveCategoriasDiferentesOrdenadasComoArrayJson() throws Exception {
        categoria("Literatura");
        categoria("Informática");
        categoria("Historia");
        categoria("Economía");
        categoria("Derecho");
        categoria("Ciencias");

        mvc.perform(get("/api/libros/categorias").with(user("alumno").roles("STUDENT")))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("application/json"))
            .andExpect(content().json("""
                ["Ciencias", "Derecho", "Economía", "Historia", "Informática", "Literatura"]
                """))
            .andExpect(jsonPath("$[0]").value("Ciencias"))
            .andExpect(jsonPath("$[1]").value("Derecho"))
            .andExpect(jsonPath("$[2]").value("Economía"))
            .andExpect(jsonPath("$[3]").value("Historia"))
            .andExpect(jsonPath("$[4]").value("Informática"))
            .andExpect(jsonPath("$[5]").value("Literatura"));
    }

    @Test
    void agrupaDuplicadosIgnorandoMayusculasYEspaciosExteriores() throws Exception {
        categoria("Historia");
        categoria("historia");
        categoria("HISTORIA");
        categoria("  Historia  ");
        categoria("Historia");

        var categorias = libros.findCategorias();
        assertEquals(1, categorias.size());
        assertEquals("historia", categorias.getFirst().toLowerCase(Locale.ROOT));
        assertEquals(categorias.getFirst().trim(), categorias.getFirst());

        mvc.perform(get("/api/libros/categorias").with(user("alumno").roles("STUDENT")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0]").value(categorias.getFirst()));
    }

    @Test
    void ignoraNulosVaciosYSoloEspacios() throws Exception {
        categoria(null);
        categoria("");
        categoria("     ");
        categoria("  Ciencias  ");

        mvc.perform(get("/api/libros/categorias").with(user("alumno").roles("STUDENT")))
            .andExpect(status().isOk())
            .andExpect(content().json("[\"Ciencias\"]"));
    }

    @Test
    void categoriaPublicadaPermiteEncontrarLibrosConEspaciosExteriores() throws Exception {
        categoria("  Astronomía  ");

        mvc.perform(get("/api/libros/categorias").with(user("alumno").roles("STUDENT")))
            .andExpect(status().isOk())
            .andExpect(content().json("[\"Astronomía\"]"));

        mvc.perform(get("/api/libros").param("categoria", "Astronomía")
                .with(user("alumno").roles("STUDENT")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void sinCategoriasValidasDevuelveArrayVacio() throws Exception {
        categoria(null);
        categoria("   ");

        mvc.perform(get("/api/libros/categorias").with(user("alumno").roles("STUDENT")))
            .andExpect(status().isOk())
            .andExpect(content().json("[]"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"STUDENT", "LIBRARIAN", "ADMIN"})
    void permiteLosRolesQuePuedenConsultarLibros(String rol) throws Exception {
        mvc.perform(get("/api/libros/categorias").with(user("lector").roles(rol)))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("application/json"));
    }

    @Test
    void sinAutenticarDevuelve401JsonSinRedireccion() throws Exception {
        mvc.perform(get("/api/libros/categorias"))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith("application/json"))
            .andExpect(header().doesNotExist("Location"));
    }

    @Test
    void unRolNoPermitidoDevuelve403() throws Exception {
        mvc.perform(get("/api/libros/categorias").with(user("otro").roles("OTHER")))
            .andExpect(status().isForbidden());
    }
}

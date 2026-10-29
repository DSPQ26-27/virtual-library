package com.biblioteca.backend.catalogo;

import com.biblioteca.backend.BackendApplication;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/** Persistencia de Libro y Ejemplar y datos de muestra (#27). */
@SpringBootTest(classes = BackendApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional // cada test deshace sus cambios al terminar
class CatalogoPersistenceTests {
    @Autowired LibroRepository libros;
    @Autowired EjemplarRepository ejemplares;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;

    @Test
    void sampleDataIsLoadedByFlyway() {
        assertEquals(4, libros.count());
        assertEquals(4, ejemplares.count());
        List<String> titulos = libros.findAll().stream().map(Libro::getTitulo).toList();
        assertTrue(titulos.contains("Don Quijote de la Mancha"));
        assertTrue(titulos.contains("La casa de Bernarda Alba"));
    }

    @Test
    void savesAndReadsBookWithItsCopy() {
        Libro libro = libros.save(new Libro("978-84-670-4170-3", "El nombre de la rosa",
            "Umberto Eco", "Lumen", "Novela", 1980));
        Ejemplar ejemplar = ejemplares.saveAndFlush(new Ejemplar(libro, "EJ-TEST-1"));
        entityManager.clear(); // obliga a releer desde la base de datos

        Ejemplar leido = ejemplares.findById(ejemplar.getId()).orElseThrow();
        assertEquals("EJ-TEST-1", leido.getCodigo());
        assertEquals("El nombre de la rosa", leido.getLibro().getTitulo());
        assertEquals("Umberto Eco", leido.getLibro().getAutor());
        assertEquals(1980, leido.getLibro().getAnioPublicacion());
        assertEquals(EstadoEjemplar.DISPONIBLE, leido.getEstado());
        assertTrue(leido.isActivo());
        assertTrue(leido.isPrestable());
    }

    @Test
    void lentOrRetiredCopiesAreNotLendable() {
        Libro libro = libros.save(new Libro(null, "Libro de prueba", "Autor", null, null, null));
        Long prestado = ejemplares.saveAndFlush(new Ejemplar(libro, "EJ-TEST-2")).getId();
        Long deBaja = ejemplares.saveAndFlush(new Ejemplar(libro, "EJ-TEST-3")).getId();
        jdbc.update("UPDATE ejemplares SET estado = 'PRESTADO' WHERE id = ?", prestado);
        jdbc.update("UPDATE ejemplares SET activo = FALSE WHERE id = ?", deBaja);
        entityManager.clear();

        assertFalse(ejemplares.findById(prestado).orElseThrow().isPrestable());
        assertFalse(ejemplares.findById(deBaja).orElseThrow().isPrestable());
    }

    @Test
    void databaseRejectsUnknownCopyState() {
        Long libroId = libros.findAll().getFirst().getId();
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
            "INSERT INTO ejemplares (libro_id, codigo, estado) VALUES (?, 'EJ-TEST-4', 'ROTO')", libroId));
    }

    @Test
    void copyCodeMustBeUnique() {
        Libro libro = libros.findAll().getFirst();
        assertThrows(DataIntegrityViolationException.class,
            () -> ejemplares.saveAndFlush(new Ejemplar(libro, "EJ-0001")));
    }
}

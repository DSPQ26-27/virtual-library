package com.biblioteca.backend.catalogo;

import com.biblioteca.backend.BackendApplication;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.contains;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * GET /api/libros: catálogo (#28) con búsqueda, filtros y paginación (#47).
 * Cada test se ejecuta en una transacción que se deshace al terminar.
 */
@SpringBootTest(classes = BackendApplication.class)
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
public class LibroControllerIntegrationTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private LibroRepository libroRepository;

    @Autowired
    private EjemplarRepository ejemplarRepository;

    @Autowired
    private JdbcTemplate jdbc;

    // ---------- Utilidades ----------

    private ResultActions buscar(String parametros) throws Exception {
        return mvc.perform(get("/api/libros" + parametros)
            .with(user("alumno").roles("STUDENT")));
    }

    private Libro libro(String isbn, String titulo, String autor, String categoria, int anio) {
        return libroRepository.saveAndFlush(
            new Libro(isbn, titulo, autor, "Editorial de prueba", categoria, anio));
    }

    private void ejemplar(Libro libro, String codigo, String estado, boolean activo) {
        Long id = ejemplarRepository.saveAndFlush(new Ejemplar(libro, codigo)).getId();
        jdbc.update("UPDATE ejemplares SET estado = ?, activo = ? WHERE id = ?", estado, activo, id);
    }

    /**
     * Cuatro libros marcados con "Zzprueba" para no mezclarlos con los datos de ejemplo:
     * Alfa (Historia): ejemplar disponible   -> en biblioteca y prestable
     * Beta (Historia): ejemplar prestado     -> en biblioteca, no prestable
     * Gamma (Ciencia): sin ejemplares        -> fuera de la biblioteca
     * Delta (Ciencia): ejemplar dado de baja -> fuera de la biblioteca
     */
    private void crearEscenario() {
        Libro alfa = libro("978-0-00-000001-1", "Zzprueba Alfa", "Ana Autora", "Historia", 2001);
        Libro beta = libro("978-0-00-000002-2", "Zzprueba Beta", "Bruno Autor", "Historia", 2002);
        libro("978-0-00-000003-3", "Zzprueba Gamma", "Ana Autora", "Ciencia", 2003);
        Libro delta = libro("978-0-00-000004-4", "Zzprueba Delta", "Carla Autora", "Ciencia", 2004);
        ejemplar(alfa, "ZZ-0001", "DISPONIBLE", true);
        ejemplar(beta, "ZZ-0002", "PRESTADO", true);
        ejemplar(delta, "ZZ-0004", "DISPONIBLE", false);
    }

    // ---------- Pruebas de la #28 (adaptadas al formato paginado) ----------

    // PRUEBA 1: Un usuario sin autenticar no puede ver el catálogo
    @Test
    void usuarioNoAutenticadoNoPuedeVerCatalogo() throws Exception {
        mvc.perform(get("/api/libros"))
            .andExpect(status().isUnauthorized());
    }

    // PRUEBA 2: Un usuario autenticado puede consultar libros
    @Test
    void usuarioAutenticadoPuedeVerLibros() throws Exception {
        Libro libro = libro("9788420412146", "Don Quijote de la Mancha",
            "Miguel de Cervantes", "Novela", 1605);

        buscar("?size=100")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[?(@.id == " + libro.getId() + ")]").exists())
            .andExpect(jsonPath("$.content[?(@.titulo == 'Don Quijote de la Mancha')]").exists())
            .andExpect(jsonPath("$.content[?(@.autor == 'Miguel de Cervantes')]").exists());
    }

    // PRUEBA 3: Si no hay libros, devuelve una página vacía (nunca un error)
    @Test
    void catalogoVacioDevuelveListaVacia() throws Exception {
        jdbc.update("DELETE FROM libro_materias");
        jdbc.update("DELETE FROM ejemplares");
        jdbc.update("DELETE FROM libros");

        buscar("")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isEmpty())
            .andExpect(jsonPath("$.totalElements").value(0))
            .andExpect(jsonPath("$.totalPages").value(0));
    }

    // ---------- Pruebas de la #47 ----------

    @Test
    void devuelvePaginaConDatosDeNavegacion() throws Exception {
        int total = (int) libroRepository.count();

        buscar("?size=2")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(2))
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(2))
            .andExpect(jsonPath("$.totalElements").value(total))
            .andExpect(jsonPath("$.totalPages").value((total + 1) / 2));
    }

    @Test
    void buscaPorTituloAutorEIsbnSinDistinguirMayusculas() throws Exception {
        crearEscenario();

        buscar("?q=ZZPRUEBA ALFA")
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].titulo").value("Zzprueba Alfa"));

        buscar("?q=bruno")
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].titulo").value("Zzprueba Beta"));

        buscar("?q=000003")
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].titulo").value("Zzprueba Gamma"));
    }

    @Test
    void filtraPorCategoriaSinDistinguirMayusculas() throws Exception {
        crearEscenario();

        buscar("?q=zzprueba&categoria=ciencia")
            .andExpect(jsonPath("$.content[*].titulo", contains("Zzprueba Delta", "Zzprueba Gamma")));
    }

    @Test
    void lasTresVistasFiltranPorEjemplares() throws Exception {
        crearEscenario();

        buscar("?q=zzprueba&vista=TODOS")
            .andExpect(jsonPath("$.totalElements").value(4));

        buscar("?q=zzprueba&vista=BIBLIOTECA")
            .andExpect(jsonPath("$.content[*].titulo", contains("Zzprueba Alfa", "Zzprueba Beta")));

        buscar("?q=zzprueba&vista=DISPONIBLES")
            .andExpect(jsonPath("$.content[*].titulo", contains("Zzprueba Alfa")));
    }

    @Test
    void disponibleSeCalculaConLosEjemplaresReales() throws Exception {
        crearEscenario();

        // Orden por título: Alfa, Beta, Delta, Gamma
        buscar("?q=zzprueba")
            .andExpect(jsonPath("$.content[*].disponible", contains(true, false, false, false)));
    }

    @Test
    void losFiltrosSeCombinan() throws Exception {
        crearEscenario();

        buscar("?q=ana&categoria=historia&vista=DISPONIBLES")
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].titulo").value("Zzprueba Alfa"));
    }

    @Test
    void ordenaYPagina() throws Exception {
        crearEscenario();

        // Descendente: Gamma, Delta, Beta, Alfa -> la segunda página de tamaño 2 es Beta, Alfa
        buscar("?q=zzprueba&sort=titulo,desc&page=1&size=2")
            .andExpect(jsonPath("$.page").value(1))
            .andExpect(jsonPath("$.totalPages").value(2))
            .andExpect(jsonPath("$.content[*].titulo", contains("Zzprueba Beta", "Zzprueba Alfa")));
    }

    @Test
    void limitaElTamanoEIgnoraOrdenesNoPermitidos() throws Exception {
        buscar("?size=500&sort=isbn,asc")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.size").value(LibroService.TAMANO_MAXIMO));
    }

    @Test
    void vistaNoValidaDevuelve400() throws Exception {
        buscar("?vista=OTRA")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Parámetro de búsqueda no válido."));
    }

    @Test
    void busquedaSinResultadosDevuelvePaginaVacia() throws Exception {
        buscar("?q=ningunlibrotieneestetitulo")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isEmpty())
            .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void losComodinesDelUsuarioSeTratanComoTexto() throws Exception {
        buscar("?q=%25")   // "%" codificado en la URL
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(0));
    }
}

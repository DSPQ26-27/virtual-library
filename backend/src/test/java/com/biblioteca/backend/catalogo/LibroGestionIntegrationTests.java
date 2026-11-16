package com.biblioteca.backend.catalogo;

import com.biblioteca.backend.BackendApplication;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Gestión del catálogo (#51): crear, editar y retirar libros.
 * Cada test se ejecuta en una transacción que se deshace al terminar.
 */
@SpringBootTest(classes = BackendApplication.class)
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class LibroGestionIntegrationTests {

    /** ISBN del Quijote de los datos de ejemplo (V4), que tiene 2 ejemplares. */
    private static final String ISBN_QUIJOTE = "978-84-376-0494-7";

    @Autowired private MockMvc mvc;
    @Autowired private LibroRepository libros;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;

    // ---------- Utilidades ----------

    /** Datos completos de un libro; se pueden cambiar campos antes de enviarlos. */
    private static Map<String, Object> datos(String isbn, String titulo, String autor) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("isbn", isbn);
        d.put("titulo", titulo);
        d.put("autor", autor);
        d.put("editorial", "Editorial de prueba");
        d.put("categoria", "Zzgestion");
        d.put("anioPublicacion", 2020);
        d.put("descripcion", "Descripción de prueba");
        d.put("portadaUrl", "https://covers.example.com/portada.jpg");
        return d;
    }

    private static RequestPostProcessor como(String rol) {
        return user("personal").roles(rol);
    }

    private ResultActions crear(String rol, Object cuerpo) throws Exception {
        return mvc.perform(post("/api/libros").with(como(rol)).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(cuerpo)));
    }

    private ResultActions editar(String rol, long id, Object cuerpo) throws Exception {
        return mvc.perform(put("/api/libros/{id}", id).with(como(rol)).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(cuerpo)));
    }

    private ResultActions retirar(String rol, long id) throws Exception {
        return mvc.perform(delete("/api/libros/{id}", id).with(como(rol)).with(csrf()));
    }

    private ResultActions buscar(String parametros) throws Exception {
        return mvc.perform(get("/api/libros" + parametros).with(como("STUDENT")));
    }

    private long crearYObtenerId(Map<String, Object> d) throws Exception {
        MvcResult r = crear("LIBRARIAN", d).andExpect(status().isCreated()).andReturn();
        return json.readTree(r.getResponse().getContentAsString()).get("id").asLong();
    }

    private long idQuijote() {
        return jdbc.queryForObject("SELECT id FROM libros WHERE isbn = ?", Long.class, ISBN_QUIJOTE);
    }

    // ---------- Crear ----------

    @Test
    void unBibliotecarioCreaUnLibroConTodosSusDatos() throws Exception {
        MvcResult r = crear("LIBRARIAN", datos("978-0-306-40615-7", "Zzgestion Nuevo", "Autora Nueva"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.isbn").value("978-0-306-40615-7"))
            .andExpect(jsonPath("$.titulo").value("Zzgestion Nuevo"))
            .andExpect(jsonPath("$.autor").value("Autora Nueva"))
            .andExpect(jsonPath("$.editorial").value("Editorial de prueba"))
            .andExpect(jsonPath("$.categoria").value("Zzgestion"))
            .andExpect(jsonPath("$.anioPublicacion").value(2020))
            .andExpect(jsonPath("$.descripcion").value("Descripción de prueba"))
            .andExpect(jsonPath("$.portadaUrl").value("https://covers.example.com/portada.jpg"))
            .andExpect(jsonPath("$.origen").value("MANUAL"))
            .andExpect(jsonPath("$.retirado").value(false))
            .andReturn();

        long id = json.readTree(r.getResponse().getContentAsString()).get("id").asLong();
        assertEquals("/api/libros/" + id, r.getResponse().getHeader("Location"));

        Map<String, Object> fila = jdbc.queryForMap(
            "SELECT titulo, descripcion, portada_url, origen, retirado FROM libros WHERE id = ?", id);
        assertEquals("Zzgestion Nuevo", fila.get("titulo"));
        assertEquals("Descripción de prueba", fila.get("descripcion"));
        assertEquals("https://covers.example.com/portada.jpg", fila.get("portada_url"));
        assertEquals("MANUAL", fila.get("origen"));
        assertEquals(false, fila.get("retirado"));
    }

    @Test
    void unAdministradorTambienPuedeCrear() throws Exception {
        crear("ADMIN", datos(null, "Zzgestion Admin", "Autor"))
            .andExpect(status().isCreated());
    }

    // ---------- Permisos ----------

    @Test
    void unEstudiantePuedeVerLaFichaPeroNoModificarElCatalogo() throws Exception {
        long total = libros.count();
        long id = idQuijote();

        mvc.perform(get("/api/libros/{id}", id).with(como("STUDENT")))
            .andExpect(status().isOk());

        crear("STUDENT", datos(null, "Zzgestion Intruso", "Autor")).andExpect(status().isForbidden());
        editar("STUDENT", id, datos(ISBN_QUIJOTE, "Título cambiado", "Autor")).andExpect(status().isForbidden());
        retirar("STUDENT", id).andExpect(status().isForbidden());

        assertEquals(total, libros.count());
        assertEquals("Don Quijote de la Mancha",
            jdbc.queryForObject("SELECT titulo FROM libros WHERE id = ?", String.class, id));
        assertEquals(false,
            jdbc.queryForObject("SELECT retirado FROM libros WHERE id = ?", Boolean.class, id));
    }

    @Test
    void sinSesionDevuelve401() throws Exception {
        mvc.perform(post("/api/libros").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(datos(null, "Zzgestion Anónimo", "Autor"))))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void sinTokenCsrfDevuelve403() throws Exception {
        mvc.perform(post("/api/libros").with(como("LIBRARIAN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(datos(null, "Zzgestion Sin CSRF", "Autor"))))
            .andExpect(status().isForbidden());
    }

    // ---------- Validaciones ----------

    @Test
    void losDatosNoValidosDevuelven400ConUnMensajePorCampo() throws Exception {
        long total = libros.count();
        Map<String, Object> d = datos("123", "   ", null);
        d.put("anioPublicacion", 3000);
        d.put("portadaUrl", "ftp://covers.example.com/portada.jpg");

        crear("LIBRARIAN", d)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Revisa los datos del libro."))
            .andExpect(jsonPath("$.errors.titulo").value("El título es obligatorio."))
            .andExpect(jsonPath("$.errors.autor").value("El autor es obligatorio."))
            .andExpect(jsonPath("$.errors.isbn").value("ISBN no válido: debe tener 10 o 13 dígitos."))
            .andExpect(jsonPath("$.errors.anioPublicacion").value("Año de publicación no válido."))
            .andExpect(jsonPath("$.errors.portadaUrl").value("La portada debe ser una URL http o https."));

        assertEquals(total, libros.count());
    }

    @Test
    void losCamposOpcionalesVaciosSeGuardanComoNull() throws Exception {
        Map<String, Object> d = datos("  ", "Zzgestion Opcionales", "Autor");
        d.put("editorial", "   ");
        d.put("categoria", "");

        crear("LIBRARIAN", d)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.isbn").value(nullValue()))
            .andExpect(jsonPath("$.editorial").value(nullValue()))
            .andExpect(jsonPath("$.categoria").value(nullValue()));
    }

    // ---------- ISBN duplicado ----------

    @Test
    void unIsbnDuplicadoConOtroFormatoDevuelve409() throws Exception {
        // Mismo ISBN que el Quijote, pero sin guiones
        crear("LIBRARIAN", datos("9788437604947", "Otro Quijote", "Otro autor"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("Ya existe un libro con ese ISBN."))
            .andExpect(jsonPath("$.errors.isbn").value("Este ISBN ya está registrado en otro libro."));
    }

    // ---------- Editar ----------

    @Test
    void editarActualizaLosDatosYConservaElOrigen() throws Exception {
        long id = crearYObtenerId(datos("978-0-306-40615-7", "Zzgestion Original", "Autor Original"));
        Map<String, Object> d = datos("978-0-306-40615-7", "Zzgestion Editado", "Autor Editado");
        d.put("anioPublicacion", 1999);
        d.put("descripcion", "Descripción nueva");

        editar("LIBRARIAN", id, d)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value((int) id))
            .andExpect(jsonPath("$.titulo").value("Zzgestion Editado"))
            .andExpect(jsonPath("$.autor").value("Autor Editado"))
            .andExpect(jsonPath("$.anioPublicacion").value(1999))
            .andExpect(jsonPath("$.descripcion").value("Descripción nueva"))
            .andExpect(jsonPath("$.origen").value("MANUAL"));

        assertEquals("Zzgestion Editado",
            jdbc.queryForObject("SELECT titulo FROM libros WHERE id = ?", String.class, id));
    }

    @Test
    void editarConservandoSuPropioIsbnNoEsDuplicado() throws Exception {
        // El Quijote se guarda con su propio ISBN, escrito en otro formato
        editar("LIBRARIAN", idQuijote(), datos("9788437604947", "Don Quijote de la Mancha", "Miguel de Cervantes"))
            .andExpect(status().isOk());
    }

    @Test
    void editarConElIsbnDeOtroLibroDevuelve409() throws Exception {
        long id = crearYObtenerId(datos("978-0-306-40615-7", "Zzgestion Propio", "Autor"));

        editar("LIBRARIAN", id, datos(ISBN_QUIJOTE, "Zzgestion Propio", "Autor"))
            .andExpect(status().isConflict());
    }

    // ---------- Errores ----------

    @Test
    void unLibroInexistenteDevuelve404() throws Exception {
        long inexistente = 999_999_999L;

        mvc.perform(get("/api/libros/{id}", inexistente).with(como("LIBRARIAN")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("El libro no existe."));
        editar("LIBRARIAN", inexistente, datos(null, "Zzgestion", "Autor")).andExpect(status().isNotFound());
        retirar("LIBRARIAN", inexistente).andExpect(status().isNotFound());
    }

    @Test
    void unIdNoNumericoDevuelve400() throws Exception {
        mvc.perform(get("/api/libros/abc").with(como("LIBRARIAN")))
            .andExpect(status().isBadRequest());
    }

    // ---------- Retirar ----------

    @Test
    void retirarEsLogicoYConservaElLibroYSusEjemplares() throws Exception {
        long id = idQuijote();
        int ejemplaresAntes = jdbc.queryForObject(
            "SELECT COUNT(*) FROM ejemplares WHERE libro_id = ?", Integer.class, id);
        assertEquals(2, ejemplaresAntes);

        retirar("LIBRARIAN", id).andExpect(status().isNoContent());
        libros.flush(); // envía el UPDATE pendiente para poder comprobarlo con SQL

        Map<String, Object> fila = jdbc.queryForMap(
            "SELECT retirado, fecha_retirada FROM libros WHERE id = ?", id);
        assertEquals(true, fila.get("retirado"));
        assertNotNull(fila.get("fecha_retirada"));
        assertEquals(ejemplaresAntes, jdbc.queryForObject(
            "SELECT COUNT(*) FROM ejemplares WHERE libro_id = ?", Integer.class, id));

        mvc.perform(get("/api/libros/{id}", id).with(como("LIBRARIAN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.retirado").value(true));
    }

    @Test
    void unLibroRetiradoDesapareceDelCatalogoYDeLasCategorias() throws Exception {
        long id = crearYObtenerId(datos(null, "Zzgestion Retirable", "Autor"));

        buscar("?q=zzgestion retirable").andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/libros/categorias").with(como("STUDENT")))
            .andExpect(jsonPath("$", hasItem("Zzgestion")));

        retirar("LIBRARIAN", id).andExpect(status().isNoContent());

        buscar("?q=zzgestion retirable").andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/libros/categorias").with(como("STUDENT")))
            .andExpect(jsonPath("$", not(hasItem("Zzgestion"))));
    }

    @Test
    void retirarDosVecesNoCambiaLaFechaDeRetirada() throws Exception {
        long id = idQuijote();

        retirar("LIBRARIAN", id).andExpect(status().isNoContent());
        libros.flush();
        Timestamp primera = jdbc.queryForObject(
            "SELECT fecha_retirada FROM libros WHERE id = ?", Timestamp.class, id);

        retirar("LIBRARIAN", id).andExpect(status().isNoContent());
        libros.flush();
        Timestamp segunda = jdbc.queryForObject(
            "SELECT fecha_retirada FROM libros WHERE id = ?", Timestamp.class, id);

        assertNotNull(primera);
        assertEquals(primera, segunda);
    }
}

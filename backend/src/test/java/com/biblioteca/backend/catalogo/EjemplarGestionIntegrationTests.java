package com.biblioteca.backend.catalogo;

import com.biblioteca.backend.BackendApplication;
import com.biblioteca.backend.prestamo.Prestamo;
import com.biblioteca.backend.prestamo.PrestamoRepository;
import com.biblioteca.backend.usuario.Usuario;
import com.biblioteca.backend.usuario.UsuarioRepository;
import jakarta.persistence.EntityManager;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** API real con H2 aislada y rollback: permisos, inventario, validación y retirada lógica. */
@SpringBootTest(classes = BackendApplication.class)
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class EjemplarGestionIntegrationTests {

    @Autowired private MockMvc mvc;
    @Autowired private LibroRepository libros;
    @Autowired private EjemplarRepository ejemplares;
    @Autowired private EjemplarGestionService gestion;
    @Autowired private PrestamoRepository prestamos;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private ObjectMapper json;

    private String ruta(long libroId) {
        return "/api/libros/" + libroId + "/ejemplares";
    }

    private Map<String, Object> datos(String codigo, String estado) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("codigo", codigo);
        datos.put("estado", estado);
        return datos;
    }

    private long nuevoLibro() {
        return libros.saveAndFlush(new Libro(null, "Zzejemplares Prueba", "Autor", null, null, null)).getId();
    }

    private long libroDelEjemplar(String codigo) {
        return ejemplares.findByCodigo(codigo).orElseThrow().getLibro().getId();
    }

    private long idEjemplar(String codigo) {
        return ejemplares.findByCodigo(codigo).orElseThrow().getId();
    }

    private ResultActions listar(long libroId) throws Exception {
        return mvc.perform(get(ruta(libroId)).with(user("lector").roles("STUDENT")));
    }

    private ResultActions crear(String rol, long libroId, Object datos) throws Exception {
        return mvc.perform(post(ruta(libroId)).with(user("personal").roles(rol)).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(datos)));
    }

    private ResultActions editar(String rol, long libroId, long ejemplarId, Object datos) throws Exception {
        return mvc.perform(put(ruta(libroId) + "/" + ejemplarId)
            .with(user("personal").roles(rol)).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(datos)));
    }

    private ResultActions retirar(String rol, long libroId, long ejemplarId) throws Exception {
        return mvc.perform(delete(ruta(libroId) + "/" + ejemplarId)
            .with(user("personal").roles(rol)).with(csrf()));
    }

    private long crearEjemplar(long libroId, String codigo, String estado) throws Exception {
        MvcResult resultado = crear("LIBRARIAN", libroId, datos(codigo, estado))
            .andExpect(status().isCreated()).andReturn();
        return json.readTree(resultado.getResponse().getContentAsString()).get("id").asLong();
    }

    private Prestamo crearPrestamo(long ejemplarId) {
        Usuario usuario = new Usuario();
        usuario.setNombre("Lector de prueba US06");
        usuario.setEmail("us06-" + ejemplarId + "@example.com");
        usuario.setPasswordHash("hash-de-prueba");
        usuarios.saveAndFlush(usuario);
        return prestamos.saveAndFlush(new Prestamo(ejemplares.findById(ejemplarId).orElseThrow(), usuario));
    }

    private ResultActions catalogo(String vista) throws Exception {
        return mvc.perform(get("/api/libros").param("q", "Zzejemplares Prueba").param("vista", vista)
            .with(user("lector").roles("STUDENT")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"STUDENT", "LIBRARIAN", "ADMIN"})
    void todosLosRolesPuedenConsultarLosEjemplares(String rol) throws Exception {
        long libroId = libroDelEjemplar("EJ-0001");
        mvc.perform(get(ruta(libroId)).with(user("usuario").roles(rol)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(2))
            .andExpect(jsonPath("$.disponibles").value(1))
            .andExpect(jsonPath("$.ejemplares.length()").value(2))
            .andExpect(jsonPath("$.ejemplares[0].id").value((int) idEjemplar("EJ-0001")))
            .andExpect(jsonPath("$.ejemplares[0].codigo").value("EJ-0001"))
            .andExpect(jsonPath("$.ejemplares[0].estado").value("DISPONIBLE"))
            .andExpect(jsonPath("$.ejemplares[0].activo").value(true))
            .andExpect(jsonPath("$.ejemplares[1].estado").value("PRESTADO"));
    }

    @Test
    void unLibroSinEjemplaresDevuelveListaVaciaYContadoresCero() throws Exception {
        listar(nuevoLibro()).andExpect(status().isOk())
            .andExpect(jsonPath("$.ejemplares").isEmpty())
            .andExpect(jsonPath("$.total").value(0))
            .andExpect(jsonPath("$.disponibles").value(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"LIBRARIAN", "ADMIN"})
    void elPersonalPuedeCrearEditarYRetirarSinBorrarElEjemplar(String rol) throws Exception {
        long libroId = nuevoLibro();
        MvcResult creado = crear(rol, libroId, datos("  EJ-PERSONAL  ", "DISPONIBLE"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.codigo").value("EJ-PERSONAL"))
            .andExpect(jsonPath("$.estado").value("DISPONIBLE"))
            .andExpect(jsonPath("$.activo").value(true)).andReturn();
        long id = json.readTree(creado.getResponse().getContentAsString()).get("id").asLong();
        assertEquals(ruta(libroId) + "/" + id, creado.getResponse().getHeader("Location"));

        // Su propio código sigue siendo válido al editar, y cambiar el estado se persiste.
        editar(rol, libroId, id, datos("EJ-PERSONAL", "PRESTADO"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("PRESTADO"));
        editar(rol, libroId, id, datos("  EJ-EDITADO  ", "DISPONIBLE"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value((int) id))
            .andExpect(jsonPath("$.codigo").value("EJ-EDITADO"));

        retirar(rol, libroId, id).andExpect(status().isNoContent()).andExpect(content().string(""));
        ejemplares.flush();
        Ejemplar conservado = ejemplares.findById(id).orElseThrow();
        assertFalse(conservado.isActivo());
        assertEquals(libroId, conservado.getLibro().getId());
        assertEquals("EJ-EDITADO", conservado.getCodigo());
        listar(libroId).andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.disponibles").value(0))
            .andExpect(jsonPath("$.ejemplares[0].activo").value(false));
    }

    @Test
    void unEstudianteNoPuedeCrearEditarNiRetirar() throws Exception {
        long libroId = libroDelEjemplar("EJ-0001");
        long id = idEjemplar("EJ-0001");
        long total = ejemplares.count();
        crear("STUDENT", libroId, datos("EJ-INTRUSO", "DISPONIBLE")).andExpect(status().isForbidden());
        editar("STUDENT", libroId, id, datos("EJ-INTRUSO", "PRESTADO")).andExpect(status().isForbidden());
        retirar("STUDENT", libroId, id).andExpect(status().isForbidden());
        assertEquals(total, ejemplares.count());
        assertEquals("EJ-0001", ejemplares.findById(id).orElseThrow().getCodigo());
        assertTrue(ejemplares.findById(id).orElseThrow().isPrestable());
    }

    @Test
    void sinSesionTodasLasOperacionesDevuelven401() throws Exception {
        long libroId = libroDelEjemplar("EJ-0001");
        long id = idEjemplar("EJ-0001");
        String cuerpo = json.writeValueAsString(datos("EJ-ANONIMO", "DISPONIBLE"));
        mvc.perform(get(ruta(libroId))).andExpect(status().isUnauthorized());
        mvc.perform(post(ruta(libroId)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
            .andExpect(status().isUnauthorized());
        mvc.perform(put(ruta(libroId) + "/" + id).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isUnauthorized());
        mvc.perform(delete(ruta(libroId) + "/" + id).with(csrf())).andExpect(status().isUnauthorized());
    }

    @Test
    void lasEscriturasExigenTokenCsrf() throws Exception {
        long libroId = libroDelEjemplar("EJ-0001");
        long id = idEjemplar("EJ-0001");
        String cuerpo = json.writeValueAsString(datos("EJ-SIN-CSRF", "DISPONIBLE"));
        mvc.perform(post(ruta(libroId)).with(user("personal").roles("LIBRARIAN"))
            .contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isForbidden());
        mvc.perform(put(ruta(libroId) + "/" + id).with(user("personal").roles("ADMIN"))
            .contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isForbidden());
        mvc.perform(delete(ruta(libroId) + "/" + id).with(user("personal").roles("LIBRARIAN")))
            .andExpect(status().isForbidden());
    }

    @Test
    void codigoYEstadoSonObligatoriosYLosErroresSonPorCampo() throws Exception {
        long libroId = nuevoLibro();
        crear("LIBRARIAN", libroId, datos("   ", null)).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Revisa los datos del ejemplar."))
            .andExpect(jsonPath("$.errors.codigo").value("El código es obligatorio."))
            .andExpect(jsonPath("$.errors.estado").value("Selecciona un estado válido."));
        crear("LIBRARIAN", libroId, Map.of()).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.codigo").value("El código es obligatorio."))
            .andExpect(jsonPath("$.errors.estado").value("Selecciona un estado válido."));
        assertTrue(ejemplares.findByLibroIdOrderByIdAsc(libroId).isEmpty());
    }

    @Test
    void elCodigoAdmiteHasta50Caracteres() throws Exception {
        long libroId = nuevoLibro();
        crear("LIBRARIAN", libroId, datos("X".repeat(51), "DISPONIBLE"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.codigo").value("El código no puede superar 50 caracteres."));
        crear("LIBRARIAN", libroId, datos("X".repeat(50), "PRESTADO"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.estado").value("PRESTADO"));
        listar(libroId).andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.disponibles").value(0));
    }

    @Test
    void estadoDesconocidoJsonRotoEIdsNoNumericosDevuelven400() throws Exception {
        long libroId = nuevoLibro();
        crear("LIBRARIAN", libroId, datos("EJ-ESTADO", "ROTO"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Revisa los datos del ejemplar."));
        mvc.perform(post(ruta(libroId)).with(user("personal").roles("LIBRARIAN")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors").isMap());
        mvc.perform(get("/api/libros/abc/ejemplares").with(user("lector").roles("STUDENT")))
            .andExpect(status().isBadRequest());
        mvc.perform(put(ruta(libroId) + "/abc").with(user("personal").roles("LIBRARIAN")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(datos("EJ-ID", "DISPONIBLE"))))
            .andExpect(status().isBadRequest());
        assertTrue(ejemplares.findByLibroIdOrderByIdAsc(libroId).isEmpty());
    }

    @Test
    void unLibroOUnEjemplarInexistenteDevuelve404() throws Exception {
        long libroId = nuevoLibro();
        long inexistente = 999_999_999L;
        listar(inexistente).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("El libro no existe."));
        crear("LIBRARIAN", inexistente, datos("EJ-NO-LIBRO", "DISPONIBLE")).andExpect(status().isNotFound());
        editar("LIBRARIAN", libroId, inexistente, datos("EJ-NO-EXISTE", "DISPONIBLE"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("El ejemplar no existe para este libro."));
        retirar("LIBRARIAN", libroId, inexistente).andExpect(status().isNotFound());
    }

    @Test
    void noSePuedeEditarNiRetirarElEjemplarDeOtroLibro() throws Exception {
        long otroLibro = nuevoLibro();
        long id = idEjemplar("EJ-0001");
        editar("LIBRARIAN", otroLibro, id, datos("EJ-AJENO", "PRESTADO"))
            .andExpect(status().isNotFound());
        retirar("ADMIN", otroLibro, id).andExpect(status().isNotFound());
        assertEquals("EJ-0001", ejemplares.findById(id).orElseThrow().getCodigo());
        assertTrue(ejemplares.findById(id).orElseThrow().isPrestable());
        listar(otroLibro).andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void noSePuedeCrearNiEditarConUnCodigoDeOtroLibro() throws Exception {
        long libroId = nuevoLibro();
        long id = crearEjemplar(libroId, "EJ-PROPIO", "DISPONIBLE");
        crear("LIBRARIAN", libroId, datos("  EJ-0001  ", "DISPONIBLE"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("Ya existe un ejemplar con ese código."))
            .andExpect(jsonPath("$.errors.codigo").value("Este código ya está registrado en otro ejemplar."));
        editar("ADMIN", libroId, id, datos("EJ-0001", "PRESTADO")).andExpect(status().isConflict());
        assertEquals("EJ-PROPIO", ejemplares.findById(id).orElseThrow().getCodigo());
        listar(libroId).andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.disponibles").value(1));
    }

    @Test
    void elCodigoDeUnEjemplarRetiradoSigueReservadoGlobalmente() throws Exception {
        long libroId = nuevoLibro();
        long id = crearEjemplar(libroId, "EJ-RESERVADO", "DISPONIBLE");
        retirar("LIBRARIAN", libroId, id).andExpect(status().isNoContent());
        crear("LIBRARIAN", libroId, datos("EJ-RESERVADO", "DISPONIBLE")).andExpect(status().isConflict());
        crear("ADMIN", nuevoLibro(), datos("EJ-RESERVADO", "DISPONIBLE")).andExpect(status().isConflict());
    }

    @Test
    void unEjemplarRetiradoNoSePuedeEditarNiReactivar() throws Exception {
        long libroId = nuevoLibro();
        long id = crearEjemplar(libroId, "EJ-RETIRADO", "DISPONIBLE");
        retirar("LIBRARIAN", libroId, id).andExpect(status().isNoContent());
        editar("ADMIN", libroId, id, datos("EJ-REACTIVADO", "PRESTADO"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("No se puede editar un ejemplar retirado."));
        Ejemplar retirado = ejemplares.findById(id).orElseThrow();
        assertFalse(retirado.isActivo());
        assertEquals("EJ-RETIRADO", retirado.getCodigo());
        assertEquals(EstadoEjemplar.DISPONIBLE, retirado.getEstado());
    }

    @ParameterizedTest
    @ValueSource(strings = {"LIBRARIAN", "ADMIN"})
    void noSePuedeRetirarUnEjemplarMarcadoComoPrestado(String rol) throws Exception {
        long libroId = libroDelEjemplar("EJ-0002");
        long id = idEjemplar("EJ-0002");
        long total = ejemplares.count();
        retirar(rol, libroId, id).andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("No se puede retirar un ejemplar con un préstamo activo."));
        ejemplares.flush();
        assertEquals(total, ejemplares.count());
        Ejemplar conservado = ejemplares.findById(id).orElseThrow();
        assertTrue(conservado.isActivo());
        assertEquals(EstadoEjemplar.PRESTADO, conservado.getEstado());
        listar(libroId).andExpect(jsonPath("$.total").value(2)).andExpect(jsonPath("$.disponibles").value(1));
    }

    @Test
    void retirarSigueSiendoIdempotenteInclusoParaUnRetiradoPrestadoAnterior() throws Exception {
        long libroId = libroDelEjemplar("EJ-0002");
        long id = idEjemplar("EJ-0002");
        Ejemplar anterior = ejemplares.findById(id).orElseThrow();
        anterior.retirar(); // Representa una retirada anterior a la protección de préstamos.
        ejemplares.saveAndFlush(anterior);
        retirar("LIBRARIAN", libroId, id).andExpect(status().isNoContent());
        retirar("ADMIN", libroId, id).andExpect(status().isNoContent());
        assertFalse(ejemplares.findById(id).orElseThrow().isActivo());
        assertEquals(EstadoEjemplar.PRESTADO, ejemplares.findById(id).orElseThrow().getEstado());
    }

    @ParameterizedTest
    @ValueSource(strings = {"LIBRARIAN", "ADMIN"})
    void unPrestamoRealActivoImpideRetirarAunqueElEstadoEsteDesincronizado(String rol) throws Exception {
        long libroId = nuevoLibro();
        long id = crearEjemplar(libroId, "EJ-PRESTAMO-ACTIVO", "DISPONIBLE");
        Prestamo prestamo = crearPrestamo(id);
        retirar(rol, libroId, id).andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("No se puede retirar un ejemplar con un préstamo activo."));
        ejemplares.flush();
        entityManager.clear();
        assertTrue(ejemplares.findById(id).orElseThrow().isActivo());
        assertEquals(EstadoEjemplar.DISPONIBLE, ejemplares.findById(id).orElseThrow().getEstado());
        assertNull(prestamos.findById(prestamo.getId()).orElseThrow().getFechaDevolucion());
        listar(libroId).andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.disponibles").value(0))
            .andExpect(jsonPath("$.ejemplares[0].estado").value("PRESTADO"));
    }

    @Test
    void devolverPermiteRetirarSinBorrarElHistorialNiCambiarSusDatos() throws Exception {
        long libroId = nuevoLibro();
        long id = crearEjemplar(libroId, "EJ-CON-HISTORIAL", "PRESTADO");
        Prestamo prestamo = crearPrestamo(id);
        long prestamoId = prestamo.getId();
        long usuarioId = prestamo.getUsuario().getId();
        prestamo.devolver();
        prestamos.saveAndFlush(prestamo);
        entityManager.clear();
        Prestamo devuelto = prestamos.findById(prestamoId).orElseThrow();
        var fechaPrestamo = devuelto.getFechaPrestamo();
        var fechaDevolucion = devuelto.getFechaDevolucion();
        long totalHistorial = prestamos.count();

        editar("LIBRARIAN", libroId, id, datos("EJ-CON-HISTORIAL", "DISPONIBLE"))
            .andExpect(status().isOk());
        retirar("ADMIN", libroId, id).andExpect(status().isNoContent());
        retirar("LIBRARIAN", libroId, id).andExpect(status().isNoContent());
        ejemplares.flush();
        entityManager.clear();

        assertFalse(ejemplares.findById(id).orElseThrow().isActivo());
        assertEquals(totalHistorial, prestamos.count());
        Prestamo conservado = prestamos.findById(prestamoId).orElseThrow();
        assertEquals(id, conservado.getEjemplar().getId());
        assertEquals(usuarioId, conservado.getUsuario().getId());
        assertEquals(fechaPrestamo, conservado.getFechaPrestamo());
        assertEquals(fechaDevolucion, conservado.getFechaDevolucion());
        assertNotNull(conservado.getFechaDevolucion());
        assertTrue(usuarios.existsById(usuarioId));
    }

    @Test
    void editarNoPuedeFingirUnaDevolucionPeroSiCambiarElCodigo() throws Exception {
        long libroId = nuevoLibro();
        long id = crearEjemplar(libroId, "EJ-PRESTADO-EDITABLE", "PRESTADO");
        Prestamo prestamo = crearPrestamo(id);
        editar("LIBRARIAN", libroId, id, datos("EJ-DEVOLUCION-FALSA", "DISPONIBLE"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("No se puede cambiar el estado de un ejemplar con un préstamo activo."));
        assertEquals("EJ-PRESTADO-EDITABLE", ejemplares.findById(id).orElseThrow().getCodigo());
        assertEquals(EstadoEjemplar.PRESTADO, ejemplares.findById(id).orElseThrow().getEstado());
        assertNull(prestamos.findById(prestamo.getId()).orElseThrow().getFechaDevolucion());
        editar("ADMIN", libroId, id, datos("EJ-CODIGO-CORREGIDO", "PRESTADO"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.codigo").value("EJ-CODIGO-CORREGIDO"));
        assertTrue(prestamos.existsByEjemplarIdAndFechaDevolucionIsNull(id));
    }

    @Test
    void disponibilidadYVistasConsultanPrestamosRealesYSeRecuperanTrasLaDevolucion() throws Exception {
        long libroId = nuevoLibro();
        long id = crearEjemplar(libroId, "EJ-DISPONIBILIDAD-REAL", "DISPONIBLE");
        Prestamo prestamo = crearPrestamo(id);
        listar(libroId).andExpect(jsonPath("$.disponibles").value(0))
            .andExpect(jsonPath("$.ejemplares[0].estado").value("PRESTADO"));
        catalogo("TODOS").andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].disponible").value(false));
        catalogo("DISPONIBLES").andExpect(jsonPath("$.totalElements").value(0));
        catalogo("BIBLIOTECA").andExpect(jsonPath("$.totalElements").value(1));

        prestamo.devolver();
        prestamos.saveAndFlush(prestamo);
        editar("LIBRARIAN", libroId, id, datos("EJ-DISPONIBILIDAD-REAL", "DISPONIBLE"))
            .andExpect(status().isOk());
        listar(libroId).andExpect(jsonPath("$.disponibles").value(1))
            .andExpect(jsonPath("$.ejemplares[0].estado").value("DISPONIBLE"));
        catalogo("TODOS").andExpect(jsonPath("$.content[0].disponible").value(true));
        catalogo("DISPONIBLES").andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void losContadoresYElCatalogoReflejanAltasEdicionesYRetiradas() throws Exception {
        long libroId = nuevoLibro();
        catalogo("BIBLIOTECA").andExpect(jsonPath("$.totalElements").value(0));
        long primero = crearEjemplar(libroId, "EJ-CONTADOR-1", "DISPONIBLE");
        long segundo = crearEjemplar(libroId, "EJ-CONTADOR-2", "DISPONIBLE");
        crearEjemplar(libroId, "EJ-CONTADOR-3", "PRESTADO");
        listar(libroId).andExpect(jsonPath("$.total").value(3)).andExpect(jsonPath("$.disponibles").value(2));
        catalogo("DISPONIBLES").andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].disponible").value(true));

        retirar("LIBRARIAN", libroId, primero).andExpect(status().isNoContent());
        listar(libroId).andExpect(jsonPath("$.total").value(3)).andExpect(jsonPath("$.disponibles").value(1));
        editar("ADMIN", libroId, segundo, datos("EJ-CONTADOR-2", "PRESTADO")).andExpect(status().isOk());
        listar(libroId).andExpect(jsonPath("$.total").value(3)).andExpect(jsonPath("$.disponibles").value(0));
        catalogo("DISPONIBLES").andExpect(jsonPath("$.totalElements").value(0));
        catalogo("BIBLIOTECA").andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].disponible").value(false));

        editar("LIBRARIAN", libroId, segundo, datos("EJ-CONTADOR-2", "DISPONIBLE")).andExpect(status().isOk());
        catalogo("DISPONIBLES").andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void unaEdicionSimultaneaEsperaLaRetiradaYNoReactivaElEjemplar() throws Exception {
        long libroId = nuevoLibro();
        long id = crearEjemplar(libroId, "EJ-CONCURRENCIA", "DISPONIBLE");
        var transaccion = new TransactionTemplate(transactionManager);
        var bloqueoAdquirido = new CountDownLatch(1);
        var confirmarRetirada = new CountDownLatch(1);
        var edicionIniciada = new CountDownLatch(1);
        var trabajadores = Executors.newFixedThreadPool(2);
        try {
            var retirada = trabajadores.submit(() -> transaccion.executeWithoutResult(estado -> {
                ejemplares.findByIdAndLibroId(id, libroId).orElseThrow();
                bloqueoAdquirido.countDown();
                try {
                    if (!confirmarRetirada.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("No llegó la confirmación de la prueba.");
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(ex);
                }
                gestion.retirar(libroId, id);
            }));
            assertTrue(bloqueoAdquirido.await(5, TimeUnit.SECONDS));
            var edicion = trabajadores.submit(() -> {
                edicionIniciada.countDown();
                return gestion.editar(libroId, id,
                    new EjemplarRequest("EJ-CONCURRENCIA-EDITADO", EstadoEjemplar.DISPONIBLE));
            });
            assertTrue(edicionIniciada.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> edicion.get(200, TimeUnit.MILLISECONDS));

            confirmarRetirada.countDown();
            retirada.get(5, TimeUnit.SECONDS);
            ExecutionException error = assertThrows(ExecutionException.class,
                () -> edicion.get(5, TimeUnit.SECONDS));
            assertInstanceOf(EjemplarRetiradoException.class, error.getCause());
            Ejemplar conservado = ejemplares.findById(id).orElseThrow();
            assertFalse(conservado.isActivo());
            assertEquals("EJ-CONCURRENCIA", conservado.getCodigo());
        } finally {
            confirmarRetirada.countDown();
            trabajadores.shutdownNow();
            assertTrue(trabajadores.awaitTermination(5, TimeUnit.SECONDS));
            transaccion.executeWithoutResult(estado -> {
                ejemplares.deleteById(id);
                ejemplares.flush();
                libros.deleteById(libroId);
            });
        }
    }
}

package com.biblioteca.backend.prestamo;

import com.biblioteca.backend.BackendApplication;
import com.biblioteca.backend.auth.LibraryUserDetails;
import com.biblioteca.backend.catalogo.Ejemplar;
import com.biblioteca.backend.catalogo.EjemplarRepository;
import com.biblioteca.backend.catalogo.EstadoEjemplar;
import com.biblioteca.backend.catalogo.Libro;
import com.biblioteca.backend.catalogo.LibroRepository;
import com.biblioteca.backend.usuario.Usuario;
import com.biblioteca.backend.usuario.UsuarioRepository;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** POST /api/prestamos con H2 y rollback: préstamo válido, reglas, errores, permisos y concurrencia (Issue #58). */
@SpringBootTest(classes = BackendApplication.class)
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class PrestamoIntegrationTests {

    @Autowired private MockMvc mvc;
    @Autowired private PrestamoService prestamoService;
    @Autowired private PrestamoRepository prestamos;
    @Autowired private LibroRepository libros;
    @Autowired private EjemplarRepository ejemplares;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;

    // --- Datos de prueba ---

    private Usuario estudiante(String email) {
        Usuario usuario = new Usuario();
        usuario.setNombre("Estudiante de prueba");
        usuario.setEmail(email);
        usuario.setPasswordHash("hash-de-prueba");
        return usuarios.saveAndFlush(usuario);
    }

    /** Crea un libro con un ejemplar DISPONIBLE por cada código. */
    private Libro libro(String titulo, String... codigos) {
        Libro libro = libros.saveAndFlush(new Libro(null, titulo, "Autor de prueba", null, "Pruebas", 2020));
        for (String codigo : codigos) {
            ejemplares.saveAndFlush(new Ejemplar(libro, codigo));
        }
        return libro;
    }

    private String estadoEjemplar(String codigo) {
        return jdbc.queryForObject("SELECT estado FROM ejemplares WHERE codigo = ?", String.class, codigo);
    }

    private long activos(Usuario usuario) {
        return prestamos.countByUsuarioIdAndEstado(usuario.getId(), EstadoPrestamo.ACTIVO);
    }

    // --- Peticiones ---

    private ResultActions pedirConCuerpo(Usuario estudiante, String cuerpo) throws Exception {
        return mvc.perform(post("/api/prestamos")
            .with(user(new LibraryUserDetails(estudiante))).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private ResultActions pedir(Usuario estudiante, long libroId) throws Exception {
        return pedirConCuerpo(estudiante, "{\"libroId\": " + libroId + "}");
    }

    // --- A) Préstamo válido ---

    @Test
    void unEstudiantePideUnLibroYSeLePrestaUnEjemplarLibre() throws Exception {
        Usuario ana = estudiante("prestamos-ana@example.com");
        Libro libro = libro("Zzprestamos Libro", "EJ-PR-1", "EJ-PR-2");

        String respuesta = pedir(ana, libro.getId())
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.libroId").value(libro.getId()))
            .andExpect(jsonPath("$.titulo").value("Zzprestamos Libro"))
            .andExpect(jsonPath("$.ejemplarCodigo").value("EJ-PR-1"))
            .andExpect(jsonPath("$.estado").value("ACTIVO"))
            .andReturn().getResponse().getContentAsString();

        LocalDateTime inicio = LocalDateTime.parse(JsonPath.read(respuesta, "$.fechaPrestamo"));
        LocalDateTime vencimiento = LocalDateTime.parse(JsonPath.read(respuesta, "$.fechaVencimiento"));
        assertEquals(inicio.plusDays(30), vencimiento);
        assertEquals("PRESTADO", estadoEjemplar("EJ-PR-1"));
        assertEquals("DISPONIBLE", estadoEjemplar("EJ-PR-2"));
        assertEquals(1, activos(ana));
    }

    @Test
    void cadaPrestamoUsaOtroEjemplarYCuandoNoQuedanDevuelve409() throws Exception {
        Libro libro = libro("Zzprestamos Dos Copias", "EJ-PR-A", "EJ-PR-B");

        pedir(estudiante("prestamos-ana@example.com"), libro.getId())
            .andExpect(status().isCreated()).andExpect(jsonPath("$.ejemplarCodigo").value("EJ-PR-A"));
        pedir(estudiante("prestamos-luis@example.com"), libro.getId())
            .andExpect(status().isCreated()).andExpect(jsonPath("$.ejemplarCodigo").value("EJ-PR-B"));
        pedir(estudiante("prestamos-marta@example.com"), libro.getId())
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.codigo").value("SIN_EJEMPLARES"))
            .andExpect(jsonPath("$.message").value("No quedan ejemplares disponibles de este libro."));
    }

    // --- B) Reglas de negocio ---

    @Test
    void noSePrestanEjemplaresRetiradosNiPrestados() throws Exception {
        Libro libro = libro("Zzprestamos Sin Copias Libres", "EJ-PR-RETIRADO", "EJ-PR-PRESTADO");
        Ejemplar retirado = ejemplares.findByCodigo("EJ-PR-RETIRADO").orElseThrow();
        retirado.retirar();
        Ejemplar prestado = ejemplares.findByCodigo("EJ-PR-PRESTADO").orElseThrow();
        prestado.actualizarDatos("EJ-PR-PRESTADO", EstadoEjemplar.PRESTADO);
        ejemplares.flush();

        pedir(estudiante("prestamos-ana@example.com"), libro.getId())
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.codigo").value("SIN_EJEMPLARES"));
    }

    @Test
    void permiteCincoPrestamosYRechazaElSexto() throws Exception {
        Usuario ana = estudiante("prestamos-ana@example.com");
        for (int i = 1; i <= 5; i++) {
            pedir(ana, libro("Zzprestamos Limite " + i, "EJ-PR-L" + i).getId())
                .andExpect(status().isCreated());
        }
        Libro sexto = libro("Zzprestamos Limite 6", "EJ-PR-L6");

        pedir(ana, sexto.getId())
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.codigo").value("LIMITE_PRESTAMOS"))
            .andExpect(jsonPath("$.message").value("Has alcanzado el límite de 5 préstamos activos."));

        assertEquals(5, activos(ana));
        assertEquals("DISPONIBLE", estadoEjemplar("EJ-PR-L6"));
    }

    @Test
    void unPrestamoVencidoBloqueaYAlDevolverloSeDesbloquea() throws Exception {
        Usuario ana = estudiante("prestamos-ana@example.com");
        libro("Zzprestamos Vencido", "EJ-PR-VENCIDO");
        Libro nuevo = libro("Zzprestamos Nuevo", "EJ-PR-NUEVO");
        long ejemplarVencido = ejemplares.findByCodigo("EJ-PR-VENCIDO").orElseThrow().getId();
        LocalDateTime ahora = LocalDateTime.now();
        jdbc.update("""
            INSERT INTO prestamos (ejemplar_id, usuario_id, fecha_prestamo, fecha_vencimiento, estado)
            VALUES (?, ?, ?, ?, 'ACTIVO')
            """, ejemplarVencido, ana.getId(), ahora.minusDays(40), ahora.minusDays(10));

        pedir(ana, nuevo.getId())
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.codigo").value("PRESTAMOS_VENCIDOS"));
        assertEquals("DISPONIBLE", estadoEjemplar("EJ-PR-NUEVO"));

        jdbc.update("UPDATE prestamos SET estado = 'DEVUELTO', fecha_devolucion = ? WHERE ejemplar_id = ?",
            ahora, ejemplarVencido);

        pedir(ana, nuevo.getId()).andExpect(status().isCreated());
    }

    // --- C) Errores de petición ---

    @Test
    void unLibroInexistenteORetiradoDevuelve404() throws Exception {
        Usuario ana = estudiante("prestamos-ana@example.com");
        Libro retirado = libro("Zzprestamos Retirado", "EJ-PR-LIBRO-RETIRADO");
        retirado.retirar();
        libros.flush();

        pedir(ana, 999_999_999L)
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("El libro no existe."));
        pedir(ana, retirado.getId())
            .andExpect(status().isNotFound());
        assertEquals(0, activos(ana));
    }

    @Test
    void losDatosNoValidosDevuelven400() throws Exception {
        Usuario ana = estudiante("prestamos-ana@example.com");

        pedirConCuerpo(ana, "{}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Revisa los datos del préstamo."))
            .andExpect(jsonPath("$.errors.libroId").value("Indica el libro que quieres pedir."));
        pedirConCuerpo(ana, "{\"libroId\": -3}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.libroId").value("El libro no es válido."));
        pedirConCuerpo(ana, "{\"libroId\": \"abc\"}")
            .andExpect(status().isBadRequest());
    }

    // --- D) Permisos ---

    @Test
    void sinSesionDevuelve401() throws Exception {
        mvc.perform(post("/api/prestamos").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"libroId\": 1}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void sinTokenCsrfDevuelve403() throws Exception {
        Usuario ana = estudiante("prestamos-ana@example.com");
        Libro libro = libro("Zzprestamos Csrf", "EJ-PR-CSRF");

        mvc.perform(post("/api/prestamos").with(user(new LibraryUserDetails(ana)))
                .contentType(MediaType.APPLICATION_JSON).content("{\"libroId\": " + libro.getId() + "}"))
            .andExpect(status().isForbidden());
        assertEquals(0, activos(ana));
    }

    @ParameterizedTest
    @ValueSource(strings = {"LIBRARIAN", "ADMIN"})
    void bibliotecariosYAdministradoresNoPuedenPedirPrestamos(String rol) throws Exception {
        Libro libro = libro("Zzprestamos Personal", "EJ-PR-PERSONAL");

        mvc.perform(post("/api/prestamos").with(user("personal").roles(rol)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"libroId\": " + libro.getId() + "}"))
            .andExpect(status().isForbidden());
        assertEquals("DISPONIBLE", estadoEjemplar("EJ-PR-PERSONAL"));
    }

    // --- E) Concurrencia ---

    /** Cada hilo espera la señal de salida y devuelve PRESTADO o el código del rechazo. */
    private Callable<String> pedirEnHilo(long usuarioId, long libroId, CountDownLatch salida) {
        return () -> {
            salida.await();
            try {
                prestamoService.prestar(usuarioId, libroId);
                return "PRESTADO";
            } catch (PrestamoRechazadoException ex) {
                return ex.getCodigo();
            }
        };
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void dosEstudiantesALaVezPorElUltimoEjemplarSoloUnoLoConsigue() throws Exception {
        var transaccion = new TransactionTemplate(transactionManager);
        long[] ids = transaccion.execute(estado -> new long[] {
            estudiante("concurrencia-ana@example.com").getId(),
            estudiante("concurrencia-luis@example.com").getId(),
            libro("Zzprestamos Concurrencia", "EJ-PR-CONCURRENCIA").getId()
        });
        long libroId = ids[2];
        var hilos = Executors.newFixedThreadPool(2);
        try {
            var salida = new CountDownLatch(1);
            var ana = hilos.submit(pedirEnHilo(ids[0], libroId, salida));
            var luis = hilos.submit(pedirEnHilo(ids[1], libroId, salida));
            salida.countDown();

            List<String> resultados = new ArrayList<>(List.of(
                ana.get(10, TimeUnit.SECONDS), luis.get(10, TimeUnit.SECONDS)));
            resultados.sort(null);

            assertEquals(List.of("PRESTADO", "SIN_EJEMPLARES"), resultados);
            assertEquals(1, jdbc.queryForObject("""
                SELECT COUNT(*) FROM prestamos p JOIN ejemplares e ON e.id = p.ejemplar_id
                WHERE e.libro_id = ?
                """, Integer.class, libroId));
            assertEquals("PRESTADO", estadoEjemplar("EJ-PR-CONCURRENCIA"));
        } finally {
            hilos.shutdownNow();
            jdbc.update("DELETE FROM prestamos WHERE ejemplar_id IN (SELECT id FROM ejemplares WHERE libro_id = ?)", libroId);
            jdbc.update("DELETE FROM ejemplares WHERE libro_id = ?", libroId);
            jdbc.update("DELETE FROM libros WHERE id = ?", libroId);
            jdbc.update("DELETE FROM usuarios WHERE id IN (?, ?)", ids[0], ids[1]);
        }
    }
}

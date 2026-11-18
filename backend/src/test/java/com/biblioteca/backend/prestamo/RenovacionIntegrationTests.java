package com.biblioteca.backend.prestamo;

import com.biblioteca.backend.auth.LibraryUserDetails;
import com.biblioteca.backend.catalogo.*;
import com.biblioteca.backend.espera.SolicitudEspera;
import com.biblioteca.backend.espera.SolicitudEsperaRepository;
import com.biblioteca.backend.usuario.*;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class RenovacionIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired PrestamoService servicio;
    @Autowired PrestamoRepository prestamos;
    @Autowired UsuarioRepository usuarios;
    @Autowired LibroRepository libros;
    @Autowired EjemplarRepository ejemplares;
    @Autowired SolicitudEsperaRepository espera;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;
    @Autowired PlatformTransactionManager manager;

    record Datos(Usuario estudiante, Usuario otro, Long libro, Long ejemplar, Long prestamo) {}

    private Usuario estudiante() {
        Usuario usuario = new Usuario();
        usuario.setNombre("Renovación");
        usuario.setEmail(UUID.randomUUID() + "@example.com");
        usuario.setPasswordHash("hash");
        return usuarios.saveAndFlush(usuario);
    }

    private Datos crear() {
        Usuario estudiante = estudiante();
        Usuario otro = estudiante();
        Libro libro = libros.saveAndFlush(new Libro(null, "Renovación", "Autor", null, "Pruebas", 2020));
        Ejemplar ejemplar = new Ejemplar(libro, UUID.randomUUID().toString());
        ejemplar.prestar();
        ejemplares.saveAndFlush(ejemplar);
        Prestamo prestamo = prestamos.saveAndFlush(new Prestamo(ejemplar, estudiante));
        entityManager.clear();
        return new Datos(estudiante, otro, libro.getId(), ejemplar.getId(), prestamo.getId());
    }

    private ResultActions renovar(Usuario usuario, Long prestamoId) throws Exception {
        return mvc.perform(post("/api/prestamos/" + prestamoId + "/renovacion")
            .with(user(new LibraryUserDetails(usuario))).with(csrf()));
    }

    private Prestamo releer(Datos d) {
        entityManager.flush();
        entityManager.clear();
        return prestamos.findById(d.prestamo()).orElseThrow();
    }

    private void solicitud(Datos d, String estado) {
        SolicitudEspera solicitud = espera.saveAndFlush(new SolicitudEspera(
            libros.findById(d.libro()).orElseThrow(), d.otro()));
        jdbc.update("UPDATE solicitudes_espera SET estado = ? WHERE id = ?", estado, solicitud.getId());
        entityManager.clear();
    }

    @Test
    void permiteDosRenovacionesDe30DiasDesdeElVencimientoYRechazaLaTercera() throws Exception {
        Datos d = crear();
        LocalDateTime vencimiento = releer(d).getFechaVencimiento();
        assertEquals(0, releer(d).getNumeroRenovaciones());
        for (int numero = 1; numero <= 2; numero++) {
            renovar(d.estudiante(), d.prestamo())
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(d.prestamo()))
                .andExpect(jsonPath("$.estado").value("ACTIVO"))
                .andExpect(jsonPath("$.numeroRenovaciones").value(numero))
                .andExpect(jsonPath("$.fechaVencimiento").isNotEmpty());
            Prestamo guardado = releer(d);
            assertEquals(numero, guardado.getNumeroRenovaciones());
            assertEquals(vencimiento.plusDays(30L * numero), guardado.getFechaVencimiento());
            assertNull(guardado.getFechaDevolucion());
            mvc.perform(get("/api/prestamos/mios").with(user(new LibraryUserDetails(d.estudiante()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.activos[0].numeroRenovaciones").value(numero));
        }
        renovar(d.estudiante(), d.prestamo()).andExpect(status().isConflict())
            .andExpect(jsonPath("$.codigo").value("LIMITE_RENOVACIONES"));
        assertEquals(2, releer(d).getNumeroRenovaciones());
        assertEquals(vencimiento.plusDays(60), releer(d).getFechaVencimiento());
        assertEquals(EstadoEjemplar.PRESTADO, ejemplares.findById(d.ejemplar()).orElseThrow().getEstado());
        mvc.perform(get("/api/prestamos/mios").with(user(new LibraryUserDetails(d.estudiante()))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.activos[0].fechaVencimiento").isNotEmpty());
    }

    @Test
    void rechazaPrestamoAjenoEInexistenteSinCambios() throws Exception {
        Datos d = crear();
        LocalDateTime vencimiento = releer(d).getFechaVencimiento();
        renovar(d.otro(), d.prestamo()).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.codigo").value("PRESTAMO_NO_ENCONTRADO"));
        renovar(d.estudiante(), -1L).andExpect(status().isNotFound());
        assertEquals(0, releer(d).getNumeroRenovaciones());
        assertEquals(vencimiento, releer(d).getFechaVencimiento());
    }

    @ParameterizedTest
    @ValueSource(strings = {"DEVUELTO", "CANCELADO"})
    void rechazaPrestamosDevueltosOCancelados(String estado) throws Exception {
        Datos d = crear();
        LocalDateTime vencimiento = releer(d).getFechaVencimiento();
        jdbc.update("UPDATE prestamos SET estado = ?, fecha_devolucion = ? WHERE id = ?",
            estado, estado.equals("DEVUELTO") ? LocalDateTime.now() : null, d.prestamo());
        entityManager.clear();
        renovar(d.estudiante(), d.prestamo()).andExpect(status().isConflict())
            .andExpect(jsonPath("$.codigo").value("PRESTAMO_NO_ACTIVO"));
        assertEquals(0, releer(d).getNumeroRenovaciones());
        assertEquals(vencimiento, releer(d).getFechaVencimiento());
        if (estado.equals("CANCELADO")) {
            assertFalse(prestamos.tienePrestamoActivo(d.ejemplar()));
            assertTrue(prestamos.findEjemplarIdsConPrestamoActivo(List.of(d.ejemplar())).isEmpty());
            jdbc.update("UPDATE ejemplares SET estado = 'DISPONIBLE' WHERE id = ?", d.ejemplar());
            entityManager.clear();
            assertEquals(1, ejemplares.findPrestables(d.libro()).size());
            assertTrue(ejemplares.findLibrosConEjemplarPrestable(List.of(d.libro())).contains(d.libro()));
            assertEquals(EstadoPrestamo.CANCELADO, servicio.misPrestamos(d.estudiante().getId()).historial().getFirst().estado());
        }
    }

    @Test
    void rechazaPrestamoVencidoSinCambios() throws Exception {
        Datos d = crear();
        LocalDateTime inicio = LocalDateTime.now().minusDays(60).withNano(0);
        jdbc.update("UPDATE prestamos SET fecha_prestamo = ?, fecha_vencimiento = ? WHERE id = ?",
            inicio, inicio.plusDays(30), d.prestamo());
        entityManager.clear();
        renovar(d.estudiante(), d.prestamo()).andExpect(status().isConflict())
            .andExpect(jsonPath("$.codigo").value("PRESTAMO_VENCIDO"));
        assertEquals(inicio.plusDays(30), releer(d).getFechaVencimiento());
        assertEquals(0, releer(d).getNumeroRenovaciones());
    }

    @ParameterizedTest
    @ValueSource(strings = {"INACTIVO", "OTRO_PRESTAMO_VENCIDO"})
    void rechazaEstudiantesBloqueados(String bloqueo) throws Exception {
        Datos d = crear();
        LocalDateTime vencimiento = releer(d).getFechaVencimiento();
        if (bloqueo.equals("INACTIVO")) {
            jdbc.update("UPDATE usuarios SET activo = false WHERE id = ?", d.estudiante().getId());
        } else {
            Ejemplar otro = ejemplares.saveAndFlush(new Ejemplar(libros.findById(d.libro()).orElseThrow(), UUID.randomUUID().toString()));
            otro.prestar();
            Prestamo vencido = prestamos.saveAndFlush(new Prestamo(otro, d.estudiante()));
            LocalDateTime inicio = LocalDateTime.now().minusDays(60);
            jdbc.update("UPDATE prestamos SET fecha_prestamo = ?, fecha_vencimiento = ? WHERE id = ?",
                inicio, inicio.plusDays(30), vencido.getId());
        }
        entityManager.clear();
        renovar(d.estudiante(), d.prestamo()).andExpect(status().isConflict())
            .andExpect(jsonPath("$.codigo").value("ESTUDIANTE_BLOQUEADO"));
        assertEquals(0, releer(d).getNumeroRenovaciones());
        assertEquals(vencimiento, releer(d).getFechaVencimiento());
    }

    @Test
    void rechazaSolicitudesPendientesSinEjemplaresLibres() throws Exception {
        Datos d = crear();
        solicitud(d, "PENDIENTE");
        LocalDateTime vencimiento = releer(d).getFechaVencimiento();
        renovar(d.estudiante(), d.prestamo()).andExpect(status().isConflict())
            .andExpect(jsonPath("$.codigo").value("LISTA_ESPERA_PENDIENTE"));
        assertEquals(0, releer(d).getNumeroRenovaciones());
        assertEquals(vencimiento, releer(d).getFechaVencimiento());
    }

    @ParameterizedTest
    @ValueSource(strings = {"RETIRADO", "PRESTADO", "CON_PRESTAMO_ACTIVO"})
    void losEjemplaresNoPrestablesNoDesbloqueanLaRenovacion(String tipo) throws Exception {
        Datos d = crear();
        solicitud(d, "PENDIENTE");
        Ejemplar otro = new Ejemplar(libros.findById(d.libro()).orElseThrow(), UUID.randomUUID().toString());
        if (tipo.equals("RETIRADO")) otro.retirar();
        if (tipo.equals("PRESTADO")) otro.prestar();
        ejemplares.saveAndFlush(otro);
        if (tipo.equals("CON_PRESTAMO_ACTIVO")) prestamos.saveAndFlush(new Prestamo(otro, d.otro()));
        entityManager.clear();
        renovar(d.estudiante(), d.prestamo()).andExpect(status().isConflict())
            .andExpect(jsonPath("$.codigo").value("LISTA_ESPERA_PENDIENTE"));
        assertEquals(0, releer(d).getNumeroRenovaciones());
    }

    @Test
    void permiteRenovarConPendientesSiQuedaUnEjemplarLibre() throws Exception {
        Datos d = crear();
        solicitud(d, "PENDIENTE");
        ejemplares.saveAndFlush(new Ejemplar(libros.findById(d.libro()).orElseThrow(), UUID.randomUUID().toString()));
        renovar(d.estudiante(), d.prestamo()).andExpect(status().isOk())
            .andExpect(jsonPath("$.numeroRenovaciones").value(1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ASIGNADA", "CANCELADA", "OTRO_LIBRO"})
    void otrasSolicitudesNoImpidenRenovar(String tipo) throws Exception {
        Datos d = crear();
        if (tipo.equals("OTRO_LIBRO")) {
            Libro otro = libros.saveAndFlush(new Libro(null, "Otro", "Autor", null, "Pruebas", 2020));
            espera.saveAndFlush(new SolicitudEspera(otro, d.otro()));
        } else {
            solicitud(d, tipo);
        }
        renovar(d.estudiante(), d.prestamo()).andExpect(status().isOk())
            .andExpect(jsonPath("$.numeroRenovaciones").value(1));
    }

    @Test
    void exigeSesionEstudianteYCsrf() throws Exception {
        String ruta = "/api/prestamos/1/renovacion";
        mvc.perform(post(ruta).with(csrf())).andExpect(status().isUnauthorized());
        for (String rol : List.of("LIBRARIAN", "ADMIN")) {
            mvc.perform(post(ruta).with(user("personal").roles(rol)).with(csrf()))
                .andExpect(status().isForbidden());
        }
        mvc.perform(post(ruta).with(user("estudiante").roles("STUDENT")))
            .andExpect(status().isForbidden());
        mvc.perform(post(ruta).with(user("estudiante").roles("STUDENT")).with(csrf().useInvalidToken()))
            .andExpect(status().isForbidden());
    }

    @Test
    void swaggerDocumentaRenovacionCsrfYRespuesta() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
            .andExpect(jsonPath("$.paths['/api/prestamos/{id}/renovacion'].post.responses['200']").exists())
            .andExpect(jsonPath("$.paths['/api/prestamos/{id}/renovacion'].post.responses['401']").exists())
            .andExpect(jsonPath("$.paths['/api/prestamos/{id}/renovacion'].post.responses['403']").exists())
            .andExpect(jsonPath("$.paths['/api/prestamos/{id}/renovacion'].post.responses['404']").exists())
            .andExpect(jsonPath("$.paths['/api/prestamos/{id}/renovacion'].post.responses['409']").exists())
            .andExpect(jsonPath("$.paths['/api/prestamos/{id}/renovacion'].post.parameters[?(@.name == 'X-CSRF-TOKEN')]").isNotEmpty())
            .andExpect(jsonPath("$.components.schemas.RenovacionResponse.properties.numeroRenovaciones").exists())
            .andExpect(jsonPath("$.components.schemas.RenovacionResponse.properties.fechaVencimiento").exists());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 3})
    void laBaseDeDatosImpideContadoresFueraDelLimite(int numero) {
        Datos d = crear();
        assertThrows(DataIntegrityViolationException.class,
            () -> jdbc.update("UPDATE prestamos SET numero_renovaciones = ? WHERE id = ?", numero, d.prestamo()));
    }

    private void limpiar(Datos d) {
        jdbc.update("DELETE FROM solicitudes_espera WHERE libro_id = ?", d.libro());
        jdbc.update("DELETE FROM prestamos WHERE ejemplar_id = ?", d.ejemplar());
        jdbc.update("DELETE FROM ejemplares WHERE libro_id = ?", d.libro());
        jdbc.update("DELETE FROM libros WHERE id = ?", d.libro());
        jdbc.update("DELETE FROM usuarios WHERE id IN (?, ?)", d.estudiante().getId(), d.otro().getId());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void tresRenovacionesSimultaneasSoloPermitenDos() throws Exception {
        Datos d = new TransactionTemplate(manager).execute(s -> crear());
        LocalDateTime vencimiento = prestamos.findById(d.prestamo()).orElseThrow().getFechaVencimiento();
        var pool = Executors.newFixedThreadPool(3);
        var inicio = new CountDownLatch(1);
        Callable<String> renovar = () -> {
            if (!inicio.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Sin señal de inicio");
            try { servicio.renovar(d.estudiante().getId(), d.prestamo()); return "OK"; }
            catch (RenovacionRechazadaException e) { return e.getCodigo(); }
        };
        try {
            var a = pool.submit(renovar);
            var b = pool.submit(renovar);
            var c = pool.submit(renovar);
            inicio.countDown();
            var resultados = List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS), c.get(20, TimeUnit.SECONDS));
            assertEquals(2, resultados.stream().filter("OK"::equals).count());
            assertEquals(1, resultados.stream().filter("LIMITE_RENOVACIONES"::equals).count());
            Prestamo guardado = prestamos.findById(d.prestamo()).orElseThrow();
            assertEquals(2, guardado.getNumeroRenovaciones());
            assertEquals(vencimiento.plusDays(60), guardado.getFechaVencimiento());
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(20, TimeUnit.SECONDS);
            limpiar(d);
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void renovarYDevolverSimultaneamenteMantieneElPrestamoCerrado() throws Exception {
        Datos d = new TransactionTemplate(manager).execute(s -> crear());
        LocalDateTime vencimiento = prestamos.findById(d.prestamo()).orElseThrow().getFechaVencimiento();
        var pool = Executors.newFixedThreadPool(2);
        var inicio = new CountDownLatch(1);
        try {
            var renovacion = pool.submit(() -> {
                if (!inicio.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Sin señal de inicio");
                try { servicio.renovar(d.estudiante().getId(), d.prestamo()); return 1; }
                catch (RenovacionRechazadaException e) { assertEquals("PRESTAMO_NO_ACTIVO", e.getCodigo()); return 0; }
            });
            var devolucion = pool.submit(() -> {
                if (!inicio.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Sin señal de inicio");
                return servicio.devolver(d.estudiante().getId(), d.prestamo());
            });
            inicio.countDown();
            int numero = renovacion.get(20, TimeUnit.SECONDS);
            assertEquals(EstadoPrestamo.DEVUELTO, devolucion.get(20, TimeUnit.SECONDS).estado());
            Prestamo guardado = prestamos.findById(d.prestamo()).orElseThrow();
            assertEquals(EstadoPrestamo.DEVUELTO, guardado.getEstado());
            assertEquals(numero, guardado.getNumeroRenovaciones());
            assertEquals(vencimiento.plusDays(30L * numero), guardado.getFechaVencimiento());
            assertEquals(EstadoEjemplar.DISPONIBLE, ejemplares.findById(d.ejemplar()).orElseThrow().getEstado());
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(20, TimeUnit.SECONDS);
            limpiar(d);
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void unRollbackRevierteElContadorYElVencimientoJuntos() {
        Datos d = new TransactionTemplate(manager).execute(s -> crear());
        LocalDateTime vencimiento = prestamos.findById(d.prestamo()).orElseThrow().getFechaVencimiento();
        try {
            assertThrows(IllegalStateException.class, () -> new TransactionTemplate(manager).executeWithoutResult(s -> {
                servicio.renovar(d.estudiante().getId(), d.prestamo());
                prestamos.flush();
                throw new IllegalStateException("Fallo de prueba tras guardar");
            }));
            Prestamo guardado = prestamos.findById(d.prestamo()).orElseThrow();
            assertEquals(0, guardado.getNumeroRenovaciones());
            assertEquals(vencimiento, guardado.getFechaVencimiento());
        } finally { limpiar(d); }
    }
}

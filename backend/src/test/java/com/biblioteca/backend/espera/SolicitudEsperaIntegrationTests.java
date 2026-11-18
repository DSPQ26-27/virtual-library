package com.biblioteca.backend.espera;

import com.biblioteca.backend.auth.LibraryUserDetails;
import com.biblioteca.backend.catalogo.*;
import com.biblioteca.backend.usuario.*;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SolicitudEsperaIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarios;
    @Autowired LibroRepository libros;
    @Autowired EjemplarRepository ejemplares;
    @Autowired SolicitudEsperaRepository solicitudes;
    @Autowired SolicitudEsperaService servicio;
    @Autowired JdbcTemplate jdbc;

    @Test
    void soloPermiteCancelarSolicitudesPropiasConCsrfYConservaHistorial() throws Exception {
        Usuario usuario = estudiante();
        Usuario otro = estudiante();
        Libro libro = libro();
        var solicitud = servicio.apuntarse(usuario.getId(), libro.getId());
        String ruta = "/api/espera/" + solicitud.id() + "/cancelacion";
        mvc.perform(post(ruta).with(user(new LibraryUserDetails(otro))).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(post(ruta).with(user(new LibraryUserDetails(usuario))))
                .andExpect(status().isForbidden());
        mvc.perform(post(ruta).with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(post(ruta).with(user("bibliotecario").roles("LIBRARIAN")).with(csrf()))
                .andExpect(status().isForbidden());
        for (int i = 0; i < 2; i++) {
            mvc.perform(post(ruta).with(user(new LibraryUserDetails(usuario))).with(csrf()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("CANCELADA"));
        }
        assertTrue(solicitudes.existsById(solicitud.id()));
        assertNotEquals(solicitud.id(), servicio.apuntarse(usuario.getId(), libro.getId()).id());
    }

    @Test
    void noPermiteCancelarUnaSolicitudAsignada() throws Exception {
        Usuario usuario = estudiante();
        var solicitud = solicitudes.saveAndFlush(new SolicitudEspera(libro(), usuario));
        solicitud.asignar();
        solicitudes.flush();
        mvc.perform(post("/api/espera/" + solicitud.getId() + "/cancelacion")
                .with(user(new LibraryUserDetails(usuario))).with(csrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("SOLICITUD_ASIGNADA"));
    }
    private Usuario estudiante() {
        Usuario usuario = new Usuario();
        usuario.setNombre("Estudiante de prueba");
        usuario.setEmail(UUID.randomUUID() + "@example.com");
        usuario.setPasswordHash("hash");
        return usuarios.saveAndFlush(usuario);
    }

    private Libro libro() {
        return libros.saveAndFlush(new Libro(null, "Libro de prueba", "Autor", null, "Pruebas", 2020));
    }

    private ResultActions apuntarse(Usuario usuario, String contenido) throws Exception {
        return mvc.perform(post("/api/espera").with(user(new LibraryUserDetails(usuario)))
                .with(csrf()).contentType("application/json").content(contenido));
    }

    @Test
    void registraSolicitudYRechazaDuplicados() throws Exception {
        Usuario usuario = estudiante();
        Libro libro = libro();
        String contenido = "{\"libroId\":" + libro.getId() + "}";
        apuntarse(usuario, contenido).andExpect(status().isCreated())
                .andExpect(jsonPath("$.libroId").value(libro.getId()))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.fechaSolicitud").isNotEmpty());
        apuntarse(usuario, contenido).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_DUPLICADA"));
        assertEquals(1, solicitudes.findByUsuarioIdOrderByFechaSolicitudAscIdAsc(usuario.getId()).size());
    }

    @Test
    void rechazaLibroDisponibleRetiradoEInexistente() throws Exception {
        Usuario usuario = estudiante();
        Libro libro = libro();
        ejemplares.saveAndFlush(new Ejemplar(libro, UUID.randomUUID().toString()));
        apuntarse(usuario, "{\"libroId\":" + libro.getId() + "}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("EJEMPLARES_DISPONIBLES"));
        jdbc.update("UPDATE libros SET retirado = true WHERE id = ?", libro.getId());
        apuntarse(usuario, "{\"libroId\":" + libro.getId() + "}").andExpect(status().isNotFound());
        apuntarse(usuario, "{\"libroId\":9223372036854775807}").andExpect(status().isNotFound());
    }

    @Test
    void validaDatosSesionRolYCsrf() throws Exception {
        Usuario usuario = estudiante();
        for (String contenido : new String[]{"{}", "{\"libroId\":0}", "{\"libroId\":-1}", "{"}) {
            apuntarse(usuario, contenido).andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/espera/mias")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/espera").with(user(new LibraryUserDetails(usuario)))
                .contentType("application/json").content("{\"libroId\":1}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/espera/mias").with(user("bibliotecario").roles("LIBRARIAN")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/espera").with(user("bibliotecario").roles("LIBRARIAN"))
                .with(csrf()).contentType("application/json").content("{\"libroId\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void consultaSoloSolicitudesPropiasYOrdenaLaColaPorFechaEId() throws Exception {
        Usuario primero = estudiante();
        Usuario segundo = estudiante();
        Libro libro = libro();
        SolicitudEspera a = solicitudes.saveAndFlush(new SolicitudEspera(libro, primero));
        SolicitudEspera b = solicitudes.saveAndFlush(new SolicitudEspera(libro, segundo));
        jdbc.update("UPDATE solicitudes_espera SET fecha_solicitud = '2026-01-01 12:00:00' WHERE libro_id = ?",
                libro.getId());
        var cola = solicitudes.findByLibroIdAndEstadoOrderByFechaSolicitudAscIdAsc(
                libro.getId(), EstadoSolicitudEspera.PENDIENTE);
        assertEquals(a.getId(), cola.get(0).getId());
        assertEquals(b.getId(), cola.get(1).getId());
        mvc.perform(get("/api/espera/mias").with(user(new LibraryUserDetails(primero))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(a.getId()));
    }

    @Test
    void rechazaEstudianteInactivo() throws Exception {
        Usuario usuario = estudiante();
        usuario.setActivo(false);
        usuarios.saveAndFlush(usuario);
        apuntarse(usuario, "{\"libroId\":" + libro().getId() + "}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ESTUDIANTE_INACTIVO"));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void dosPeticionesSimultaneasSoloCreanUnaSolicitud() throws Exception {
        Usuario usuario = estudiante();
        Libro libro = libro();
        ExecutorService ejecutor = Executors.newFixedThreadPool(2);
        CountDownLatch inicio = new CountDownLatch(1);
        Callable<Boolean> peticion = () -> {
            inicio.await();
            try {
                servicio.apuntarse(usuario.getId(), libro.getId());
                return true;
            } catch (SolicitudEsperaRechazadaException ex) {
                assertEquals("SOLICITUD_DUPLICADA", ex.getCodigo());
                return false;
            }
        };
        try {
            Future<Boolean> a = ejecutor.submit(peticion);
            Future<Boolean> b = ejecutor.submit(peticion);
            inicio.countDown();
            boolean creadaA = a.get(15, TimeUnit.SECONDS);
            boolean creadaB = b.get(15, TimeUnit.SECONDS);
            assertNotEquals(creadaA, creadaB);
            assertEquals(1, solicitudes.findByUsuarioIdOrderByFechaSolicitudAscIdAsc(usuario.getId()).size());
        } finally {
            ejecutor.shutdownNow();
            ejecutor.awaitTermination(15, TimeUnit.SECONDS);
            jdbc.update("DELETE FROM solicitudes_espera WHERE libro_id = ?", libro.getId());
            libros.deleteById(libro.getId());
            usuarios.deleteById(usuario.getId());
        }
    }
}


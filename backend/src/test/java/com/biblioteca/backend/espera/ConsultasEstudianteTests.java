package com.biblioteca.backend.espera;

import com.biblioteca.backend.auth.LibraryUserDetails;
import com.biblioteca.backend.catalogo.*;
import com.biblioteca.backend.prestamo.*;
import com.biblioteca.backend.usuario.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ConsultasEstudianteTests {
    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarios;
    @Autowired LibroRepository libros;
    @Autowired EjemplarRepository ejemplares;
    @Autowired PrestamoRepository prestamos;
    @Autowired SolicitudEsperaRepository solicitudes;

    private Usuario estudiante() {
        Usuario usuario = new Usuario();
        usuario.setNombre("Estudiante");
        usuario.setEmail(UUID.randomUUID() + "@example.com");
        usuario.setPasswordHash("hash");
        return usuarios.saveAndFlush(usuario);
    }

    @Test
    void ambasConsultasUsanLaSesionAunqueSeEnvieElIdDeOtroUsuario() throws Exception {
        Usuario propio = estudiante();
        Usuario otro = estudiante();
        Libro libro = libros.saveAndFlush(new Libro(null, "Prueba consultas", "Autor", null, "Pruebas", 2020));
        for (Usuario usuario : new Usuario[]{propio, otro}) {
            Ejemplar ejemplar = new Ejemplar(libro, UUID.randomUUID().toString());
            ejemplar.prestar();
            ejemplares.saveAndFlush(ejemplar);
            prestamos.saveAndFlush(new Prestamo(ejemplar, usuario));
            solicitudes.saveAndFlush(new SolicitudEspera(libro, usuario));
        }
        Long prestamoPropio = prestamos.listarPropios(propio.getId()).get(0).id();
        Long solicitudPropia = solicitudes.findByUsuarioIdOrderByFechaSolicitudAscIdAsc(propio.getId()).get(0).getId();

        mvc.perform(get("/api/prestamos/mios").param("usuarioId", otro.getId().toString())
                .with(user(new LibraryUserDetails(propio))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.activos.length()").value(1))
                .andExpect(jsonPath("$.activos[0].id").value(prestamoPropio))
                .andExpect(jsonPath("$.historial.length()").value(0));
        mvc.perform(get("/api/espera/mias").param("usuarioId", otro.getId().toString())
                .with(user(new LibraryUserDetails(propio))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(solicitudPropia));
    }
}

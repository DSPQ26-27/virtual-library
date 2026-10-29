package com.biblioteca.backend.auth;

import com.biblioteca.backend.BackendApplication;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Pruebas de POST /api/auth/register (US01, #22) según docs/contrato-registro.md. */
@SpringBootTest(classes = BackendApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureMockMvc
class RegistrationIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired ObjectMapper json;

    @BeforeEach
    void cleanDatabase() {
        jdbc.update("DELETE FROM usuarios");
    }

    /** Petición anónima: sin sesión, sin cookies y sin token CSRF. */
    private ResultActions register(Object body) throws Exception {
        String content = body instanceof String raw ? raw : json.writeValueAsString(body);
        return mvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON).content(content));
    }

    private int countUsers() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM usuarios", Integer.class);
    }

    @Test
    void validRegistrationCreatesStudentWithHashedPassword() throws Exception {
        var result = register(Map.of("nombre", "  Santi Gaytan  ",
                "email", " Santi@Example.com ", "password", "ClaveSegura123"))
            .andExpect(status().isCreated()).andReturn();

        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        assertTrue(body.get("id").asLong() > 0);
        assertEquals("Santi Gaytan", body.get("nombre").asString());
        assertEquals("santi@example.com", body.get("email").asString());
        assertEquals(3, body.size(), "La respuesta no debe incluir la contraseña ni su hash");

        Map<String, Object> row = jdbc.queryForMap(
            "SELECT rol, activo, password_hash FROM usuarios WHERE email = 'santi@example.com'");
        assertEquals("ESTUDIANTE", row.get("rol"));
        assertEquals(true, row.get("activo"));
        String hash = (String) row.get("password_hash");
        assertNotEquals("ClaveSegura123", hash, "La contraseña no puede guardarse en claro");
        assertTrue(passwords.matches("ClaveSegura123", hash));
    }

    @Test
    void clientCannotChooseItsRole() throws Exception {
        register(Map.of("nombre", "Intruso", "email", "intruso@example.com",
                "password", "ClaveSegura123", "rol", "ADMIN"))
            .andExpect(status().isCreated());
        assertEquals("ESTUDIANTE", jdbc.queryForObject(
            "SELECT rol FROM usuarios WHERE email = 'intruso@example.com'", String.class));
    }

    @Test
    void duplicateEmailIsRejectedIgnoringCase() throws Exception {
        register(Map.of("nombre", "Ana", "email", "ana@example.com", "password", "ClaveSegura123"))
            .andExpect(status().isCreated());

        register(Map.of("nombre", "Otra Ana", "email", "ANA@Example.com", "password", "OtraClave456"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("Ya existe una cuenta con ese correo electrónico."))
            .andExpect(jsonPath("$.errors.email").value("Este correo ya está registrado."));
        assertEquals(1, countUsers());
    }

    @Test
    void invalidDataReturnsOneMessagePerField() throws Exception {
        register(Map.of("nombre", "A", "email", "no-es-un-correo", "password", "corta"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Revisa los datos del formulario."))
            .andExpect(jsonPath("$.errors.nombre").value("El nombre debe tener entre 2 y 100 caracteres."))
            .andExpect(jsonPath("$.errors.email").value("Correo inválido."))
            .andExpect(jsonPath("$.errors.password").value("La contraseña debe tener entre 8 y 72 caracteres."));
        assertEquals(0, countUsers());
    }

    @Test
    void onlyInvalidFieldsAreReported() throws Exception {
        register(Map.of("nombre", "   ", "email", "valido@example.com", "password", "        "))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.nombre").value("El nombre es obligatorio."))
            .andExpect(jsonPath("$.errors.password").value("La contraseña es obligatoria."))
            .andExpect(jsonPath("$.errors.email").doesNotExist());
    }

    @Test
    void passwordLongerThanBcryptLimitIsRejected() throws Exception {
        register(Map.of("nombre", "Largo", "email", "largo@example.com", "password", "a".repeat(73)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void emailOf254CharactersIsAccepted() throws Exception {
        String email = "a".repeat(64) + "@" + "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(61);
        assertEquals(254, email.length());
        register(Map.of("nombre", "Correo Largo", "email", email, "password", "ClaveSegura123"))
            .andExpect(status().isCreated());
    }

    @Test
    void malformedOrEmptyBodyReturns400() throws Exception {
        for (String body : new String[]{"{", "null", "{}"}) {
            register(body).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Revisa los datos del formulario."));
        }
        assertEquals(0, countUsers());
    }
}

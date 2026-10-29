package com.biblioteca.backend.auth;

import com.biblioteca.backend.BackendApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = BackendApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureMockMvc
class AuthIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired ObjectMapper json;
    @LocalServerPort int port;

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM usuarios");
        addUser("alumno@example.com", "ESTUDIANTE", true);
    }

    private void addUser(String email, String role, boolean active) {
        jdbc.update("INSERT INTO usuarios(nombre,email,password_hash,rol,activo) VALUES (?,?,?,?,?)",
            "Alumno de prueba", email, passwords.encode("ClavePrueba123!"), role, active);
    }

    private record CsrfSession(MockHttpSession session, String token) {}

    private CsrfSession csrf(MockHttpSession session) throws Exception {
        var request = get("/api/auth/csrf");
        if (session != null) request.session(session);
        MvcResult result = mvc.perform(request).andExpect(status().isOk())
            .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
            .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
            .andReturn();
        return new CsrfSession((MockHttpSession) result.getRequest().getSession(false),
            json.readTree(result.getResponse().getContentAsString()).get("token").asString());
    }

    private MvcResult login(CsrfSession csrf, String email, String password, int expected) throws Exception {
        return mvc.perform(post("/api/auth/login").session(csrf.session())
            .with(request -> {
                request.setRequestedSessionId(csrf.session().getId());
                request.setRequestedSessionIdValid(true);
                return request;
            })
            .header("X-CSRF-TOKEN", csrf.token()).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(java.util.Map.of("email", email, "password", password))))
            .andExpect(status().is(expected)).andReturn();
    }

    @Test
    void loginPersistsSessionAndMeReturnsSafeUser() throws Exception {
        var csrf = csrf(null);
        String oldId = csrf.session().getId();
        var result = login(csrf, " ALUMNO@example.com ", "ClavePrueba123!", 200);
        JsonNode user = json.readTree(result.getResponse().getContentAsString());
        assertTrue(user.get("id").asLong() > 0);
        assertEquals("Alumno de prueba", user.get("nombre").asString());
        assertEquals("alumno@example.com", user.get("email").asString());
        assertEquals("STUDENT", user.get("roles").get(0).asString());
        assertEquals(4, user.size());
        assertNotEquals(oldId, csrf.session().getId(), "Debe rotarse el id de sesión");
        mvc.perform(get("/api/auth/me").session(csrf.session()))
            .andExpect(status().isOk()).andExpect(content().json(user.toString()));
        var context = (SecurityContext) csrf.session().getAttribute(
            HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertNotNull(context);
        assertNull(context.getAuthentication().getCredentials());
        assertNull(((LibraryUserDetails) context.getAuthentication().getPrincipal()).getPassword());
    }

    @Test
    void invalidCredentialsShareGenericMessageAndDoNotAuthenticate() throws Exception {
        for (String[] pair : new String[][]{{"alumno@example.com", "incorrecta"},
            {"noexiste@example.com", "ClavePrueba123!"}}) {
            var csrf = csrf(null);
            var result = login(csrf, pair[0], pair[1], 401);
            assertEquals("Correo o contraseña incorrectos.",
                json.readTree(result.getResponse().getContentAsString()).get("message").asString());
            mvc.perform(get("/api/auth/me").session(csrf.session())).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void disabledAndUnknownRoleAccountsCannotLogin() throws Exception {
        addUser("inactivo@example.com", "STUDENT", false);
        addUser("sinrol@example.com", "OTHER", true);
        login(csrf(null), "inactivo@example.com", "ClavePrueba123!", 401);
        login(csrf(null), "sinrol@example.com", "ClavePrueba123!", 401);
    }

    @Test
    void ambiguousEmailCannotLogin() throws Exception {
        addUser("ALUMNO@example.com", "STUDENT", true);
        login(csrf(null), "alumno@example.com", "ClavePrueba123!", 401);
    }

    @Test
    void spanishAndEnglishRolesAreMappedFromDatabase() throws Exception {
        for (String[] pair : new String[][]{{"ESTUDIANTE", "STUDENT"}, {"STUDENT", "STUDENT"},
            {"BIBLIOTECARIO", "LIBRARIAN"}, {"LIBRARIAN", "LIBRARIAN"},
            {"ADMINISTRADOR", "ADMIN"}, {"ADMIN", "ADMIN"}}) {
            jdbc.update("UPDATE usuarios SET rol=?", pair[0]);
            var result = login(csrf(null), "alumno@example.com", "ClavePrueba123!", 200);
            assertEquals(pair[1], json.readTree(result.getResponse().getContentAsString())
                .get("roles").get(0).asString());
        }
    }

    @Test
    void malformedOrInvalidLoginReturns400() throws Exception {
        for (String body : new String[]{"{}", "null", "{", "{\"email\":\"mal\",\"password\":\"x\"}",
            "{\"email\":\"alumno@example.com\",\"password\":\"   \"}"}) {
            var csrf = csrf(null);
            mvc.perform(post("/api/auth/login").session(csrf.session())
                .header("X-CSRF-TOKEN", csrf.token()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        }
    }

    @Test
    void loginRequiresActualCsrfTokenAndMatchingSession() throws Exception {
        String body = "{\"email\":\"alumno@example.com\",\"password\":\"ClavePrueba123!\"}";
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isForbidden());
        var csrf = csrf(null);
        mvc.perform(post("/api/auth/login").session(csrf.session()).header("X-CSRF-TOKEN", "incorrecto")
            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/login").header("X-CSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
    }

    @Test
    void csrfRotatesOnLoginAndLogoutInvalidatesSession() throws Exception {
        var before = csrf(null);
        login(before, "alumno@example.com", "ClavePrueba123!", 200);
        mvc.perform(post("/api/auth/logout").session(before.session())
            .header("X-CSRF-TOKEN", before.token())).andExpect(status().isForbidden());
        mvc.perform(get("/api/auth/me").session(before.session())).andExpect(status().isOk());
        var after = csrf(before.session());
        mvc.perform(post("/api/auth/logout").session(after.session())
            .header("X-CSRF-TOKEN", after.token())).andExpect(status().isNoContent())
            .andExpect(content().string(""))
            .andExpect(cookie().maxAge("JSESSIONID", 0));
        assertTrue(after.session().isInvalid());
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/libros")).andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousMeAndExpiredSessionAreUnauthorized() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        var csrf = csrf(null);
        login(csrf, "alumno@example.com", "ClavePrueba123!", 200);
        csrf.session().invalidate();
        mvc.perform(get("/api/auth/me").cookie(new jakarta.servlet.http.Cookie("JSESSIONID", "caducada")))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void healthRemainsPublicAndLoggedOutLogoutReturns401WithCsrf() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk());
        var csrf = csrf(null);
        mvc.perform(post("/api/auth/logout").session(csrf.session())
            .header("X-CSRF-TOKEN", csrf.token())).andExpect(status().isUnauthorized());
    }

    @Test
    void realHttpCookieRoundTripRotatesSessionAndRejectsOldCookiesAfterLogout() throws Exception {
        var cookies = new java.net.CookieManager(null, java.net.CookiePolicy.ACCEPT_ALL);
        try (var client = java.net.http.HttpClient.newBuilder().cookieHandler(cookies).build()) {
            String base = "http://localhost:" + port;
            var csrf = client.send(java.net.http.HttpRequest.newBuilder(
                java.net.URI.create(base + "/api/auth/csrf")).GET().build(),
                java.net.http.HttpResponse.BodyHandlers.ofString());
            assertEquals(200, csrf.statusCode());
            String setCookie = csrf.headers().firstValue("set-cookie").orElseThrow();
            assertTrue(setCookie.contains("HttpOnly"));
            assertTrue(setCookie.contains("SameSite=Lax"));
            String originalCookie = setCookie.split(";", 2)[0];
            String token = json.readTree(csrf.body()).get("token").asString();
            var login = client.send(java.net.http.HttpRequest.newBuilder(
                java.net.URI.create(base + "/api/auth/login"))
                .header("Content-Type", "application/json").header("X-CSRF-TOKEN", token)
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(
                    "{\"email\":\"alumno@example.com\",\"password\":\"ClavePrueba123!\"}"))
                .build(), java.net.http.HttpResponse.BodyHandlers.ofString());
            assertEquals(200, login.statusCode());
            String loggedInCookie = login.headers().firstValue("set-cookie").orElseThrow().split(";", 2)[0];
            assertNotEquals(originalCookie, loggedInCookie);
            var me = client.send(java.net.http.HttpRequest.newBuilder(
                java.net.URI.create(base + "/api/auth/me")).GET().build(),
                java.net.http.HttpResponse.BodyHandlers.ofString());
            assertEquals(200, me.statusCode());
            var nextCsrf = client.send(java.net.http.HttpRequest.newBuilder(
                java.net.URI.create(base + "/api/auth/csrf")).GET().build(),
                java.net.http.HttpResponse.BodyHandlers.ofString());
            String nextToken = json.readTree(nextCsrf.body()).get("token").asString();
            var logout = client.send(java.net.http.HttpRequest.newBuilder(
                java.net.URI.create(base + "/api/auth/logout")).header("X-CSRF-TOKEN", nextToken)
                .POST(java.net.http.HttpRequest.BodyPublishers.noBody()).build(),
                java.net.http.HttpResponse.BodyHandlers.ofString());
            assertEquals(204, logout.statusCode());
            assertTrue(logout.headers().allValues("set-cookie").stream()
                .flatMap(value -> java.net.HttpCookie.parse(value).stream())
                .anyMatch(cookie -> cookie.getName().equals("JSESSIONID") && cookie.hasExpired()),
                "El logout debe emitir una cookie de sesión expirada");
            var after = client.send(java.net.http.HttpRequest.newBuilder(
                java.net.URI.create(base + "/api/auth/me")).GET().build(),
                java.net.http.HttpResponse.BodyHandlers.ofString());
            assertEquals(401, after.statusCode());
            try (var attacker = java.net.http.HttpClient.newHttpClient()) {
                for (String stolen : new String[]{originalCookie, loggedInCookie}) {
                    var replay = attacker.send(java.net.http.HttpRequest.newBuilder(
                        java.net.URI.create(base + "/api/auth/me")).header("Cookie", stolen).GET().build(),
                        java.net.http.HttpResponse.BodyHandlers.ofString());
                    assertEquals(401, replay.statusCode());
                }
            }
        }
    }
}

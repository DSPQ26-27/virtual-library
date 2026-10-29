package com.biblioteca.backend.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = SecurityConfigTests.TestConfig.class)
class SecurityConfigTests {
    @TestConfiguration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, TestEndpoints.class})
    static class TestConfig {}

    // Controladores de prueba: no implementan las tareas de los compañeros.
    @RestController
    @TestComponent
    static class TestEndpoints {
        @RequestMapping({"/api/health", "/api/auth/csrf", "/api/auth/register",
            "/api/auth/login", "/api/auth/logout", "/api/auth/me",
            "/api/libros", "/api/admin/users"})
        String endpoint() { return "ok"; }
    }

    @Autowired
    WebApplicationContext context;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void publicEndpointsAndRegistrationDoNotRequireSession() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk());
        mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk());
        mvc.perform(post("/api/auth/register")).andExpect(status().isOk());
    }

    @Test
    void anonymousProtectedRequestsReturnJsonWithoutRedirect() throws Exception {
        for (String path : new String[]{"/api/libros", "/api/auth/me", "/api/admin/users"}) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(header().doesNotExist("Location"));
        }
    }

    @Test
    void loginAndLogoutRequireValidCsrf() throws Exception {
        for (String path : new String[]{"/api/auth/login", "/api/auth/logout"}) {
            mvc.perform(post(path)).andExpect(status().isForbidden());
            mvc.perform(post(path).with(csrf().useInvalidToken()))
                .andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/auth/login").with(csrf())).andExpect(status().isOk());
        mvc.perform(post("/api/auth/logout").with(user("student").roles("STUDENT"))
            .with(csrf())).andExpect(status().isOk());
    }

    @Test
    void catalogAllowsKnownRolesButRejectsUnknownRoles() throws Exception {
        for (String role : new String[]{"STUDENT", "LIBRARIAN", "ADMIN"}) {
            mvc.perform(get("/api/libros").with(user("reader").roles(role)))
                .andExpect(status().isOk());
        }
        mvc.perform(get("/api/libros").with(user("other").roles("OTHER")))
            .andExpect(status().isForbidden());
    }

    @Test
    void catalogWritesRequireStaffAndCsrf() throws Exception {
        mvc.perform(post("/api/libros").with(user("student").roles("STUDENT")).with(csrf()))
            .andExpect(status().isForbidden());
        for (String role : new String[]{"LIBRARIAN", "ADMIN"}) {
            mvc.perform(post("/api/libros").with(user("staff").roles(role)).with(csrf()))
                .andExpect(status().isOk());
            mvc.perform(post("/api/libros").with(user("staff").roles(role)))
                .andExpect(status().isForbidden());
        }
    }

    @Test
    void onlyAdminCanAccessAdministration() throws Exception {
        for (String role : new String[]{"STUDENT", "LIBRARIAN"}) {
            mvc.perform(get("/api/admin/users").with(user("reader").roles(role)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/json"));
        }
        mvc.perform(get("/api/admin/users").with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk());
    }
}

package com.biblioteca.performance;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/** Comprobación funcional de infraestructura: un usuario, sin perfil de carga. */
public class SesionInicialSimulation extends Simulation {
    private final String baseUrl = EntornoSeguro.destino();
    private final String email = EntornoSeguro.credencial("BIBLIOTECA_PERF_EMAIL");
    private final String password = EntornoSeguro.credencial("BIBLIOTECA_PERF_PASSWORD");

    private final HttpProtocolBuilder protocolo = http.baseUrl(baseUrl)
        .acceptHeader("application/json")
        .disableFollowRedirect();

    private final ScenarioBuilder sesion = scenario("Sesión y catálogo")
        // Obtener el token inicial y autenticar al estudiante.
        .exec(http("CSRF antes de login").get("/api/auth/csrf")
            .check(status().is(200), jsonPath("$.headerName").is("X-CSRF-TOKEN"),
                jsonPath("$.token").ofString().saveAs("csrf")))
        .exitHereIfFailed()

        .exec(http("Login").post("/api/auth/login")
            .header("X-CSRF-TOKEN", "#{csrf}")
            .header("Content-Type", "application/json")
            .body(StringBody(session -> credencialesJson()))
            .check(status().is(200), jsonPath("$.email").is(email),
                jsonPath("$.id").exists(), jsonPath("$.roles[0]").is("STUDENT")))
        .exitHereIfFailed()

        // Obtener el nuevo token y comprobar la sesión autenticada.
        .exec(http("CSRF después de login").get("/api/auth/csrf")
            .check(status().is(200), jsonPath("$.headerName").is("X-CSRF-TOKEN"),
                jsonPath("$.token").ofString().saveAs("csrf")))
        .exitHereIfFailed()


        .exec(http("Sesión actual").get("/api/auth/me")
            .check(status().is(200), jsonPath("$.email").is(email),
                jsonPath("$.id").exists(), jsonPath("$.roles[0]").is("STUDENT")))
        .exitHereIfFailed()
        .exec(http("Catálogo").get("/api/libros?page=0&size=20")
            .check(status().is(200), jsonPath("$.page").is("0"),
                jsonPath("$.totalElements").exists(), jsonPath("$.content").exists()))
        .exitHereIfFailed()
        
        .exec(http("Logout").post("/api/auth/logout")
            .header("X-CSRF-TOKEN", "#{csrf}")
            .check(status().is(204)));

    public SesionInicialSimulation() {
        setUp(sesion.injectOpen(atOnceUsers(1)))
            .protocols(protocolo)
            .assertions(global().failedRequests().count().is(0L));
    }

    private String credencialesJson() {
        return "{\"email\":\"" + escapar(email) + "\",\"password\":\"" + escapar(password) + "\"}";
    }

    private static String escapar(String valor) {
        StringBuilder resultado = new StringBuilder();
        for (char c : valor.toCharArray()) {
            switch (c) {
                case '"' -> resultado.append("\\\"");
                case '\\' -> resultado.append("\\\\");
                default -> {
                    if (c < 0x20) {
                        resultado.append(String.format("\\u%04x", (int) c));
                    } else {
                        resultado.append(c);
                    }
                }
            }
        }
        return resultado.toString();
    }
}

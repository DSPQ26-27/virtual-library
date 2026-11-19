package com.biblioteca.performance;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/** Lecturas con concurrencia acotada; el calentamiento tiene nombre propio. */
public class CatalogoSimulation extends Simulation {
    private final String url = EntornoSeguro.destino();
    private final String email = EntornoSeguro.credencial("BIBLIOTECA_PERF_EMAIL");
    private final String password = EntornoSeguro.credencial("BIBLIOTECA_PERF_PASSWORD");
    private final int usuarios = enteroPermitido("BIBLIOTECA_PERF_USUARIOS", 1, 5, 10);
    private final int libros = enteroPermitido("BIBLIOTECA_PERF_LIBROS", 1000, 5000, 10000);

    private final ScenarioBuilder catalogo = scenario("Lectura catalogo")

        // Autenticación de cada usuario virtual.
        .exec(http("Inicio CSRF").get("/api/auth/csrf")
            .check(status().is(200), jsonPath("$.token").saveAs("csrf")))
        .exitHereIfFailed()


        .exec(http("Inicio sesion").post("/api/auth/login").header("X-CSRF-TOKEN", "#{csrf}")
            .header("Content-Type", "application/json")
            .body(StringBody("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
            .check(status().is(200), jsonPath("$.email").is(email)))
        .exitHereIfFailed()

        
        // Calentamiento separado de las consultas medidas.
        .during(5).on(
            exec(http("Calentamiento").get("/api/libros?page=0&size=20")
                .check(status().is(200), jsonPath("$.totalElements").is(Integer.toString(libros))))
            .pause(1))
        .exitHereIfFailed()


        // Consultas reales con pausas entre operaciones.
        .during(12).on(
            exec(http("Catalogo inicial").get("/api/libros?page=0&size=20")
                .check(status().is(200), jsonPath("$.totalElements").is(Integer.toString(libros))))
            .pause(1)

            .exec(http("Busqueda titulo").get("/api/libros?q=Atlas&page=0&size=20")
                .check(status().is(200), jsonPath("$.content[0].titulo").exists()))
            .pause(1)

            .exec(http("Busqueda autor").get("/api/libros?q=Alvarez&page=0&size=20")
                .check(status().is(200), jsonPath("$.content[0].autor").exists()))
            .pause(1)

            .exec(http("Categoria").get("/api/libros?categoria=Ciencia&page=0&size=20")
                .check(status().is(200), jsonPath("$.content[0].id").exists()))
            .pause(1)

            .exec(http("Disponibles").get("/api/libros?vista=DISPONIBLES&page=0&size=20")
                .check(status().is(200), jsonPath("$.content[0].disponible").is("true")))
            .pause(1)

            .exec(http("Pagina intermedia").get("/api/libros?page=" + (libros / 40) + "&size=20")
                .check(status().is(200), jsonPath("$.content[0].id").exists()))
            .pause(1)

            .exec(http("Pagina profunda").get("/api/libros?page=" + (libros / 20 - 1) + "&size=20")
                .check(status().is(200), jsonPath("$.content[0].id").exists()))
            .pause(1)
            
            .exec(http("Categorias").get("/api/libros/categorias")
                .check(status().is(200), jsonPath("$[0]").exists()))
            .pause(1));

    public CatalogoSimulation() {
        setUp(catalogo.injectClosed(
            rampConcurrentUsers(0).to(usuarios).during(2),
            constantConcurrentUsers(usuarios).during(18)
        ))
            .protocols(http.baseUrl(url).acceptHeader("application/json").disableFollowRedirect())
            .maxDuration(22)
            .assertions(global().failedRequests().count().is(0L));
    }

    private static int enteroPermitido(String nombre, int... permitidos) {
        String texto = EntornoSeguro.credencial(nombre);
        for (int numero : permitidos) {
            if (Integer.toString(numero).equals(texto)) {
                return numero;
            }
        }
        throw new IllegalStateException("Valor no permitido para " + nombre);
    }
}

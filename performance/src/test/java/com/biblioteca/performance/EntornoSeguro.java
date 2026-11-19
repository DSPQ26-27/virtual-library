package com.biblioteca.performance;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Validación de destino: no sustituye la verificación independiente de la base. */
final class EntornoSeguro {
    private EntornoSeguro() {
    }

    static String destino() {
        String valor = System.getenv("BIBLIOTECA_PERF_URL");
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException("Indique BIBLIOTECA_PERF_URL para el entorno de rendimiento aislado.");
        }
        URI uri;
        try {
            uri = URI.create(valor);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("BIBLIOTECA_PERF_URL no es una URI válida.", ex);
        }
        if (!"http".equals(uri.getScheme())
                || uri.getHost() == null
                || !Set.of("localhost", "127.0.0.1").contains(uri.getHost())
                || uri.getPort() < 1
                || uri.getPort() == 8080
                || uri.getUserInfo() != null
                || uri.getQuery() != null
                || uri.getFragment() != null
                || !(uri.getPath().isEmpty() || "/".equals(uri.getPath()))) {
            throw new IllegalStateException(
                "Destino no permitido: use un puerto local explícito distinto de 8080, sin ruta ni credenciales."
            );
        }
        String proyecto = credencial("BIBLIOTECA_PERF_PROJECT");
        if (!proyecto.matches("biblioteca-perf-[a-f0-9]{32}")) {
            throw new IllegalStateException("El proyecto debe ser exclusivo y contener un identificador aleatorio.");
        }
        Path verificador = Path.of("verificar-entorno.ps1").toAbsolutePath().normalize();
        try {
            Process proceso = new ProcessBuilder(
                "powershell", "-NoProfile", "-NonInteractive", "-File", verificador.toString()
            ).inheritIO().start();

            if (!proceso.waitFor(90, TimeUnit.SECONDS)) {
                proceso.destroyForcibly();
                throw new IllegalStateException("La verificación del entorno agotó el tiempo máximo.");
            }
            if (proceso.exitValue() != 0) {
                throw new IllegalStateException("Verificación del entorno fallida; Gatling no enviará peticiones.");
            }
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo ejecutar la verificación obligatoria del entorno.", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Verificación interrumpida.", ex);
        }
        return valor;
    }

    static String credencial(String nombre) {
        String valor = System.getenv(nombre);
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException("Falta la credencial de prueba " + nombre + ".");
        }
        return valor;
    }
}

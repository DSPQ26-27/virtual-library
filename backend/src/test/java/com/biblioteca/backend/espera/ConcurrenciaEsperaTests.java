package com.biblioteca.backend.espera;

import com.biblioteca.backend.catalogo.*;
import com.biblioteca.backend.prestamo.*;
import com.biblioteca.backend.usuario.*;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class ConcurrenciaEsperaTests {
    @Autowired PrestamoService prestamosService;
    @Autowired SolicitudEsperaService espera;
    @Autowired UsuarioRepository usuarios;
    @Autowired LibroRepository libros;
    @Autowired EjemplarRepository ejemplares;
    @Autowired PrestamoRepository prestamos;
    @Autowired SolicitudEsperaRepository solicitudes;
    @Autowired PlatformTransactionManager manager;
    @Autowired JdbcTemplate jdbc;

    record Datos(Long propietario, Long primero, Long segundo, Long libro,
                 Long prestamoA, Long prestamoB, Long solicitudA, Long solicitudB) {}

    private Usuario usuario() {
        Usuario usuario = new Usuario();
        usuario.setNombre("Prueba concurrente");
        usuario.setEmail(UUID.randomUUID() + "@example.com");
        usuario.setPasswordHash("hash");
        return usuarios.saveAndFlush(usuario);
    }

    private Long prestamo(Libro libro, Usuario propietario) {
        Ejemplar ejemplar = new Ejemplar(libro, UUID.randomUUID().toString());
        ejemplar.prestar();
        ejemplares.saveAndFlush(ejemplar);
        return prestamos.saveAndFlush(new Prestamo(ejemplar, propietario)).getId();
    }

    private Datos crear() {
        return new TransactionTemplate(manager).execute(status -> {
            Usuario propietario = usuario();
            Usuario primero = usuario();
            Usuario segundo = usuario();
            Libro libro = libros.saveAndFlush(new Libro(null, "Concurrencia", "Autor", null, "Pruebas", 2020));
            Long prestamoA = prestamo(libro, propietario);
            Long prestamoB = prestamo(libro, propietario);
            Long solicitudA = espera.apuntarse(primero.getId(), libro.getId()).id();
            Long solicitudB = espera.apuntarse(segundo.getId(), libro.getId()).id();
            return new Datos(propietario.getId(), primero.getId(), segundo.getId(), libro.getId(),
                    prestamoA, prestamoB, solicitudA, solicitudB);
        });
    }

    // Cada tarea llama a un servicio transaccional desde un hilo diferente.
    private void simultaneamente(Runnable primera, Runnable segunda) throws Exception {
        ExecutorService ejecutor = Executors.newFixedThreadPool(2);
        CountDownLatch preparados = new CountDownLatch(2);
        CountDownLatch inicio = new CountDownLatch(1);
        try {
            Callable<Void> a = () -> {
                preparados.countDown();
                inicio.await();
                primera.run();
                return null;
            };
            Callable<Void> b = () -> {
                preparados.countDown();
                inicio.await();
                segunda.run();
                return null;
            };
            Future<Void> resultadoA = ejecutor.submit(a);
            Future<Void> resultadoB = ejecutor.submit(b);
            assertTrue(preparados.await(10, TimeUnit.SECONDS));
            inicio.countDown();
            resultadoA.get(20, TimeUnit.SECONDS);
            resultadoB.get(20, TimeUnit.SECONDS);
        } finally {
            inicio.countDown();
            ejecutor.shutdownNow();
            ejecutor.awaitTermination(20, TimeUnit.SECONDS);
        }
    }

    private void limpiar(Datos datos) {
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            jdbc.update("DELETE FROM solicitudes_espera WHERE libro_id = ?", datos.libro());
            jdbc.update("DELETE FROM prestamos WHERE ejemplar_id IN (SELECT id FROM ejemplares WHERE libro_id = ?)", datos.libro());
            jdbc.update("DELETE FROM ejemplares WHERE libro_id = ?", datos.libro());
            jdbc.update("DELETE FROM libros WHERE id = ?", datos.libro());
            jdbc.update("DELETE FROM usuarios WHERE id IN (?, ?, ?)", datos.propietario(), datos.primero(), datos.segundo());
        });
    }

    private void comprobarEjemplaresSinDuplicados(Datos datos) {
        Long duplicados = jdbc.queryForObject("SELECT COUNT(*) FROM (SELECT p.ejemplar_id FROM prestamos p "
                + "JOIN ejemplares e ON e.id = p.ejemplar_id WHERE e.libro_id = ? AND p.estado = 'ACTIVO' "
                + "GROUP BY p.ejemplar_id HAVING COUNT(*) > 1) duplicados", Long.class, datos.libro());
        assertEquals(0L, duplicados);
    }

    @Test
    void dosEjemplaresDevueltosSeAsignanAEstudiantesDistintos() throws Exception {
        Datos datos = crear();
        try {
            simultaneamente(
                    () -> prestamosService.devolver(datos.propietario(), datos.prestamoA()),
                    () -> prestamosService.devolver(datos.propietario(), datos.prestamoB()));
            assertEquals(1, prestamos.countByUsuarioIdAndEstado(datos.primero(), EstadoPrestamo.ACTIVO));
            assertEquals(1, prestamos.countByUsuarioIdAndEstado(datos.segundo(), EstadoPrestamo.ACTIVO));
            assertEquals(EstadoSolicitudEspera.ASIGNADA, solicitudes.findById(datos.solicitudA()).orElseThrow().getEstado());
            assertEquals(EstadoSolicitudEspera.ASIGNADA, solicitudes.findById(datos.solicitudB()).orElseThrow().getEstado());
            comprobarEjemplaresSinDuplicados(datos);
        } finally { limpiar(datos); }
    }

    private void devolverSiSigueActivo(Datos datos) {
        try {
            prestamosService.devolver(datos.propietario(), datos.prestamoA());
        } catch (PrestamoYaDevueltoException esperado) {
            // La otra petición ya confirmó esta misma devolución.
        }
    }

    @Test
    void dosPeticionesDeLaMismaDevolucionSoloAsignanUnaVez() throws Exception {
        Datos datos = crear();
        try {
            simultaneamente(() -> devolverSiSigueActivo(datos), () -> devolverSiSigueActivo(datos));
            assertEquals(1, prestamos.countByUsuarioIdAndEstado(datos.primero(), EstadoPrestamo.ACTIVO));
            assertEquals(0, prestamos.countByUsuarioIdAndEstado(datos.segundo(), EstadoPrestamo.ACTIVO));
            assertEquals(EstadoSolicitudEspera.PENDIENTE, solicitudes.findById(datos.solicitudB()).orElseThrow().getEstado());
            comprobarEjemplaresSinDuplicados(datos);
        } finally { limpiar(datos); }
    }

    @Test
    void cancelacionYDevolucionMantienenUnResultadoConsistente() throws Exception {
        Datos datos = crear();
        try {
            simultaneamente(
                    () -> {
                        try { espera.cancelar(datos.primero(), datos.solicitudA()); }
                        catch (SolicitudEsperaRechazadaException ex) {
                            assertEquals("SOLICITUD_ASIGNADA", ex.getCodigo());
                        }
                    },
                    () -> prestamosService.devolver(datos.propietario(), datos.prestamoA()));
            EstadoSolicitudEspera estado = solicitudes.findById(datos.solicitudA()).orElseThrow().getEstado();
            assertTrue(estado == EstadoSolicitudEspera.ASIGNADA || estado == EstadoSolicitudEspera.CANCELADA);
            assertEquals(estado == EstadoSolicitudEspera.ASIGNADA ? 1 : 0,
                    prestamos.countByUsuarioIdAndEstado(datos.primero(), EstadoPrestamo.ACTIVO));
            assertEquals(estado == EstadoSolicitudEspera.CANCELADA ? 1 : 0,
                    prestamos.countByUsuarioIdAndEstado(datos.segundo(), EstadoPrestamo.ACTIVO));
            comprobarEjemplaresSinDuplicados(datos);
        } finally { limpiar(datos); }
    }
}

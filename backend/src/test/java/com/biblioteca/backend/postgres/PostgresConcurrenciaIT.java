package com.biblioteca.backend.postgres;

import com.biblioteca.backend.prestamo.PrestamoService;
import com.biblioteca.backend.prestamo.PrestamoRepository;
import com.biblioteca.backend.prestamo.PrestamoYaDevueltoException;
import com.biblioteca.backend.prestamo.SinEjemplaresDisponiblesException;
import com.biblioteca.backend.prestamo.RenovacionRechazadaException;
import com.biblioteca.backend.espera.SolicitudEsperaRechazadaException;
import com.biblioteca.backend.espera.SolicitudEsperaService;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.junit.jupiter.api.Assertions.*;

@PostgresIntegrationTest
class PostgresConcurrenciaIT {
    @Autowired PostgreSQLContainer postgres;
    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @Autowired PrestamoService servicio;
    @Autowired PrestamoRepository prestamos;
    @Autowired SolicitudEsperaService espera;

    private record Datos(long primero, long segundo, long libro, long ejemplar) {}
    private record Resultado(int conexion, String valor) {}
    private Datos datos;
    private final List<Long> usuariosAdicionales = new ArrayList<>();

    @BeforeEach
    void crearDatosConfirmados() throws Exception {
        comprobarConexion();
        datos = new TransactionTemplate(manager).execute(status -> {
            String clave = UUID.randomUUID().toString();
            long primero = crearUsuario(clave + "a@example.com");
            long segundo = crearUsuario(clave + "b@example.com");
            long libro = jdbc.queryForObject("INSERT INTO libros(titulo, autor) VALUES (?, ?) RETURNING id",
                Long.class, "Concurrencia", "Autor");
            long ejemplar = jdbc.queryForObject("INSERT INTO ejemplares(libro_id, codigo) VALUES (?, ?) RETURNING id",
                Long.class, libro, clave);
            return new Datos(primero, segundo, libro, ejemplar);
        });
    }

    private long crearUsuario(String email) {
        return jdbc.queryForObject("""
            INSERT INTO usuarios(nombre, email, password_hash, rol, activo)
            VALUES ('Prueba', ?, 'hash', 'ESTUDIANTE', true) RETURNING id
            """, Long.class, email);
    }

    @AfterEach
    void limpiar() throws Exception {
        if (datos == null) return;
        comprobarConexion();
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            jdbc.update("DELETE FROM solicitudes_espera WHERE libro_id = ?", datos.libro());
            jdbc.update("DELETE FROM prestamos WHERE ejemplar_id IN (SELECT id FROM ejemplares WHERE libro_id = ?)", datos.libro());
            jdbc.update("DELETE FROM ejemplares WHERE libro_id = ?", datos.libro());
            jdbc.update("DELETE FROM libros WHERE id = ?", datos.libro());
            jdbc.update("DELETE FROM usuarios WHERE id IN (?, ?)", datos.primero(), datos.segundo());
            usuariosAdicionales.forEach(id -> jdbc.update("DELETE FROM usuarios WHERE id = ?", id));
        });
    }

    private void comprobarConexion() throws Exception {
        assertTrue(postgres.isRunning());
        try (var conexion = dataSource.getConnection()) {
            assertEquals(postgres.getJdbcUrl(), conexion.getMetaData().getURL());
            assertEquals(postgres.getDatabaseName(), conexion.getCatalog());
            assertEquals(postgres.getUsername(), conexion.getMetaData().getUserName());
        }
    }

    @Test
    void dosEstudiantesCompitenPorElUltimoEjemplar() throws Exception {
        List<Resultado> resultados = simultaneamente(
            () -> prestar(datos.primero()), () -> prestar(datos.segundo()));
        assertEquals(List.of("PRESTADO", "SIN_EJEMPLARES"), resultados.stream().map(Resultado::valor).sorted().toList());
        assertEquals(1, contar("ACTIVO"));
        assertEquals("PRESTADO", estadoEjemplar());
        assertEquals(1, totalPrestamos());
    }

    @Test
    void dobleDevolucionCierraUnaSolaVez() throws Exception {
        long prestamo = servicio.prestar(datos.primero(), datos.libro()).id();
        long solicitud = jdbc.queryForObject("""
            INSERT INTO solicitudes_espera(libro_id, usuario_id) VALUES (?, ?) RETURNING id
            """, Long.class, datos.libro(), datos.segundo());
        List<Resultado> resultados = simultaneamente(
            () -> devolver(datos.primero(), prestamo), () -> devolver(datos.primero(), prestamo));
        assertEquals(List.of("DEVUELTO", "YA_DEVUELTO"), resultados.stream().map(Resultado::valor).sorted().toList());
        assertEquals(1, contar("ACTIVO"));
        assertEquals(1, contar("DEVUELTO"));
        assertEquals(2, totalPrestamos());
        assertEquals("PRESTADO", estadoEjemplar());
        assertEquals("ASIGNADA", estadoSolicitud(solicitud));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM prestamos WHERE ejemplar_id = ? AND usuario_id = ? AND estado = 'ACTIVO'", Integer.class, datos.ejemplar(), datos.segundo()));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM prestamos WHERE id = ? AND fecha_devolucion IS NOT NULL", Integer.class, prestamo));
    }

    @Test
    void devolverYPrestarMantieneEstadosCoherentes() throws Exception {
        long prestamo = servicio.prestar(datos.primero(), datos.libro()).id();
        List<Resultado> resultados = simultaneamente(
            () -> devolver(datos.primero(), prestamo), () -> prestar(datos.segundo()));
        assertEquals("DEVUELTO", resultados.get(0).valor());
        String peticion = resultados.get(1).valor();
        assertTrue(List.of("PRESTADO", "SIN_EJEMPLARES").contains(peticion));
        assertEquals(1, contar("DEVUELTO"));
        assertEquals(peticion.equals("PRESTADO") ? 1 : 0, contar("ACTIVO"));
        assertEquals(peticion.equals("PRESTADO") ? "PRESTADO" : "DISPONIBLE", estadoEjemplar());
        assertEquals(peticion.equals("PRESTADO") ? 2 : 1, totalPrestamos());
    }

    @Test
    void prestarEsperaElBloqueoPessimistaDeCoordinacion() throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            new TransactionTemplate(manager).executeWithoutResult(status -> {
                int pid = jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class);
                jdbc.queryForObject("SELECT id FROM coordinacion_prestamos WHERE id = 1 FOR UPDATE", Integer.class);
                Future<Integer> intento = pool.submit(() -> new TransactionTemplate(manager).execute(otro -> {
                    int otroPid = jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class);
                    jdbc.execute("SET LOCAL lock_timeout = '1s'");
                    try {
                        servicio.prestar(datos.segundo(), datos.libro());
                        fail("La operación no debe adquirir la fila bloqueada");
                    } catch (RuntimeException error) {
                        Throwable causa = error;
                        while (causa != null && !(causa instanceof SQLException)) causa = causa.getCause();
                        assertInstanceOf(SQLException.class, causa);
                        assertEquals("55P03", ((SQLException) causa).getSQLState());
                        otro.setRollbackOnly();
                    }
                    return otroPid;
                }));
                try {
                    assertNotEquals(pid, intento.get(10, TimeUnit.SECONDS));
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(ex);
                } catch (ExecutionException | TimeoutException ex) {
                    throw new IllegalStateException(ex);
                }
            });
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(20, TimeUnit.SECONDS));
        }
        assertEquals(0, totalPrestamos());
        assertEquals("DISPONIBLE", estadoEjemplar());
    }

    @Test
    void falloTrasFlushYAsignacionRevierteLaDevolucionCompleta() {
        long prestamo = servicio.prestar(datos.primero(), datos.libro()).id();
        long solicitud = jdbc.queryForObject("""
            INSERT INTO solicitudes_espera(libro_id, usuario_id) VALUES (?, ?) RETURNING id
            """, Long.class, datos.libro(), datos.segundo());

        assertThrows(FalloForzado.class, () -> new TransactionTemplate(manager).executeWithoutResult(status -> {
            servicio.devolver(datos.primero(), prestamo);
            // El servicio ya ejecutó flush y creó el préstamo de la solicitud.
            prestamos.flush();
            assertEquals(1, contar("DEVUELTO"));
            assertEquals(1, contar("ACTIVO"));
            assertEquals("ASIGNADA", estadoSolicitud(solicitud));
            throw new FalloForzado();
        }));

        assertEquals(1, contar("ACTIVO"));
        assertEquals(0, contar("DEVUELTO"));
        assertEquals(1, totalPrestamos());
        assertEquals("PRESTADO", estadoEjemplar());
        assertEquals("PENDIENTE", estadoSolicitud(solicitud));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM prestamos WHERE id = ? AND fecha_devolucion IS NULL AND estado = 'ACTIVO'", Integer.class, prestamo));
    }

    @Test
    void errorDeBaseDeDatosAlAsignarRevierteElFlushDeLaDevolucion() {
        long prestamo = servicio.prestar(datos.primero(), datos.libro()).id();
        long solicitud = jdbc.queryForObject("""
            INSERT INTO solicitudes_espera(libro_id, usuario_id) VALUES (?, ?) RETURNING id
            """, Long.class, datos.libro(), datos.segundo());

        RuntimeException error = assertThrows(RuntimeException.class,
            () -> new TransactionTemplate(manager).executeWithoutResult(status -> {
                // El trigger existe solo durante esta transacción de prueba, que acaba en rollback.
                jdbc.execute("""
                    CREATE FUNCTION pg_temp.fallar_asignacion() RETURNS trigger LANGUAGE plpgsql AS $$
                    BEGIN RAISE EXCEPTION 'fallo asignacion de prueba'; END $$
                    """);
                jdbc.execute("""
                    CREATE TRIGGER fallo_asignacion_test BEFORE UPDATE ON solicitudes_espera
                    FOR EACH ROW WHEN (NEW.estado = 'ASIGNADA')
                    EXECUTE FUNCTION pg_temp.fallar_asignacion()
                    """);
                servicio.devolver(datos.primero(), prestamo);
                prestamos.flush();
            }));
        assertTrue(error.toString().contains("fallo asignacion de prueba")
            || (error.getCause() != null && error.getCause().toString().contains("fallo asignacion de prueba")));
        assertEquals(1, contar("ACTIVO"));
        assertEquals(0, contar("DEVUELTO"));
        assertEquals(1, totalPrestamos());
        assertEquals("PRESTADO", estadoEjemplar());
        assertEquals("PENDIENTE", estadoSolicitud(solicitud));
    }

    private static class FalloForzado extends RuntimeException {}

    @Test
    void dosRenovacionesSimultaneasRespetanLimiteYVencimiento() throws Exception {
        long prestamo = servicio.prestar(datos.primero(), datos.libro()).id();
        LocalDateTime vencimiento = vencimiento(prestamo);
        var resultados = simultaneamente(() -> renovar(prestamo), () -> renovar(prestamo));
        assertEquals(List.of("RENOVADO", "RENOVADO"), resultados.stream().map(Resultado::valor).sorted().toList());
        assertEquals(2, renovaciones(prestamo));
        assertEquals(vencimiento.plusDays(60), vencimiento(prestamo));
        assertEquals("LIMITE_RENOVACIONES", assertThrows(RenovacionRechazadaException.class,
            () -> servicio.renovar(datos.primero(), prestamo)).getCodigo());
        assertEquals(2, renovaciones(prestamo));
        assertEquals(vencimiento.plusDays(60), vencimiento(prestamo));
    }

    @Test
    void dosRenovacionesConUnaPreviaSoloPermitenUnaMas() throws Exception {
        long prestamo = servicio.prestar(datos.primero(), datos.libro()).id();
        servicio.renovar(datos.primero(), prestamo);
        LocalDateTime anterior = vencimiento(prestamo);
        var resultados = simultaneamente(() -> renovar(prestamo), () -> renovar(prestamo));
        assertEquals(List.of("LIMITE_RENOVACIONES", "RENOVADO"), resultados.stream().map(Resultado::valor).sorted().toList());
        assertEquals(2, renovaciones(prestamo));
        assertEquals(anterior.plusDays(30), vencimiento(prestamo));
    }

    @Test
    void renovarYDevolverConcurrenSinPerderElEstado() throws Exception {
        long prestamo = servicio.prestar(datos.primero(), datos.libro()).id();
        LocalDateTime anterior = vencimiento(prestamo);
        var resultados = simultaneamente(() -> renovar(prestamo), () -> devolver(datos.primero(), prestamo));
        assertEquals("DEVUELTO", resultados.get(1).valor());
        assertTrue(List.of("RENOVADO", "PRESTAMO_NO_ACTIVO").contains(resultados.get(0).valor()));
        int renovaciones = resultados.get(0).valor().equals("RENOVADO") ? 1 : 0;
        assertEquals(renovaciones, renovaciones(prestamo));
        assertEquals(anterior.plusDays(30L * renovaciones), vencimiento(prestamo));
        assertEquals(1, contar("DEVUELTO"));
        assertEquals(0, contar("ACTIVO"));
        assertEquals("DISPONIBLE", estadoEjemplar());
    }

    @Test
    void renovarYApuntarseConcurrenConReglasDeEspera() throws Exception {
        long prestamo = servicio.prestar(datos.primero(), datos.libro()).id();
        LocalDateTime anterior = vencimiento(prestamo);
        var resultados = simultaneamente(() -> renovar(prestamo), () -> {
            espera.apuntarse(datos.segundo(), datos.libro()); return "APUNTADO";
        });
        assertEquals("APUNTADO", resultados.get(1).valor());
        assertTrue(List.of("RENOVADO", "LISTA_ESPERA_PENDIENTE").contains(resultados.get(0).valor()));
        int renovaciones = resultados.get(0).valor().equals("RENOVADO") ? 1 : 0;
        assertEquals(renovaciones, renovaciones(prestamo));
        assertEquals(anterior.plusDays(30L * renovaciones), vencimiento(prestamo));
        assertEquals(1, contar("ACTIVO"));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM solicitudes_espera WHERE libro_id = ? AND usuario_id = ? AND estado = 'PENDIENTE'", Integer.class, datos.libro(), datos.segundo()));
    }

    @Test
    void fifoSaltaBloqueadoYDesempataPorIdConFechasIguales() {
        long prestamo = servicio.prestar(datos.primero(), datos.libro()).id();
        long tercero = usuarioAdicional();
        long cuarto = usuarioAdicional();
        jdbc.update("UPDATE usuarios SET activo = false WHERE id = ?", datos.segundo());
        long bloqueado = solicitud(datos.segundo());
        long primeraElegible = solicitud(tercero);
        long segundaElegible = solicitud(cuarto);
        jdbc.update("UPDATE solicitudes_espera SET fecha_solicitud = ? WHERE libro_id = ?", LocalDateTime.of(2020, 1, 1, 0, 0), datos.libro());
        servicio.devolver(datos.primero(), prestamo);
        assertEquals("PENDIENTE", estadoSolicitud(bloqueado));
        assertEquals("ASIGNADA", estadoSolicitud(primeraElegible));
        assertEquals("PENDIENTE", estadoSolicitud(segundaElegible));
        assertEquals(1, activosDe(tercero));
        assertEquals(0, activosDe(cuarto));
        assertEquals("PRESTADO", estadoEjemplar());
    }

    @Test
    void dosDevolucionesAsignanEjemplaresDistintosSinDuplicar() throws Exception {
        long prestamoA = servicio.prestar(datos.primero(), datos.libro()).id();
        long ejemplarB = ejemplarAdicional();
        long prestamoB = servicio.prestar(datos.segundo(), datos.libro()).id();
        long tercero = usuarioAdicional();
        long cuarto = usuarioAdicional();
        long solicitudA = solicitud(tercero);
        long solicitudB = solicitud(cuarto);
        var resultados = simultaneamente(() -> devolver(datos.primero(), prestamoA), () -> devolver(datos.segundo(), prestamoB));
        assertEquals(List.of("DEVUELTO", "DEVUELTO"), resultados.stream().map(Resultado::valor).toList());
        assertEquals("ASIGNADA", estadoSolicitud(solicitudA));
        assertEquals("ASIGNADA", estadoSolicitud(solicitudB));
        assertEquals(1, activosDe(tercero));
        assertEquals(1, activosDe(cuarto));
        assertEquals(2, jdbc.queryForObject("SELECT count(DISTINCT ejemplar_id) FROM prestamos WHERE ejemplar_id IN (?, ?) AND estado = 'ACTIVO'", Integer.class, datos.ejemplar(), ejemplarB));
    }

    @Test
    void cancelarYDevolverMantienenSolicitudPrestamoYEjemplarCoherentes() throws Exception {
        long prestamo = servicio.prestar(datos.primero(), datos.libro()).id();
        long solicitud = solicitud(datos.segundo());
        var resultados = simultaneamente(() -> cancelar(datos.segundo(), solicitud), () -> devolver(datos.primero(), prestamo));
        assertEquals("DEVUELTO", resultados.get(1).valor());
        if (resultados.get(0).valor().equals("CANCELADA")) {
            assertEquals("CANCELADA", estadoSolicitud(solicitud));
            assertEquals(0, activosDe(datos.segundo()));
            assertEquals("DISPONIBLE", estadoEjemplar());
        } else {
            assertEquals("SOLICITUD_ASIGNADA", resultados.get(0).valor());
            assertEquals("ASIGNADA", estadoSolicitud(solicitud));
            assertEquals(1, activosDe(datos.segundo()));
            assertEquals("PRESTADO", estadoEjemplar());
        }
        assertEquals(1, contar("DEVUELTO"));
    }

    @Test
    void dosCancelacionesDeLaMismaSolicitudSonIdempotentes() throws Exception {
        servicio.prestar(datos.primero(), datos.libro());
        long solicitud = solicitud(datos.segundo());
        var resultados = simultaneamente(() -> cancelar(datos.segundo(), solicitud), () -> cancelar(datos.segundo(), solicitud));
        assertEquals(List.of("CANCELADA", "CANCELADA"), resultados.stream().map(Resultado::valor).toList());
        assertEquals("CANCELADA", estadoSolicitud(solicitud));
        assertEquals(0, activosDe(datos.segundo()));
        assertEquals(1, contar("ACTIVO"));
        assertEquals("PRESTADO", estadoEjemplar());
    }

    @Test
    void cancelarSolicitudQueAcabaDeAsignarseSeRechazaSinCambios() {
        long prestamo = servicio.prestar(datos.primero(), datos.libro()).id();
        long solicitud = solicitud(datos.segundo());
        servicio.devolver(datos.primero(), prestamo);
        assertEquals("SOLICITUD_ASIGNADA", assertThrows(SolicitudEsperaRechazadaException.class,
            () -> espera.cancelar(datos.segundo(), solicitud)).getCodigo());
        assertEquals("ASIGNADA", estadoSolicitud(solicitud));
        assertEquals(1, activosDe(datos.segundo()));
        assertEquals("PRESTADO", estadoEjemplar());
    }

    private String renovar(long id) { servicio.renovar(datos.primero(), id); return "RENOVADO"; }
    private String cancelar(long usuario, long id) { espera.cancelar(usuario, id); return "CANCELADA"; }
    private int renovaciones(long id) { return jdbc.queryForObject("SELECT numero_renovaciones FROM prestamos WHERE id = ?", Integer.class, id); }
    private LocalDateTime vencimiento(long id) { return jdbc.queryForObject("SELECT fecha_vencimiento FROM prestamos WHERE id = ?", LocalDateTime.class, id); }
    private int activosDe(long id) { return jdbc.queryForObject("SELECT count(*) FROM prestamos WHERE usuario_id = ? AND estado = 'ACTIVO'", Integer.class, id); }
    private long usuarioAdicional() {
        long id = crearUsuario(UUID.randomUUID() + "@example.com");
        usuariosAdicionales.add(id);
        return id;
    }
    private long ejemplarAdicional() { return jdbc.queryForObject("INSERT INTO ejemplares(libro_id, codigo) VALUES (?, ?) RETURNING id", Long.class, datos.libro(), UUID.randomUUID().toString()); }
    private long solicitud(long usuario) { return jdbc.queryForObject("INSERT INTO solicitudes_espera(libro_id, usuario_id) VALUES (?, ?) RETURNING id", Long.class, datos.libro(), usuario); }

    private String prestar(long usuario) {
        servicio.prestar(usuario, datos.libro());
        return "PRESTADO";
    }
    private String devolver(long usuario, long prestamo) {
        servicio.devolver(usuario, prestamo);
        return "DEVUELTO";
    }

    private List<Resultado> simultaneamente(Supplier<String> primera, Supplier<String> segunda) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch preparados = new CountDownLatch(2);
        CountDownLatch inicio = new CountDownLatch(1);
        try {
            Future<Resultado> a = pool.submit(() -> ejecutar(primera, preparados, inicio));
            Future<Resultado> b = pool.submit(() -> ejecutar(segunda, preparados, inicio));
            assertTrue(preparados.await(10, TimeUnit.SECONDS), "Los hilos no prepararon sus transacciones");
            inicio.countDown();
            Resultado primero = a.get(20, TimeUnit.SECONDS);
            Resultado segundo = b.get(20, TimeUnit.SECONDS);
            assertNotEquals(primero.conexion(), segundo.conexion(), "Cada hilo debe usar otra conexión PostgreSQL");
            return List.of(primero, segundo);
        } finally {
            inicio.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(20, TimeUnit.SECONDS), "Los hilos no terminaron");
        }
    }

    private Resultado ejecutar(Supplier<String> operacion, CountDownLatch preparados, CountDownLatch inicio) {
        int[] pid = new int[1];
        try {
            return new TransactionTemplate(manager).execute(status -> {
                pid[0] = jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class);
                preparados.countDown();
                try {
                    if (!inicio.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Sin señal de inicio");
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(ex);
                }
                return new Resultado(pid[0], operacion.get());
            });
        } catch (SinEjemplaresDisponiblesException ex) {
            return new Resultado(pid[0], "SIN_EJEMPLARES");
        } catch (PrestamoYaDevueltoException ex) {
            return new Resultado(pid[0], "YA_DEVUELTO");
        } catch (RenovacionRechazadaException ex) {
            return new Resultado(pid[0], ex.getCodigo());
        } catch (SolicitudEsperaRechazadaException ex) {
            return new Resultado(pid[0], ex.getCodigo());
        }
    }

    private int contar(String estado) {
        return jdbc.queryForObject("SELECT count(*) FROM prestamos WHERE ejemplar_id = ? AND estado = ?", Integer.class, datos.ejemplar(), estado);
    }
    private int totalPrestamos() {
        return jdbc.queryForObject("SELECT count(*) FROM prestamos WHERE ejemplar_id = ?", Integer.class, datos.ejemplar());
    }
    private String estadoEjemplar() {
        return jdbc.queryForObject("SELECT estado FROM ejemplares WHERE id = ?", String.class, datos.ejemplar());
    }
    private String estadoSolicitud(long id) {
        return jdbc.queryForObject("SELECT estado FROM solicitudes_espera WHERE id = ?", String.class, id);
    }
}

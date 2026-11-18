package com.biblioteca.backend.espera;

import com.biblioteca.backend.catalogo.*;
import com.biblioteca.backend.prestamo.*;
import com.biblioteca.backend.usuario.*;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AsignacionFifoTests {
    @Autowired PrestamoService servicio;
    @Autowired PrestamoRepository prestamos;
    @Autowired UsuarioRepository usuarios;
    @Autowired LibroRepository libros;
    @Autowired EjemplarRepository ejemplares;
    @Autowired SolicitudEsperaRepository solicitudes;
    @Autowired SolicitudEsperaService espera;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;

    private Usuario usuario() {
        Usuario usuario = new Usuario();
        usuario.setNombre("Estudiante");
        usuario.setEmail(UUID.randomUUID() + "@example.com");
        usuario.setPasswordHash("hash");
        return usuarios.saveAndFlush(usuario);
    }

    private Libro libro() {
        return libros.saveAndFlush(new Libro(null, "Prueba FIFO", "Autor", null, "Pruebas", 2020));
    }

    private Prestamo prestamo(Libro libro, Usuario usuario) {
        Ejemplar ejemplar = new Ejemplar(libro, UUID.randomUUID().toString());
        ejemplar.prestar();
        ejemplares.saveAndFlush(ejemplar);
        return prestamos.saveAndFlush(new Prestamo(ejemplar, usuario));
    }

    @Test
    void asignaPorOrdenYConservaHistorialConPrestamoDe30Dias() {
        Usuario propietario = usuario();
        Usuario primero = usuario();
        Usuario segundo = usuario();
        Libro libro = libro();
        Prestamo anterior = prestamo(libro, propietario);
        var a = espera.apuntarse(primero.getId(), libro.getId());
        var b = espera.apuntarse(segundo.getId(), libro.getId());
        servicio.devolver(propietario.getId(), anterior.getId());
        assertEquals(EstadoPrestamo.DEVUELTO, anterior.getEstado());
        assertEquals(EstadoSolicitudEspera.ASIGNADA, solicitudes.findById(a.id()).orElseThrow().getEstado());
        assertEquals(EstadoSolicitudEspera.PENDIENTE, solicitudes.findById(b.id()).orElseThrow().getEstado());
        var listado = prestamos.listarPropios(primero.getId()).get(0);
        Prestamo nuevo = prestamos.findById(listado.id()).orElseThrow();
        assertEquals(anterior.getEjemplar().getId(), nuevo.getEjemplar().getId());
        assertEquals(nuevo.getFechaPrestamo().plusDays(30), nuevo.getFechaVencimiento());
        assertEquals(EstadoEjemplar.PRESTADO, nuevo.getEjemplar().getEstado());
        servicio.devolver(primero.getId(), nuevo.getId());
        assertEquals(EstadoSolicitudEspera.ASIGNADA, solicitudes.findById(b.id()).orElseThrow().getEstado());
        assertEquals(1, prestamos.countByUsuarioIdAndEstado(segundo.getId(), EstadoPrestamo.ACTIVO));
    }

    @ParameterizedTest
    @ValueSource(strings = {"INACTIVO", "VENCIDO", "LIMITE", "ROL"})
    void saltaNoElegiblesConservandoSuSolicitud(String motivo) {
        Usuario propietario = usuario();
        Usuario primero = usuario();
        Usuario segundo = usuario();
        Libro libro = libro();
        Prestamo anterior = prestamo(libro, propietario);
        var a = espera.apuntarse(primero.getId(), libro.getId());
        var b = espera.apuntarse(segundo.getId(), libro.getId());
        if (motivo.equals("INACTIVO")) primero.setActivo(false);
        if (motivo.equals("ROL")) primero.setRol("BIBLIOTECARIO");
        if (motivo.equals("VENCIDO")) {
            Prestamo vencido = prestamo(libro(), primero);
            jdbc.update("UPDATE prestamos SET fecha_prestamo = ?, fecha_vencimiento = ? WHERE id = ?",
                    LocalDateTime.now().minusDays(40), LocalDateTime.now().minusDays(10), vencido.getId());
        }
        if (motivo.equals("LIMITE")) {
            for (int i = 0; i < 5; i++) prestamo(libro(), primero);
        }
        usuarios.flush();
        servicio.devolver(propietario.getId(), anterior.getId());
        assertEquals(EstadoSolicitudEspera.PENDIENTE, solicitudes.findById(a.id()).orElseThrow().getEstado());
        assertEquals(EstadoSolicitudEspera.ASIGNADA, solicitudes.findById(b.id()).orElseThrow().getEstado());
    }

    @Test
    void libroRetiradoNoGeneraAsignaciones() {
        Usuario propietario = usuario();
        Libro libro = libro();
        Prestamo anterior = prestamo(libro, propietario);
        var solicitud = espera.apuntarse(usuario().getId(), libro.getId());
        libro.retirar();
        libros.flush();
        servicio.devolver(propietario.getId(), anterior.getId());
        assertEquals(EstadoEjemplar.DISPONIBLE, anterior.getEjemplar().getEstado());
        assertEquals(EstadoSolicitudEspera.PENDIENTE, solicitudes.findById(solicitud.id()).orElseThrow().getEstado());
    }

    @Test
    void laFechaTienePrioridadSobreElIdentificador() {
        Usuario propietario = usuario();
        Usuario primeroInsertado = usuario();
        Usuario solicitudMasAntigua = usuario();
        Libro libro = libro();
        Prestamo anterior = prestamo(libro, propietario);
        var a = espera.apuntarse(primeroInsertado.getId(), libro.getId());
        var b = espera.apuntarse(solicitudMasAntigua.getId(), libro.getId());
        jdbc.update("UPDATE solicitudes_espera SET fecha_solicitud = ? WHERE id = ?",
                LocalDateTime.now().minusDays(1), a.id());
        jdbc.update("UPDATE solicitudes_espera SET fecha_solicitud = ? WHERE id = ?",
                LocalDateTime.now().minusDays(2), b.id());
        servicio.devolver(propietario.getId(), anterior.getId());
        assertEquals(EstadoSolicitudEspera.ASIGNADA, solicitudes.findById(b.id()).orElseThrow().getEstado());
        assertEquals(EstadoSolicitudEspera.PENDIENTE, solicitudes.findById(a.id()).orElseThrow().getEstado());
    }

    @Test
    void desempataPorIdYOmiteSolicitudesCanceladas() {
        Usuario propietario = usuario();
        Usuario cancelado = usuario();
        Usuario primero = usuario();
        Usuario segundo = usuario();
        Libro libro = libro();
        Prestamo anterior = prestamo(libro, propietario);
        var cancelada = espera.apuntarse(cancelado.getId(), libro.getId());
        var a = espera.apuntarse(primero.getId(), libro.getId());
        var b = espera.apuntarse(segundo.getId(), libro.getId());
        espera.cancelar(cancelado.getId(), cancelada.id());
        jdbc.update("UPDATE solicitudes_espera SET fecha_solicitud = ? WHERE libro_id = ?",
                LocalDateTime.now().minusDays(1), libro.getId());
        servicio.devolver(propietario.getId(), anterior.getId());
        assertEquals(EstadoSolicitudEspera.CANCELADA, solicitudes.findById(cancelada.id()).orElseThrow().getEstado());
        assertEquals(EstadoSolicitudEspera.ASIGNADA, solicitudes.findById(a.id()).orElseThrow().getEstado());
        assertEquals(EstadoSolicitudEspera.PENDIENTE, solicitudes.findById(b.id()).orElseThrow().getEstado());
    }

    @Test
    void siNadieEsElegibleLiberaElEjemplarYConservaLaEspera() {
        Usuario propietario = usuario();
        Usuario esperando = usuario();
        Libro libro = libro();
        Prestamo anterior = prestamo(libro, propietario);
        var solicitud = espera.apuntarse(esperando.getId(), libro.getId());
        esperando.setActivo(false);
        usuarios.flush();
        servicio.devolver(propietario.getId(), anterior.getId());
        assertEquals(EstadoEjemplar.DISPONIBLE, anterior.getEjemplar().getEstado());
        assertEquals(EstadoSolicitudEspera.PENDIENTE, solicitudes.findById(solicitud.id()).orElseThrow().getEstado());
        assertEquals(0, prestamos.countByUsuarioIdAndEstado(esperando.getId(), EstadoPrestamo.ACTIVO));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void dosDevolucionesSimultaneasNoSuperanCincoPrestamos() throws Exception {
        var tx = new TransactionTemplate(manager);
        record Datos(Long propietario, Long esperando, Long libroA, Long libroB, Long prestamoA, Long prestamoB) {}
        Datos datos = tx.execute(status -> {
            Usuario propietario = usuario();
            Usuario esperando = usuario();
            Libro a = libro();
            Libro b = libro();
            Prestamo prestamoA = prestamo(a, propietario);
            Prestamo prestamoB = prestamo(b, propietario);
            for (int i = 0; i < 4; i++) prestamo(a, esperando);
            espera.apuntarse(esperando.getId(), a.getId());
            espera.apuntarse(esperando.getId(), b.getId());
            return new Datos(propietario.getId(), esperando.getId(), a.getId(), b.getId(), prestamoA.getId(), prestamoB.getId());
        });
        ExecutorService ejecutor = Executors.newFixedThreadPool(2);
        CountDownLatch inicio = new CountDownLatch(1);
        try {
            Callable<Void> devolverA = () -> {
                inicio.await();
                servicio.devolver(datos.propietario(), datos.prestamoA());
                return null;
            };
            Callable<Void> devolverB = () -> {
                inicio.await();
                servicio.devolver(datos.propietario(), datos.prestamoB());
                return null;
            };
            Future<Void> a = ejecutor.submit(devolverA);
            Future<Void> b = ejecutor.submit(devolverB);
            inicio.countDown();
            a.get(20, TimeUnit.SECONDS);
            b.get(20, TimeUnit.SECONDS);
            assertEquals(5, prestamos.countByUsuarioIdAndEstado(datos.esperando(), EstadoPrestamo.ACTIVO));
            Long asignadas = jdbc.queryForObject("SELECT COUNT(*) FROM solicitudes_espera WHERE usuario_id = ? "
                    + "AND estado = 'ASIGNADA'", Long.class, datos.esperando());
            assertEquals(1L, asignadas);
        } finally {
            ejecutor.shutdownNow();
            ejecutor.awaitTermination(20, TimeUnit.SECONDS);
            tx.executeWithoutResult(status -> {
                jdbc.update("DELETE FROM solicitudes_espera WHERE usuario_id = ?", datos.esperando());
                jdbc.update("DELETE FROM prestamos WHERE usuario_id IN (?, ?)", datos.propietario(), datos.esperando());
                jdbc.update("DELETE FROM ejemplares WHERE libro_id IN (?, ?)", datos.libroA(), datos.libroB());
                jdbc.update("DELETE FROM libros WHERE id IN (?, ?)", datos.libroA(), datos.libroB());
                jdbc.update("DELETE FROM usuarios WHERE id IN (?, ?)", datos.propietario(), datos.esperando());
            });
        }
    }
}

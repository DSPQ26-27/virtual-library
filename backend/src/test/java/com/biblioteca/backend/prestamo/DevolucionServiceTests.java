package com.biblioteca.backend.prestamo;

import com.biblioteca.backend.catalogo.*;
import com.biblioteca.backend.usuario.*;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class DevolucionServiceTests {
    @Autowired PrestamoService servicio;
    @Autowired PrestamoRepository prestamos;
    @Autowired UsuarioRepository usuarios;
    @Autowired LibroRepository libros;
    @Autowired EjemplarRepository ejemplares;
    @Autowired PlatformTransactionManager manager;
    @Autowired JdbcTemplate jdbc;

    record Datos(Long usuario, Long otro, Long libro, Long ejemplar, Long prestamo) {}

    Datos crear(boolean inconsistente) {
        return new TransactionTemplate(manager).execute(s -> {
            String clave = UUID.randomUUID().toString();
            Usuario u = usuario(clave + "@example.com");
            Usuario otro = usuario("otro-" + clave + "@example.com");
            Libro libro = libros.save(new Libro(null, "Prueba", "Autor", null, "Prueba", 2020));
            Ejemplar ejemplar = new Ejemplar(libro, clave);
            if (!inconsistente) ejemplar.prestar();
            ejemplares.save(ejemplar);
            Prestamo prestamo = prestamos.save(new Prestamo(ejemplar, u));
            return new Datos(u.getId(), otro.getId(), libro.getId(), ejemplar.getId(), prestamo.getId());
        });
    }

    Usuario usuario(String email) {
        Usuario u = new Usuario();
        u.setNombre("Prueba"); u.setEmail(email); u.setPasswordHash("hash");
        return usuarios.save(u);
    }

    void limpiar(Datos d) {
        new TransactionTemplate(manager).executeWithoutResult(s -> {
            jdbc.update("DELETE FROM prestamos WHERE ejemplar_id = ?", d.ejemplar()); ejemplares.deleteById(d.ejemplar());
            libros.deleteById(d.libro()); usuarios.deleteById(d.usuario()); usuarios.deleteById(d.otro());
        });
    }

    @Test
    void devuelveConFechaRealYConservaHistorial() {
        Datos d = crear(false);
        try {
            LocalDateTime antes = LocalDateTime.now().minusSeconds(1);
            DevolucionResponse respuesta = servicio.devolver(d.usuario(), d.prestamo());
            assertEquals(EstadoPrestamo.DEVUELTO, respuesta.estado());
            assertTrue(respuesta.fechaDevolucion().isAfter(antes));
            assertTrue(respuesta.fechaDevolucion().isBefore(LocalDateTime.now().plusSeconds(1)));
            assertEquals(EstadoPrestamo.DEVUELTO, prestamos.findById(d.prestamo()).orElseThrow().getEstado());
            assertEquals(EstadoEjemplar.DISPONIBLE, ejemplares.findById(d.ejemplar()).orElseThrow().getEstado());
            LocalDateTime fechaGuardada = prestamos.findById(d.prestamo()).orElseThrow().getFechaDevolucion();
            assertThrows(PrestamoYaDevueltoException.class, () -> servicio.devolver(d.usuario(), d.prestamo()));
            assertEquals(fechaGuardada, prestamos.findById(d.prestamo()).orElseThrow().getFechaDevolucion());
        } finally { limpiar(d); }
    }

    @Test
    void rechazaInexistenteYAjenoSinModificar() {
        Datos d = crear(false);
        try {
            assertThrows(PrestamoNoEncontradoException.class, () -> servicio.devolver(d.otro(), d.prestamo()));
            assertThrows(PrestamoNoEncontradoException.class, () -> servicio.devolver(d.usuario(), -1L));
            assertEquals(EstadoPrestamo.ACTIVO, prestamos.findById(d.prestamo()).orElseThrow().getEstado());
            assertEquals(EstadoEjemplar.PRESTADO, ejemplares.findById(d.ejemplar()).orElseThrow().getEstado());
        } finally { limpiar(d); }
    }

    @Test
    void revierteElPrestamoSiFallaLaLiberacion() {
        Datos d = crear(true);
        try {
            assertThrows(IllegalStateException.class, () -> servicio.devolver(d.usuario(), d.prestamo()));
            Prestamo p = prestamos.findById(d.prestamo()).orElseThrow();
            assertEquals(EstadoPrestamo.ACTIVO, p.getEstado());
            assertNull(p.getFechaDevolucion());
            assertEquals(EstadoEjemplar.DISPONIBLE, ejemplares.findById(d.ejemplar()).orElseThrow().getEstado());
        } finally { limpiar(d); }
    }

    @Test
    void devolucionesSimultaneasSoloCierranUnaVez() throws Exception {
        Datos d = crear(false);
        var pool = Executors.newFixedThreadPool(2);
        CountDownLatch inicio = new CountDownLatch(1);
        Callable<String> devolver = () -> {
            inicio.await();
            try { servicio.devolver(d.usuario(), d.prestamo()); return "ok"; }
            catch (PrestamoYaDevueltoException e) { return "devuelto"; }
        };
        try {
            var a = pool.submit(devolver); var b = pool.submit(devolver); inicio.countDown();
            assertEquals(java.util.Set.of("ok", "devuelto"), java.util.Set.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS)));
            assertEquals(EstadoPrestamo.DEVUELTO, prestamos.findById(d.prestamo()).orElseThrow().getEstado());
            assertEquals(EstadoEjemplar.DISPONIBLE, ejemplares.findById(d.ejemplar()).orElseThrow().getEstado());
        } finally { pool.shutdownNow(); pool.awaitTermination(15, TimeUnit.SECONDS); limpiar(d); }
    }

    @Test
    void devolverYPrestarSimultaneamenteMantieneEstadosCoherentes() throws Exception {
        Datos d = crear(false);
        var pool = Executors.newFixedThreadPool(2);
        var inicio = new CountDownLatch(1);
        try {
            var devolucion = pool.submit(() -> { inicio.await(); return servicio.devolver(d.usuario(), d.prestamo()); });
            var solicitud = pool.submit(() -> {
                inicio.await();
                try { servicio.prestar(d.otro(), d.libro()); return true; }
                catch (SinEjemplaresDisponiblesException e) { return false; }
            });
            inicio.countDown();
            assertEquals(EstadoPrestamo.DEVUELTO, devolucion.get(15, TimeUnit.SECONDS).estado());
            boolean prestado = solicitud.get(15, TimeUnit.SECONDS);
            assertEquals(prestado ? EstadoEjemplar.PRESTADO : EstadoEjemplar.DISPONIBLE,
                ejemplares.findById(d.ejemplar()).orElseThrow().getEstado());
            assertEquals(prestado ? 1 : 0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM prestamos WHERE ejemplar_id = ? AND estado = 'ACTIVO'", Integer.class, d.ejemplar()));
            assertEquals(EstadoPrestamo.DEVUELTO, prestamos.findById(d.prestamo()).orElseThrow().getEstado());
        } finally { pool.shutdownNow(); pool.awaitTermination(15, TimeUnit.SECONDS); limpiar(d); }
    }

}

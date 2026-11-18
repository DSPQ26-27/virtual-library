package com.biblioteca.backend.prestamo;

import com.biblioteca.backend.BackendApplication;
import com.biblioteca.backend.catalogo.Ejemplar;
import com.biblioteca.backend.catalogo.EjemplarRepository;
import com.biblioteca.backend.usuario.Usuario;
import com.biblioteca.backend.usuario.UsuarioRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/** Persistencia de préstamos en H2 con rollback: fechas, estado, historial y reglas de la tabla (Issue #57). */
@SpringBootTest(classes = BackendApplication.class)
@ActiveProfiles("test")
@Transactional
class PrestamoPersistenceTests {

    private static final LocalDateTime INICIO = LocalDateTime.of(2026, 11, 17, 10, 0);

    @Autowired private PrestamoRepository prestamos;
    @Autowired private EjemplarRepository ejemplares;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;

    private Usuario estudiante(String email) {
        Usuario usuario = new Usuario();
        usuario.setNombre("Estudiante de prueba");
        usuario.setEmail(email);
        usuario.setPasswordHash("hash-de-prueba");
        return usuarios.saveAndFlush(usuario);
    }

    private Ejemplar ejemplar(String codigo) {
        return ejemplares.findByCodigo(codigo).orElseThrow();
    }

    /** Guarda los cambios y vacía la caché de JPA para leer lo que de verdad hay en la base de datos. */
    private Prestamo releer(Prestamo prestamo) {
        entityManager.flush();
        entityManager.clear();
        return prestamos.findById(prestamo.getId()).orElseThrow();
    }

    private long contar(Usuario usuario, EstadoPrestamo estado) {
        return prestamos.countByUsuarioIdAndEstado(usuario.getId(), estado);
    }

    /** Inserta con SQL directo, sin pasar por la clase Prestamo, para probar las reglas de la tabla. */
    private void insertar(long usuarioId, String estado, LocalDateTime vencimiento, LocalDateTime devolucion) {
        jdbc.update("""
            INSERT INTO prestamos (ejemplar_id, usuario_id, fecha_prestamo, fecha_vencimiento, fecha_devolucion, estado)
            VALUES (?, ?, ?, ?, ?, ?)
            """, ejemplar("EJ-0004").getId(), usuarioId, INICIO, vencimiento, devolucion, estado);
    }

    // --- La clase Prestamo se guarda bien ---

    @Test
    void unPrestamoNuevoEstaActivoYVenceA30Dias() {
        Prestamo prestamo = prestamos.save(new Prestamo(ejemplar("EJ-0001"), estudiante("prestamo-nuevo@example.com")));

        Prestamo guardado = releer(prestamo);

        assertEquals(EstadoPrestamo.ACTIVO, guardado.getEstado());
        assertTrue(guardado.isActivo());
        assertNull(guardado.getFechaDevolucion());
        assertEquals(guardado.getFechaPrestamo().plusDays(30), guardado.getFechaVencimiento());
        assertEquals("EJ-0001", guardado.getEjemplar().getCodigo());
        assertEquals("prestamo-nuevo@example.com", guardado.getUsuario().getEmail());
    }

    @Test
    void devolverCierraElPrestamoYLoConservaEnElHistorial() {
        Prestamo prestamo = releer(prestamos.save(
            new Prestamo(ejemplar("EJ-0001"), estudiante("prestamo-devuelto@example.com"))));
        long total = prestamos.count();

        prestamo.devolver();
        Prestamo devuelto = releer(prestamo);

        assertEquals(EstadoPrestamo.DEVUELTO, devuelto.getEstado());
        assertFalse(devuelto.isActivo());
        assertNotNull(devuelto.getFechaDevolucion());
        assertEquals(total, prestamos.count());

        LocalDateTime fechaDevolucion = devuelto.getFechaDevolucion();
        devuelto.devolver();
        assertEquals(fechaDevolucion, releer(devuelto).getFechaDevolucion());
    }

    @Test
    void consultaParaDevolucionSoloEncuentraElPrestamoDelPropietario() {
        Usuario propietario = estudiante("propietario-devolucion@example.com");
        Usuario otro = estudiante("otro-devolucion@example.com");
        Prestamo prestamo = prestamos.saveAndFlush(new Prestamo(ejemplar("EJ-0001"), propietario));
        entityManager.clear();

        assertTrue(prestamos.findPropioParaActualizar(prestamo.getId(), otro.getId()).isEmpty());
        Prestamo bloqueado = prestamos.findPropioParaActualizar(prestamo.getId(), propietario.getId()).orElseThrow();
        assertEquals(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE, entityManager.getLockMode(bloqueado));
        bloqueado.devolver();
        assertEquals(EstadoPrestamo.DEVUELTO, releer(bloqueado).getEstado());
        assertTrue(prestamos.findPropioParaActualizar(-1L, propietario.getId()).isEmpty());
    }

    @Test
    void cuentaLosPrestamosActivosDeCadaUsuario() {
        Usuario ana = estudiante("prestamo-ana@example.com");
        Usuario luis = estudiante("prestamo-luis@example.com");
        Prestamo anterior = prestamos.save(new Prestamo(ejemplar("EJ-0001"), ana));
        anterior.devolver();
        prestamos.flush(); // PostgreSQL comprueba el índice único al insertar el siguiente préstamo.
        prestamos.save(new Prestamo(ejemplar("EJ-0001"), ana));
        prestamos.save(new Prestamo(ejemplar("EJ-0004"), ana));
        prestamos.flush();

        assertEquals(2, contar(ana, EstadoPrestamo.ACTIVO));
        assertEquals(1, contar(ana, EstadoPrestamo.DEVUELTO));
        assertEquals(0, contar(luis, EstadoPrestamo.ACTIVO));
    }

    // --- Las reglas de la tabla (V9) ---

    @Test
    void unaFilaCorrectaSeGuarda() {
        long usuarioId = estudiante("regla-correcta@example.com").getId();

        assertDoesNotThrow(() -> insertar(usuarioId, "ACTIVO", INICIO.plusDays(30), null));
    }

    @Test
    void seRechazanEstadosDesconocidos() {
        long usuarioId = estudiante("regla-estado@example.com").getId();

        assertThrows(DataIntegrityViolationException.class,
            () -> insertar(usuarioId, "PERDIDO", INICIO.plusDays(30), null));
    }

    @Test
    void unPrestamoActivoNoPuedeTenerFechaDeDevolucion() {
        long usuarioId = estudiante("regla-activo@example.com").getId();

        assertThrows(DataIntegrityViolationException.class,
            () -> insertar(usuarioId, "ACTIVO", INICIO.plusDays(30), INICIO.plusDays(3)));
    }

    @Test
    void unPrestamoDevueltoTieneQueTenerFechaDeDevolucion() {
        long usuarioId = estudiante("regla-devuelto@example.com").getId();

        assertThrows(DataIntegrityViolationException.class,
            () -> insertar(usuarioId, "DEVUELTO", INICIO.plusDays(30), null));
    }

    @Test
    void elVencimientoNoPuedeSerAnteriorAlPrestamo() {
        long usuarioId = estudiante("regla-vencimiento@example.com").getId();

        assertThrows(DataIntegrityViolationException.class,
            () -> insertar(usuarioId, "ACTIVO", INICIO.minusDays(1), null));
    }

    @Test
    void elVencimientoEsObligatorio() {
        long usuarioId = estudiante("regla-sin-vencimiento@example.com").getId();

        assertThrows(DataIntegrityViolationException.class,
            () -> insertar(usuarioId, "ACTIVO", null, null));
    }
}

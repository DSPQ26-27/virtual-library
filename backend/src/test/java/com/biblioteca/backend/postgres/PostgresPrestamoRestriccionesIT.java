package com.biblioteca.backend.postgres;

import com.biblioteca.backend.catalogo.Ejemplar;
import com.biblioteca.backend.catalogo.EjemplarRepository;
import com.biblioteca.backend.catalogo.Libro;
import com.biblioteca.backend.catalogo.LibroRepository;
import com.biblioteca.backend.prestamo.Prestamo;
import com.biblioteca.backend.prestamo.PrestamoRepository;
import com.biblioteca.backend.usuario.Usuario;
import com.biblioteca.backend.usuario.UsuarioRepository;
import jakarta.persistence.EntityManager;
import java.sql.SQLException;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.junit.jupiter.api.Assertions.*;

@PostgresIntegrationTest
class PostgresPrestamoRestriccionesIT {
    @Autowired PostgreSQLContainer postgres;
    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @Autowired UsuarioRepository usuarios;
    @Autowired LibroRepository libros;
    @Autowired EjemplarRepository ejemplares;
    @Autowired PrestamoRepository prestamos;
    @Autowired EntityManager entityManager;

    private record Datos(long usuario, long libro, long ejemplar, long prestamo) {}
    private Datos datos;

    @BeforeEach
    void crearDatosConfirmados() throws Exception {
        comprobarConexionAislada();
        datos = new TransactionTemplate(manager).execute(status -> {
            String clave = UUID.randomUUID().toString();
            Usuario usuario = new Usuario();
            usuario.setNombre("Prueba");
            usuario.setEmail(clave + "@example.com");
            usuario.setPasswordHash("hash");
            usuarios.save(usuario);
            Libro libro = libros.save(new Libro(null, "Libro de prueba", "Autor", null, null, 2020));
            Ejemplar ejemplar = ejemplares.save(new Ejemplar(libro, clave));
            Prestamo prestamo = prestamos.saveAndFlush(new Prestamo(ejemplar, usuario));
            return new Datos(usuario.getId(), libro.getId(), ejemplar.getId(), prestamo.getId());
        });
    }

    @AfterEach
    void limpiarDatosPropios() throws Exception {
        if (datos == null) return;
        comprobarConexionAislada();
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            jdbc.update("DELETE FROM prestamos WHERE ejemplar_id = ?", datos.ejemplar());
            jdbc.update("DELETE FROM ejemplares WHERE id = ?", datos.ejemplar());
            jdbc.update("DELETE FROM libros WHERE id = ?", datos.libro());
            jdbc.update("DELETE FROM usuarios WHERE id = ?", datos.usuario());
        });
    }

    private void comprobarConexionAislada() throws Exception {
        assertTrue(postgres.isRunning());
        try (var conexion = dataSource.getConnection()) {
            assertEquals(postgres.getJdbcUrl(), conexion.getMetaData().getURL());
            assertEquals(postgres.getDatabaseName(), conexion.getCatalog());
            assertEquals(postgres.getUsername(), conexion.getMetaData().getUserName());
        }
    }

    // Traslada la comprobación SQL que antes se omitía con H2 a la suite PostgreSQL.
    @Test
    void postgresImpideDosPrestamosActivosDelMismoEjemplar() {
        assertEquals(1, contar("ACTIVO"));
        DataIntegrityViolationException error = assertThrows(DataIntegrityViolationException.class,
            () -> new TransactionTemplate(manager).executeWithoutResult(status -> jdbc.update("""
                INSERT INTO prestamos (ejemplar_id, usuario_id, fecha_prestamo, fecha_vencimiento, estado)
                SELECT ejemplar_id, usuario_id, fecha_prestamo, fecha_vencimiento, estado
                FROM prestamos WHERE id = ?
                """, datos.prestamo())));
        comprobarViolacionV10(error);
        assertEquals(1, contar("ACTIVO"));
        assertEquals(1, total());
    }

    @Test
    void jpaNoPuedeInsertarOtroActivoNiDejarCambiosTrasElFallo() {
        RuntimeException error = assertThrows(RuntimeException.class,
            () -> new TransactionTemplate(manager).executeWithoutResult(status -> {
                Ejemplar ejemplar = entityManager.getReference(Ejemplar.class, datos.ejemplar());
                Usuario usuario = entityManager.getReference(Usuario.class, datos.usuario());
                prestamos.saveAndFlush(new Prestamo(ejemplar, usuario));
            }));
        comprobarViolacionV10(error);
        assertEquals(1, contar("ACTIVO"));
        assertEquals(1, total());
    }

    @Test
    void flushDeDevolucionPermiteNuevoPrestamoYConservaHistorial() {
        Long nuevoId = new TransactionTemplate(manager).execute(status -> {
            Prestamo anterior = prestamos.findById(datos.prestamo()).orElseThrow();
            anterior.devolver();
            prestamos.flush();
            return prestamos.saveAndFlush(new Prestamo(anterior.getEjemplar(), anterior.getUsuario())).getId();
        });
        assertNotNull(nuevoId);
        assertNotEquals(datos.prestamo(), nuevoId);
        assertEquals(1, contar("ACTIVO"));
        assertEquals(1, contar("DEVUELTO"));
        assertEquals(2, total());
        assertEquals(1, jdbc.queryForObject("""
            SELECT count(*) FROM prestamos WHERE id = ? AND ejemplar_id = ?
              AND estado = 'DEVUELTO' AND fecha_devolucion IS NOT NULL
            """, Integer.class, datos.prestamo(), datos.ejemplar()));
    }

    private int contar(String estado) {
        return jdbc.queryForObject("SELECT count(*) FROM prestamos WHERE ejemplar_id = ? AND estado = ?",
            Integer.class, datos.ejemplar(), estado);
    }

    private int total() {
        return jdbc.queryForObject("SELECT count(*) FROM prestamos WHERE ejemplar_id = ?", Integer.class, datos.ejemplar());
    }

    private void comprobarViolacionV10(Throwable error) {
        Throwable causa = error;
        while (causa != null && !(causa instanceof SQLException)) causa = causa.getCause();
        assertInstanceOf(SQLException.class, causa);
        SQLException sql = (SQLException) causa;
        assertEquals("23505", sql.getSQLState());
        assertTrue(sql.getMessage().contains("uq_prestamos_ejemplar_activo"));
    }
}

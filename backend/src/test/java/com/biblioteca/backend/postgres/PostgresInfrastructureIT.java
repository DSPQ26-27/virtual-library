package com.biblioteca.backend.postgres;

import jakarta.persistence.EntityManagerFactory;
import java.sql.Connection;
import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.*;

@PostgresIntegrationTest
class PostgresInfrastructureIT {
    @Autowired PostgreSQLContainer postgres;
    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;
    @Autowired EntityManagerFactory entityManagerFactory;

    @Test
    void springUsaElContenedorPostgresql17ConEsquemaValidado() throws Exception {
        assertTrue(postgres.isRunning());
        try (Connection conexion = dataSource.getConnection()) {
            assertEquals("PostgreSQL", conexion.getMetaData().getDatabaseProductName());
            assertEquals(17, conexion.getMetaData().getDatabaseMajorVersion());
            assertEquals(postgres.getJdbcUrl(), conexion.getMetaData().getURL());
            assertEquals(postgres.getDatabaseName(), conexion.getCatalog());
            assertEquals(postgres.getUsername(), conexion.getMetaData().getUserName());
        }
        assertTrue(entityManagerFactory.isOpen());
        assertEquals(0, flyway.info().pending().length);
        assertTrue(flyway.validateWithResult().validationSuccessful);
        assertEquals(13, jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success", Integer.class));
        assertEquals(List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13"),
            jdbc.queryForList("SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank", String.class));
    }

    @Test
    void migracionesEspecificasCreanExtensionIndicesYCoordinacion() {
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM pg_extension WHERE extname = 'pg_trgm'", Integer.class));
        assertEquals(6, jdbc.queryForObject("""
            SELECT count(*) FROM pg_indexes WHERE schemaname = current_schema() AND indexname IN
            ('idx_libros_titulo_trgm', 'idx_libros_autor_trgm', 'idx_libros_isbn_trgm',
             'idx_libros_categoria_lower', 'idx_libros_titulo_id', 'idx_ejemplares_libro_activo_estado')
            """, Integer.class));
        String indice = jdbc.queryForObject("""
            SELECT indexdef FROM pg_indexes WHERE schemaname = current_schema()
              AND indexname = 'uq_prestamos_ejemplar_activo'
            """, String.class);
        assertNotNull(indice);
        assertTrue(indice.contains("UNIQUE INDEX"));
        assertTrue(indice.contains("ejemplar_id"));
        assertTrue(indice.contains("WHERE") && indice.contains("ACTIVO"));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM coordinacion_prestamos WHERE id = 1", Integer.class));
    }
}

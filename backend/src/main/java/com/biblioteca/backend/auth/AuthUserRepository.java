package com.biblioteca.backend.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Repository;

/** Lectura de autenticación sobre el esquema existente; no sustituye la entidad de #21. */
@Repository
public class AuthUserRepository {
    private final JdbcTemplate jdbc;

    public AuthUserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public AuthUser findByEmail(String email) {
        var users = jdbc.query("""
            SELECT id, nombre, email, password_hash, rol, activo
            FROM usuarios WHERE LOWER(email) = LOWER(?)
            """, (row, index) -> new AuthUser(row.getLong("id"), row.getString("nombre"),
                row.getString("email"), row.getString("password_hash"), row.getString("rol"),
                row.getBoolean("activo")), email);
        // Rechazar también identidades ambiguas si existen correos que solo difieren en mayúsculas.
        if (users.size() != 1) {
            throw new UsernameNotFoundException("Credenciales incorrectas.");
        }
        return users.getFirst();
    }

    public record AuthUser(long id, String nombre, String email, String passwordHash,
                           String rol, boolean activo) {}
}

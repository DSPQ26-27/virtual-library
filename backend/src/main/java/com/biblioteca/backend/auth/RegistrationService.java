package com.biblioteca.backend.auth;

import java.util.Locale;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Alta de estudiantes. Usa JdbcTemplate hasta que exista la entidad Usuario (#21). */
@Service
public class RegistrationService {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;

    public RegistrationService(JdbcTemplate jdbc, PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        // Guardamos el correo en minúsculas para que sea único sin distinguir mayúsculas.
        String email = request.email().toLowerCase(Locale.ROOT);
        if (emailExists(email)) {
            throw new EmailAlreadyRegisteredException();
        }

        // Nunca se guarda la contraseña en claro: solo su hash BCrypt.
        String passwordHash = passwordEncoder.encode(request.password());
        try {
            jdbc.update("""
                INSERT INTO usuarios (nombre, email, password_hash, rol)
                VALUES (?, ?, ?, 'ESTUDIANTE')
                """, request.nombre(), email, passwordHash);
        } catch (DuplicateKeyException e) {
            // Dos registros simultáneos con el mismo correo: la restricción UNIQUE lo frena.
            throw new EmailAlreadyRegisteredException();
        }

        Long id = jdbc.queryForObject(
            "SELECT id FROM usuarios WHERE email = ?", Long.class, email);
        return new RegisterResponse(id, request.nombre(), email);
    }

    private boolean emailExists(String email) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM usuarios WHERE LOWER(email) = ?", Integer.class, email);
        return count != null && count > 0;
    }
}

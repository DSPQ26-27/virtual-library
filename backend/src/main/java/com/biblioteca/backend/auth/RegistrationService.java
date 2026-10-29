
package com.biblioteca.backend.auth;

import com.biblioteca.backend.usuario.Usuario;
import com.biblioteca.backend.usuario.UsuarioRepository;

import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public RegistrationService(
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder) {

        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {

        // 1. Normalizar el correo electrónico
        String email = request.email().toLowerCase(Locale.ROOT);

        // 2. Comprobar si el usuario ya existe
        if (usuarioRepository.existsByEmailIgnoreCase(email)) {
            throw new EmailAlreadyRegisteredException();
        }

        // 3. Crear una entidad Usuario
        Usuario usuario = new Usuario();
        usuario.setNombre(request.nombre());
        usuario.setEmail(email);

        // 4. Generar el hash de la contraseña
        usuario.setPasswordHash(
            passwordEncoder.encode(request.password())
        );

        // 5. Establecer el rol y el estado inicial
        usuario.setRol("ESTUDIANTE");
        usuario.setActivo(true);

        // 6. Guardar en PostgreSQL mediante JPA
        try {
            usuario = usuarioRepository.saveAndFlush(usuario);
        } catch (DataIntegrityViolationException ex) {
            throw new EmailAlreadyRegisteredException();
        }

        // 7. Devolver los datos del nuevo estudiante
        return new RegisterResponse(
            usuario.getId(),
            usuario.getNombre(),
            usuario.getEmail()
        );
    }
}


package com.biblioteca.backend.auth;

import com.biblioteca.backend.usuario.Usuario;

import java.util.List;
import java.util.Locale;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

public class LibraryUserDetails extends User {

    private static final long serialVersionUID = 1L;

    private final long id;
    private final String nombre;
    private final String role;

    // Recibimos directamente la entidad JPA Usuario
    public LibraryUserDetails(Usuario usuario) {
        this(usuario, canonicalRole(usuario.getRol()));
    }

    private LibraryUserDetails(Usuario usuario, String role) {

        // Inicializamos los datos que necesita Spring Security
        super(
            usuario.getEmail(),
            usuario.getPasswordHash(),
            usuario.isActivo(),
            true,
            true,
            true,
            List.of(new SimpleGrantedAuthority("ROLE_" + role))
        );

        // Guardamos nuestros datos adicionales
        this.id = usuario.getId();
        this.nombre = usuario.getNombre();
        this.role = role;
    }

    public long getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public String getRole() {
        return role;
    }

    private static String canonicalRole(String value) {

        if (value == null) {
            throw new UsernameNotFoundException("Rol no admitido.");
        }

        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "ESTUDIANTE", "STUDENT" -> "STUDENT";
            case "BIBLIOTECARIO", "LIBRARIAN" -> "LIBRARIAN";
            case "ADMINISTRADOR", "ADMIN" -> "ADMIN";
            default -> throw new UsernameNotFoundException("Rol no admitido.");
        };
    }
}

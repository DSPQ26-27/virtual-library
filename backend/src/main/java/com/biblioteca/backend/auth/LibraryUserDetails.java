package com.biblioteca.backend.auth;

import java.util.List;
import java.util.Locale;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/** Principal de sesión; User borra el hash tras autenticar mediante CredentialsContainer. */
public class LibraryUserDetails extends User {
    private static final long serialVersionUID = 1L;
    private final long id;
    private final String nombre;
    private final String role;

    public LibraryUserDetails(AuthUserRepository.AuthUser user) {
        this(user, canonicalRole(user.rol()));
    }

    private LibraryUserDetails(AuthUserRepository.AuthUser user, String role) {
        super(user.email(), user.passwordHash(), user.activo(), true, true, true,
            List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        this.id = user.id();
        this.nombre = user.nombre();
        this.role = role;
    }

    public long getId() { return id; }
    public String getNombre() { return nombre; }
    public String getRole() { return role; }

    private static String canonicalRole(String value) {
        if (value == null) throw new UsernameNotFoundException("Rol no admitido.");
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "ESTUDIANTE", "STUDENT" -> "STUDENT";
            case "BIBLIOTECARIO", "LIBRARIAN" -> "LIBRARIAN";
            case "ADMINISTRADOR", "ADMIN" -> "ADMIN";
            default -> throw new UsernameNotFoundException("Rol no admitido.");
        };
    }
}


package com.biblioteca.backend.auth;

import com.biblioteca.backend.usuario.Usuario;
import com.biblioteca.backend.usuario.UsuarioRepository;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class LibraryUserDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    public LibraryUserDetailsService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {

        Usuario usuario = usuarioRepository
            .findByEmailIgnoreCase(email.trim())
            .orElseThrow(() ->
                new UsernameNotFoundException("Credenciales incorrectas.")
            );

        return new LibraryUserDetails(usuario);
    }
}

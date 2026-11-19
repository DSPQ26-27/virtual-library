package com.biblioteca.backend.auth;

import com.biblioteca.backend.usuario.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AutenticacionServiceUnitTests {
    @Mock UsuarioRepository usuarios;
    @Mock PasswordEncoder encoder;

    @Test void registroNormalizaYCodificaConRolEstudiante() {
        when(encoder.encode("clave")).thenReturn("hash");
        when(usuarios.saveAndFlush(any(Usuario.class))).thenAnswer(i -> {
            Usuario u = i.getArgument(0); ReflectionTestUtils.setField(u, "id", 7L); return u;
        });
        var resultado = new RegistrationService(usuarios, encoder).register(new RegisterRequest(" Ana ", " ANA@EXAMPLE.COM ", "clave"));
        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarios).saveAndFlush(captor.capture());
        Usuario u = captor.getValue();
        assertAll(() -> assertEquals("ana@example.com", u.getEmail()), () -> assertEquals("Ana", u.getNombre()),
            () -> assertEquals("hash", u.getPasswordHash()), () -> assertEquals("ESTUDIANTE", u.getRol()),
            () -> assertTrue(u.isActivo()), () -> assertEquals(7L, resultado.id()));
        verify(usuarios).existsByEmailIgnoreCase("ana@example.com");
    }

    @Test void duplicadoNoCodificaNiGuarda() {
        when(usuarios.existsByEmailIgnoreCase("ana@example.com")).thenReturn(true);
        assertThrows(EmailAlreadyRegisteredException.class, () -> new RegistrationService(usuarios, encoder)
            .register(new RegisterRequest("Ana", "ANA@example.com", "clave")));
        verifyNoInteractions(encoder);
        verify(usuarios, never()).saveAndFlush(any());
    }

    @Test void falloDeIntegridadSeTraduceADuplicado() {
        when(encoder.encode("clave")).thenReturn("hash");
        when(usuarios.saveAndFlush(any(Usuario.class))).thenThrow(new DataIntegrityViolationException("duplicado"));
        assertThrows(EmailAlreadyRegisteredException.class, () -> new RegistrationService(usuarios, encoder)
            .register(new RegisterRequest("Ana", "ana@example.com", "clave")));
    }

    @Test void usuarioRecortadoConservaEstadoYMapeaRol() {
        Usuario u = new Usuario(); u.setEmail("ana@example.com"); u.setNombre("Ana"); u.setPasswordHash("hash");
        u.setActivo(false); u.setRol("bibliotecario"); ReflectionTestUtils.setField(u, "id", 7L);
        when(usuarios.findByEmailIgnoreCase("ana@example.com")).thenReturn(Optional.of(u));
        var detalles = new LibraryUserDetailsService(usuarios).loadUserByUsername("  ana@example.com  ");
        assertFalse(detalles.isEnabled());
        assertEquals("hash", detalles.getPassword());
        assertTrue(detalles.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_LIBRARIAN")));
    }

    @Test void usuarioInexistenteORolDesconocidoSeRechaza() {
        when(usuarios.findByEmailIgnoreCase("ausente")).thenReturn(Optional.empty());
        var servicio = new LibraryUserDetailsService(usuarios);
        assertThrows(UsernameNotFoundException.class, () -> servicio.loadUserByUsername("ausente"));
        Usuario u = new Usuario(); u.setRol("OTRO");
        when(usuarios.findByEmailIgnoreCase("otro")).thenReturn(Optional.of(u));
        assertThrows(UsernameNotFoundException.class, () -> servicio.loadUserByUsername("otro"));
    }
}

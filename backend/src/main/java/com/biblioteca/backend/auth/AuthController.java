package com.biblioteca.backend.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.web.bind.annotation.RequestHeader;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final SessionAuthenticationStrategy sessionStrategy;
    private final SecurityContextRepository contextRepository;

    public AuthController(AuthenticationManager authenticationManager,
                          SessionAuthenticationStrategy sessionStrategy,
                          SecurityContextRepository contextRepository) {
        this.authenticationManager = authenticationManager;
        this.sessionStrategy = sessionStrategy;
        this.contextRepository = contextRepository;
    }

    @GetMapping("/csrf")
    public CsrfResponse csrf(CsrfToken token) {
        return new CsrfResponse(token.getHeaderName(), token.getToken());
    }

@PostMapping("/login")
public UserResponse login(
        @Valid @RequestBody LoginRequest body,

        @Parameter(
            description = "Token CSRF obtenido de GET /api/auth/csrf",
            required = true
        )
        @RequestHeader("X-CSRF-TOKEN") String csrfToken,

        HttpServletRequest request,
        HttpServletResponse response) {

    // 1. Autenticar al usuario con email y contraseña
    var authentication = authenticationManager.authenticate(
        UsernamePasswordAuthenticationToken.unauthenticated(
            body.email(),
            body.password()
        )
    );

    // 2. Aplicar las medidas de seguridad de la sesión
    // (rotación de sesión y renovación del token CSRF)
    sessionStrategy.onAuthentication(
        authentication,
        request,
        response
    );

    // 3. Crear el contexto de seguridad
    var context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(authentication);

    // 4. Establecer y guardar el contexto de autenticación
    SecurityContextHolder.setContext(context);
    contextRepository.saveContext(
        context,
        request,
        response
    );

    // 5. Devolver los datos del usuario autenticado
    return UserResponse.from(
        (LibraryUserDetails) authentication.getPrincipal()
    );
}


    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal LibraryUserDetails user) {
        if (user == null) {
            throw new AuthenticationCredentialsNotFoundException("Autenticación requerida.");
        }
        return UserResponse.from(user);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response,
                                       Authentication authentication) {
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        new CookieClearingLogoutHandler("JSESSIONID").logout(request, response, authentication);
        return ResponseEntity.noContent().build();
    }

    public record LoginRequest(@NotBlank @Email @Size(max = 254) String email,
                               @NotBlank @Size(max = 1024) String password) {
        public LoginRequest {
            if (email != null) email = email.trim();
        }
    }

    public record CsrfResponse(String headerName, String token) {}

    public record UserResponse(long id, String nombre, String email, List<String> roles) {
        static UserResponse from(LibraryUserDetails user) {
            return new UserResponse(user.getId(), user.getNombre(), user.getUsername(),
                List.of(user.getRole()));
        }
    }
}

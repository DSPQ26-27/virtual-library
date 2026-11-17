
package com.biblioteca.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

import java.util.List;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http)
            throws Exception {

        http
            // Configuración de la sesión
            .securityContext(context -> context
                .securityContextRepository(securityContextRepository()))

            // Protección CSRF
            .csrf(csrf -> csrf
                .csrfTokenRepository(csrfTokenRepository())
                .ignoringRequestMatchers(
                    PathPatternRequestMatcher.withDefaults()
                        .matcher(HttpMethod.POST, "/api/auth/register")
                )
            )

            // Permisos de acceso a los endpoints
            .authorizeHttpRequests(auth -> auth

                // Endpoints públicos de autenticación
                .requestMatchers(
                    HttpMethod.GET,
                    "/api/health",
                    "/api/auth/csrf"
                ).permitAll()

                .requestMatchers(
                    HttpMethod.POST,
                    "/api/auth/register",
                    "/api/auth/login"
                ).permitAll()

                // Swagger UI y documentación OpenAPI
                .requestMatchers(
                    "/swagger-ui.html",
                    "/swagger-ui/**",
                    "/v3/api-docs",
                    "/v3/api-docs/**",
                    "/v3/api-docs.yaml"
                ).permitAll()

                // Administración: solo administradores
                .requestMatchers("/api/admin/**")
                    .hasRole("ADMIN")

                // Consultar libros: todos los usuarios autenticados
                .requestMatchers(
                    HttpMethod.GET,
                    "/api/libros",
                    "/api/libros/**"
                ).hasAnyRole("STUDENT", "LIBRARIAN", "ADMIN")

                // Modificar libros: bibliotecarios y administradores
                .requestMatchers(
                    "/api/libros",
                    "/api/libros/**"
                ).hasAnyRole("LIBRARIAN", "ADMIN")

                // Pedir préstamos: solo estudiantes (US07)
                .requestMatchers(HttpMethod.POST, "/api/prestamos", "/api/prestamos/{id}/devolucion")
                    .hasRole("STUDENT")

                .requestMatchers(HttpMethod.GET, "/api/prestamos/mios")
                    .hasRole("STUDENT")

                // Resto de endpoints
                .anyRequest()
                    .hasAnyRole("STUDENT", "LIBRARIAN", "ADMIN")
            )

            // Desactivar el formulario de login de Spring Security
            .formLogin(AbstractHttpConfigurer::disable)

            // Desactivar autenticación HTTP Basic
            .httpBasic(AbstractHttpConfigurer::disable)

            // Utilizamos nuestro propio endpoint de logout
            .logout(AbstractHttpConfigurer::disable)

            // Desactivar almacenamiento automático de peticiones
            .requestCache(AbstractHttpConfigurer::disable)

            // Respuestas de error HTTP
            .exceptionHandling(errors -> errors

                // 401: usuario no autenticado
                .authenticationEntryPoint((request, response, exception) -> {
                    response.setStatus(401);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write(
                        "{\"message\":\"Autenticación requerida.\"}"
                    );
                })

                // 403: usuario sin permisos
                .accessDeniedHandler((request, response, exception) -> {
                    response.setStatus(403);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write(
                        "{\"message\":\"Acceso denegado.\"}"
                    );
                })
            );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public HttpSessionCsrfTokenRepository csrfTokenRepository() {
        return new HttpSessionCsrfTokenRepository();
    }

    @Bean
    public SessionAuthenticationStrategy sessionAuthenticationStrategy() {
        return new CompositeSessionAuthenticationStrategy(List.of(
            new ChangeSessionIdAuthenticationStrategy(),
            new CsrfAuthenticationStrategy(csrfTokenRepository())
        ));
    }
}

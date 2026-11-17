package com.biblioteca.backend.prestamo;

import com.biblioteca.backend.auth.LibraryUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Préstamos (US07). Solo STUDENT puede pedirlos (lo exige SecurityConfig). */
@Tag(name = "Préstamos", description = "Préstamo de ejemplares a estudiantes (US07)")
@RestController
@RequestMapping("/api/prestamos")
public class PrestamoController {

    private final PrestamoService prestamos;

    public PrestamoController(PrestamoService prestamos) {
        this.prestamos = prestamos;
    }

    @Operation(summary = "Pedir un préstamo",
        description = "El estudiante de la sesión pide un libro y se le asigna automáticamente "
            + "un ejemplar disponible con vencimiento a 30 días. Requiere el token CSRF en X-CSRF-TOKEN.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Préstamo creado"),
        @ApiResponse(responseCode = "400", description = "Datos no válidos"),
        @ApiResponse(responseCode = "401", description = "Sin sesión"),
        @ApiResponse(responseCode = "403", description = "No es estudiante o falta el token CSRF"),
        @ApiResponse(responseCode = "404", description = "El libro no existe o está retirado"),
        @ApiResponse(responseCode = "409", description = "Préstamos vencidos, límite de 5 o sin ejemplares disponibles")
    })
    @PostMapping
    public ResponseEntity<PrestamoResponse> prestar(
            @Parameter(hidden = true) @AuthenticationPrincipal LibraryUserDetails usuario,
            @Valid @RequestBody PrestamoRequest datos) {
        PrestamoResponse prestamo = prestamos.prestar(usuario.getId(), datos.libroId());
        return ResponseEntity.status(HttpStatus.CREATED).body(prestamo);
    }
}

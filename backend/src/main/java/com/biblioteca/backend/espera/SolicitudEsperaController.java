package com.biblioteca.backend.espera;

import com.biblioteca.backend.auth.LibraryUserDetails;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

@RestController
@RequestMapping("/api/espera")
public class SolicitudEsperaController {

    private final SolicitudEsperaService servicio;

    public SolicitudEsperaController(SolicitudEsperaService servicio) {
        this.servicio = servicio;
    }

    @PostMapping
    public ResponseEntity<SolicitudEsperaResponse> apuntarse(
            @AuthenticationPrincipal LibraryUserDetails usuario,
            @Valid @RequestBody SolicitudEsperaRequest datos) {
        SolicitudEsperaResponse solicitud = servicio.apuntarse(usuario.getId(), datos.libroId());
        return ResponseEntity.status(HttpStatus.CREATED).body(solicitud);
    }

    @GetMapping("/mias")
    public List<SolicitudEsperaResponse> misSolicitudes(
            @AuthenticationPrincipal LibraryUserDetails usuario) {
        return servicio.misSolicitudes(usuario.getId());
    }

    @PostMapping("/{id}/cancelacion")
    public SolicitudEsperaResponse cancelar(@AuthenticationPrincipal LibraryUserDetails usuario,
            @PathVariable Long id) {
        return servicio.cancelar(usuario.getId(), id);
    }

}


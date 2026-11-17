package com.biblioteca.backend.prestamo;

import java.util.List;

public record MisPrestamosResponse(List<PrestamoListadoResponse> activos,
    List<PrestamoListadoResponse> historial) {}

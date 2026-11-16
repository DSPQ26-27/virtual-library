package com.biblioteca.backend.catalogo;

import java.util.List;

/** El total incluye los retirados; disponibles cuenta solo los activos y prestables. */
public record EjemplaresResponse(List<EjemplarResponse> ejemplares, long total, long disponibles) {}

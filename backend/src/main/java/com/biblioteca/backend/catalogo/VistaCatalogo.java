package com.biblioteca.backend.catalogo;

/** Vistas de disponibilidad del catálogo (#47). */
public enum VistaCatalogo {
    /** Todo el catálogo, tenga o no ejemplares la biblioteca. */
    TODOS,
    /** Libros con al menos un ejemplar activo en nuestra biblioteca. */
    BIBLIOTECA,
    /** Libros con al menos un ejemplar activo y DISPONIBLE (prestable). */
    DISPONIBLES
}

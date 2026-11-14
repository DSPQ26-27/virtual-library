package com.biblioteca.backend.catalogo;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/** Filtros combinables del catálogo (#47). Cada filtro solo se aplica si viene informado. */
public final class LibroSpecifications {

    private LibroSpecifications() {}

    public static Specification<Libro> filtrar(String texto, String categoria, VistaCatalogo vista) {
        return (libro, query, cb) -> {
            List<Predicate> condiciones = new ArrayList<>();

            // 1. Texto libre: contiene la palabra en título, autor o ISBN (sin distinguir mayúsculas)
            if (texto != null && !texto.isBlank()) {
                String patron = "%" + escaparLike(texto.trim().toLowerCase(Locale.ROOT)) + "%";
                condiciones.add(cb.or(
                    cb.like(cb.lower(libro.get("titulo")), patron, '\\'),
                    cb.like(cb.lower(libro.get("autor")), patron, '\\'),
                    cb.like(libro.get("isbn"), patron, '\\')));
            }

            // 2. Categoría exacta (sin distinguir mayúsculas)
            if (categoria != null && !categoria.isBlank()) {
                condiciones.add(cb.equal(cb.lower(libro.get("categoria")),
                    categoria.trim().toLowerCase(Locale.ROOT)));
            }

            // 3. Vista: exige que exista al menos un ejemplar que cumpla la condición
            if (vista == VistaCatalogo.BIBLIOTECA || vista == VistaCatalogo.DISPONIBLES) {
                Subquery<Long> sub = query.subquery(Long.class);
                Root<Ejemplar> ejemplar = sub.from(Ejemplar.class);
                List<Predicate> deEsteLibro = new ArrayList<>();
                deEsteLibro.add(cb.equal(ejemplar.get("libro"), libro));
                deEsteLibro.add(cb.isTrue(ejemplar.get("activo")));
                if (vista == VistaCatalogo.DISPONIBLES) {
                    deEsteLibro.add(cb.equal(ejemplar.get("estado"), EstadoEjemplar.DISPONIBLE));
                }
                sub.select(ejemplar.get("id")).where(deEsteLibro.toArray(new Predicate[0]));
                condiciones.add(cb.exists(sub));
            }

            return cb.and(condiciones.toArray(new Predicate[0]));
        };
    }

    /** Evita que un % o _ escrito por el usuario actúe como comodín. */
    private static String escaparLike(String valor) {
        return valor.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}

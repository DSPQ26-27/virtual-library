package com.biblioteca.backend.catalogo;

import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Crear, editar y retirar libros del catálogo (#51). */
@Service
public class LibroGestionService {

    private final LibroRepository libros;

    public LibroGestionService(LibroRepository libros) {
        this.libros = libros;
    }

    @Transactional(readOnly = true)
    public LibroDetalleResponse obtener(Long id) {
        return LibroDetalleResponse.from(buscar(id));
    }

    @Transactional
    public LibroDetalleResponse crear(LibroRequest datos) {
        comprobarIsbnLibre(datos.isbn(), null);
        Libro libro = new Libro(datos.isbn(), datos.titulo(), datos.autor(), datos.editorial(),
            datos.categoria(), datos.anioPublicacion());
        aplicarDatos(libro, datos);            // descripción y portada
        return guardar(libro);                 // origen MANUAL y retirado false por defecto
    }

    @Transactional
    public LibroDetalleResponse editar(Long id, LibroRequest datos) {
        Libro libro = buscar(id);
        comprobarIsbnLibre(datos.isbn(), id);  // su propio ISBN no cuenta como duplicado
        aplicarDatos(libro, datos);
        return guardar(libro);
    }

    @Transactional
    public void retirar(Long id) {
        buscar(id).retirar();                  // retirada lógica: sin DELETE en la base de datos
    }

    // ---------- Ayudas ----------

    private Libro buscar(Long id) {
        return libros.findById(id).orElseThrow(LibroNoEncontradoException::new);
    }

    private void comprobarIsbnLibre(String isbn, Long idActual) {
        if (isbn == null) return;              // el ISBN es opcional
        String normalizado = isbn.replaceAll("[- ]", "").toUpperCase(Locale.ROOT);
        boolean usadoPorOtro = libros.findIdsByIsbnNormalizado(normalizado).stream()
            .anyMatch(id -> !id.equals(idActual));
        if (usadoPorOtro) throw new IsbnDuplicadoException();
    }

    private void aplicarDatos(Libro libro, LibroRequest datos) {
        libro.actualizarDatos(datos.isbn(), datos.titulo(), datos.autor(), datos.editorial(),
            datos.categoria(), datos.anioPublicacion(), datos.descripcion(), datos.portadaUrl());
    }

    private LibroDetalleResponse guardar(Libro libro) {
        try {
            return LibroDetalleResponse.from(libros.saveAndFlush(libro));
        } catch (DataIntegrityViolationException e) {
            throw new IsbnDuplicadoException(); // dos altas simultáneas con el mismo ISBN
        }
    }
}

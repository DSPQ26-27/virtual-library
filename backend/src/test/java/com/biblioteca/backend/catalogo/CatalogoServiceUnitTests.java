package com.biblioteca.backend.catalogo;

import com.biblioteca.backend.prestamo.PrestamoRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CatalogoServiceUnitTests {
    @Mock LibroRepository libros;
    @Mock EjemplarRepository ejemplares;
    @Mock PrestamoRepository prestamos;

    private LibroRequest datos(String isbn) {
        return new LibroRequest(isbn, "Titulo", "Autor", null, null, 2020, null, null);
    }
    private Libro libro() {
        Libro l = new Libro(null, "Anterior", "Autor", null, null, 2020);
        ReflectionTestUtils.setField(l, "id", 1L); return l;
    }
    private Ejemplar ejemplar(Libro libro) {
        Ejemplar e = new Ejemplar(libro, "E1"); ReflectionTestUtils.setField(e, "id", 2L); return e;
    }

    @Test void crearNormalizaIsbnParaDetectarDuplicadosYGuardaDatos() {
        when(libros.findIdsByIsbnNormalizado("123456789X")).thenReturn(List.of());
        when(libros.saveAndFlush(any(Libro.class))).thenAnswer(i -> i.getArgument(0));
        var r = new LibroGestionService(libros).crear(datos("123-456 789x"));
        ArgumentCaptor<Libro> captor = ArgumentCaptor.forClass(Libro.class);
        verify(libros).saveAndFlush(captor.capture());
        assertEquals("Titulo", captor.getValue().getTitulo());
        assertEquals("Titulo", r.titulo());
        assertEquals("MANUAL", captor.getValue().getOrigen());
    }

    @Test void isbnDeOtroLibroSeRechazaSinGuardar() {
        when(libros.findIdsByIsbnNormalizado("123456789X")).thenReturn(List.of(9L));
        assertThrows(IsbnDuplicadoException.class, () -> new LibroGestionService(libros).crear(datos("123-456 789x")));
        verify(libros, never()).saveAndFlush(any());
    }

    @Test void editarPermiteIsbnPropioYActualizaDatos() {
        Libro l = libro();
        when(libros.findById(1L)).thenReturn(Optional.of(l));
        when(libros.findIdsByIsbnNormalizado("123456789X")).thenReturn(List.of(1L));
        when(libros.saveAndFlush(l)).thenReturn(l);
        new LibroGestionService(libros).editar(1L, datos("123-456 789x"));
        assertEquals("Titulo", l.getTitulo());
        verify(libros).saveAndFlush(l);
    }

    @Test void retirarLibroEsLogicoEIdempotente() {
        Libro l = libro(); when(libros.findById(1L)).thenReturn(Optional.of(l));
        var servicio = new LibroGestionService(libros);
        servicio.retirar(1L); var fecha = l.getFechaRetirada(); servicio.retirar(1L);
        assertTrue(l.isRetirado()); assertNotNull(fecha); assertEquals(fecha, l.getFechaRetirada());
        verify(libros, never()).delete(any(Libro.class));
    }

    @Test void crearEjemplarAsociaLibroYEstado() {
        Libro l = libro(); when(libros.findById(1L)).thenReturn(Optional.of(l));
        when(ejemplares.saveAndFlush(any(Ejemplar.class))).thenAnswer(i -> i.getArgument(0));
        new EjemplarGestionService(libros, ejemplares, prestamos).crear(1L, new EjemplarRequest("E1", EstadoEjemplar.DISPONIBLE));
        ArgumentCaptor<Ejemplar> captor = ArgumentCaptor.forClass(Ejemplar.class);
        verify(ejemplares).saveAndFlush(captor.capture());
        assertSame(l, captor.getValue().getLibro()); assertTrue(captor.getValue().isPrestable());
    }

    @Test void editarNoPuedeOcultarPrestamoActivo() {
        Ejemplar e = ejemplar(libro());
        when(ejemplares.findByIdAndLibroId(2L, 1L)).thenReturn(Optional.of(e));
        when(prestamos.tienePrestamoActivo(2L)).thenReturn(true);
        assertThrows(EjemplarConPrestamoActivoException.class, () -> new EjemplarGestionService(libros, ejemplares, prestamos)
            .editar(1L, 2L, new EjemplarRequest("CAMBIADO", EstadoEjemplar.DISPONIBLE)));
        assertEquals("E1", e.getCodigo()); assertTrue(e.isActivo());
        verify(ejemplares, never()).saveAndFlush(any());
    }

    @Test void editarCodigoPropioMantieneEstadoPrestado() {
        Ejemplar e = ejemplar(libro()); e.prestar();
        when(ejemplares.findByIdAndLibroId(2L, 1L)).thenReturn(Optional.of(e));
        when(ejemplares.findByCodigo("E1")).thenReturn(Optional.of(e));
        when(ejemplares.saveAndFlush(e)).thenReturn(e);
        new EjemplarGestionService(libros, ejemplares, prestamos).editar(1L, 2L, new EjemplarRequest("E1", EstadoEjemplar.PRESTADO));
        assertEquals(EstadoEjemplar.PRESTADO, e.getEstado());
    }

    @Test void retirarEjemplarConPrestamoSeRechazaSinCambios() {
        Ejemplar e = ejemplar(libro());
        when(ejemplares.findByIdAndLibroId(2L, 1L)).thenReturn(Optional.of(e));
        when(prestamos.tienePrestamoActivo(2L)).thenReturn(true);
        assertThrows(EjemplarConPrestamoActivoException.class, () -> new EjemplarGestionService(libros, ejemplares, prestamos).retirar(1L, 2L));
        assertTrue(e.isActivo()); assertEquals(EstadoEjemplar.DISPONIBLE, e.getEstado());
    }

    @Test void listarReflejaPrestamosRealesAunqueElEstadoDigaDisponible() {
        Ejemplar e = ejemplar(libro());
        when(libros.existsById(1L)).thenReturn(true);
        when(ejemplares.findByLibroIdOrderByIdAsc(1L)).thenReturn(List.of(e));
        when(prestamos.findEjemplarIdsConPrestamoActivo(List.of(2L))).thenReturn(Set.of(2L));
        var r = new EjemplarGestionService(libros, ejemplares, prestamos).listar(1L);
        assertEquals(1, r.total()); assertEquals(0, r.disponibles());
        assertEquals(EstadoEjemplar.PRESTADO, r.ejemplares().get(0).estado());
    }

    @Test void busquedaLimitaPaginaYOrdenaSinConsultarDisponibilidadVacia() {
        when(libros.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
        new LibroService(libros, ejemplares).buscar(null, null, VistaCatalogo.values()[0], PageRequest.of(0, 500, Sort.by("noPermitido")));
        ArgumentCaptor<Pageable> pagina = ArgumentCaptor.forClass(Pageable.class);
        verify(libros).findAll(any(Specification.class), pagina.capture());
        assertEquals(100, pagina.getValue().getPageSize());
        assertEquals(Sort.by("titulo").and(Sort.by("id")), pagina.getValue().getSort());
        verifyNoInteractions(ejemplares);
    }
}

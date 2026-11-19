package com.biblioteca.backend.prestamo;

import com.biblioteca.backend.catalogo.Ejemplar;
import com.biblioteca.backend.catalogo.EjemplarRepository;
import com.biblioteca.backend.catalogo.EstadoEjemplar;
import com.biblioteca.backend.catalogo.Libro;
import com.biblioteca.backend.catalogo.LibroRepository;
import com.biblioteca.backend.espera.EstadoSolicitudEspera;
import com.biblioteca.backend.espera.SolicitudEspera;
import com.biblioteca.backend.espera.SolicitudEsperaRepository;
import com.biblioteca.backend.usuario.Usuario;
import com.biblioteca.backend.usuario.UsuarioRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PrestamoServiceUnitTests {
    private static final long USUARIO_ID = 1L;
    private static final long LIBRO_ID = 2L;
    private static final long EJEMPLAR_ID = 3L;
    private static final long PRESTAMO_ID = 4L;

    @Mock PrestamoRepository prestamos;
    @Mock UsuarioRepository usuarios;
    @Mock LibroRepository libros;
    @Mock EjemplarRepository ejemplares;
    @Mock SolicitudEsperaRepository solicitudes;
    @InjectMocks PrestamoService servicio;

    private Usuario estudiante;
    private Libro libro;
    private Ejemplar ejemplar;
    private Prestamo prestamo;

    @BeforeEach
    void prepararEntidades() {
        estudiante = new Usuario();
        ReflectionTestUtils.setField(estudiante, "id", USUARIO_ID);
        libro = new Libro(null, "Libro", "Autor", null, null, null);
        ReflectionTestUtils.setField(libro, "id", LIBRO_ID);
        ejemplar = new Ejemplar(libro, "EJ-1");
        ReflectionTestUtils.setField(ejemplar, "id", EJEMPLAR_ID);
        prestamo = new Prestamo(ejemplar, estudiante);
        ReflectionTestUtils.setField(prestamo, "id", PRESTAMO_ID);
    }

    @Test
    void prestaConCuatroPrestamosActivos() {
        when(usuarios.findParaPrestamo(USUARIO_ID)).thenReturn(Optional.of(estudiante));
        when(prestamos.countByUsuarioIdAndEstado(USUARIO_ID, EstadoPrestamo.ACTIVO)).thenReturn(4L);
        when(libros.findParaPrestamo(LIBRO_ID)).thenReturn(Optional.of(libro));
        when(ejemplares.findPrestables(LIBRO_ID)).thenReturn(List.of(ejemplar));
        when(prestamos.saveAndFlush(any(Prestamo.class))).thenAnswer(inv -> inv.getArgument(0));

        PrestamoResponse respuesta = servicio.prestar(USUARIO_ID, LIBRO_ID);

        ArgumentCaptor<Prestamo> guardado = ArgumentCaptor.forClass(Prestamo.class);
        verify(prestamos).saveAndFlush(guardado.capture());
        assertEquals(estudiante, guardado.getValue().getUsuario());
        assertEquals(ejemplar, guardado.getValue().getEjemplar());
        assertEquals(EstadoPrestamo.ACTIVO, respuesta.estado());
        assertEquals(LIBRO_ID, respuesta.libroId());
        assertEquals("EJ-1", respuesta.ejemplarCodigo());
        assertEquals(respuesta.fechaPrestamo().plusDays(30), respuesta.fechaVencimiento());
        assertEquals(EstadoEjemplar.PRESTADO, ejemplar.getEstado());
    }

    @Test
    void rechazaConCincoPrestamosActivosSinGuardar() {
        when(usuarios.findParaPrestamo(USUARIO_ID)).thenReturn(Optional.of(estudiante));
        when(prestamos.countByUsuarioIdAndEstado(USUARIO_ID, EstadoPrestamo.ACTIVO)).thenReturn(5L);

        assertThrows(LimitePrestamosException.class, () -> servicio.prestar(USUARIO_ID, LIBRO_ID));
        verify(prestamos, never()).saveAndFlush(any());
        assertEquals(EstadoEjemplar.DISPONIBLE, ejemplar.getEstado());
    }

    @Test
    void rechazaPrestamosVencidosSinGuardar() {
        when(usuarios.findParaPrestamo(USUARIO_ID)).thenReturn(Optional.of(estudiante));
        when(prestamos.tienePrestamosVencidos(USUARIO_ID)).thenReturn(true);

        assertThrows(PrestamosVencidosException.class, () -> servicio.prestar(USUARIO_ID, LIBRO_ID));
        verify(prestamos, never()).saveAndFlush(any());
        assertEquals(EstadoEjemplar.DISPONIBLE, ejemplar.getEstado());
    }

    @Test
    void rechazaSiNoHayEjemplaresSinGuardar() {
        when(usuarios.findParaPrestamo(USUARIO_ID)).thenReturn(Optional.of(estudiante));
        when(libros.findParaPrestamo(LIBRO_ID)).thenReturn(Optional.of(libro));
        when(ejemplares.findPrestables(LIBRO_ID)).thenReturn(List.of());

        assertThrows(SinEjemplaresDisponiblesException.class, () -> servicio.prestar(USUARIO_ID, LIBRO_ID));
        verify(prestamos, never()).saveAndFlush(any());
        assertEquals(EstadoEjemplar.DISPONIBLE, ejemplar.getEstado());
    }

    @Test
    void devuelveElPrestamoYLiberaElEjemplar() {
        prepararDevolucion(prestamo, ejemplar);
        when(solicitudes.findByLibroIdAndEstadoOrderByFechaSolicitudAscIdAsc(LIBRO_ID, EstadoSolicitudEspera.PENDIENTE))
            .thenReturn(List.of());

        DevolucionResponse respuesta = servicio.devolver(USUARIO_ID, PRESTAMO_ID);

        assertEquals(PRESTAMO_ID, respuesta.id());
        assertEquals(EstadoPrestamo.DEVUELTO, respuesta.estado());
        assertNotNull(respuesta.fechaDevolucion());
        assertEquals(respuesta.fechaDevolucion(), prestamo.getFechaDevolucion());
        assertEquals(EstadoEjemplar.DISPONIBLE, ejemplar.getEstado());
        verify(prestamos).flush();
        verify(prestamos, never()).saveAndFlush(any());
    }

    @Test
    void rechazaPrestamoInexistenteSinModificar() {
        ejemplar.prestar();
        when(usuarios.findParaPrestamo(USUARIO_ID)).thenReturn(Optional.of(estudiante));
        when(prestamos.localizarPropio(PRESTAMO_ID, USUARIO_ID)).thenReturn(Optional.empty());

        assertThrows(PrestamoNoEncontradoException.class, () -> servicio.devolver(USUARIO_ID, PRESTAMO_ID));
        comprobarSinCambios();
        verifyNoInteractions(libros, ejemplares);
    }

    @Test
    void rechazaPrestamoDeOtroEstudianteSinModificar() {
        ejemplar.prestar();
        long otroId = 9L;
        Usuario otro = new Usuario();
        when(usuarios.findParaPrestamo(otroId)).thenReturn(Optional.of(otro));
        when(prestamos.localizarPropio(PRESTAMO_ID, otroId)).thenReturn(Optional.empty());

        assertThrows(PrestamoNoEncontradoException.class, () -> servicio.devolver(otroId, PRESTAMO_ID));
        comprobarSinCambios();
        verifyNoInteractions(libros, ejemplares);
    }

    @Test
    void rechazaPrestamoYaDevueltoSinModificar() {
        prestamo.devolver();
        var fechaOriginal = prestamo.getFechaDevolucion();
        prepararDevolucion(prestamo, ejemplar);

        assertThrows(PrestamoYaDevueltoException.class, () -> servicio.devolver(USUARIO_ID, PRESTAMO_ID));
        assertEquals(EstadoPrestamo.DEVUELTO, prestamo.getEstado());
        assertEquals(fechaOriginal, prestamo.getFechaDevolucion());
        assertEquals(EstadoEjemplar.PRESTADO, ejemplar.getEstado());
        verificarSinEscrituras();
    }

    @Test
    void rechazaEjemplarInconsistenteSinModificar() {
        Ejemplar otroEjemplar = new Ejemplar(libro, "EJ-2");
        ReflectionTestUtils.setField(otroEjemplar, "id", 99L);
        prepararDevolucion(prestamo, otroEjemplar);

        assertThrows(IllegalStateException.class, () -> servicio.devolver(USUARIO_ID, PRESTAMO_ID));
        comprobarSinCambios();
        assertEquals(EstadoEjemplar.PRESTADO, otroEjemplar.getEstado());
    }

    @Test
    void liberaEjemplarSiNingunEstudianteDeLaColaEsElegible() {
        prepararDevolucion(prestamo, ejemplar);
        Usuario inactivo = new Usuario();
        inactivo.setActivo(false);
        ReflectionTestUtils.setField(inactivo, "id", 10L);
        SolicitudEspera solicitud = new SolicitudEspera(libro, inactivo);
        when(solicitudes.findByLibroIdAndEstadoOrderByFechaSolicitudAscIdAsc(LIBRO_ID, EstadoSolicitudEspera.PENDIENTE))
            .thenReturn(List.of(solicitud));
        when(usuarios.findParaPrestamo(10L)).thenReturn(Optional.of(inactivo));

        servicio.devolver(USUARIO_ID, PRESTAMO_ID);

        assertEquals(EstadoPrestamo.DEVUELTO, prestamo.getEstado());
        assertEquals(EstadoEjemplar.DISPONIBLE, ejemplar.getEstado());
        assertEquals(EstadoSolicitudEspera.PENDIENTE, solicitud.getEstado());
        verify(prestamos, never()).saveAndFlush(any());
    }

    @Test
    void devuelveYAsignaAlPrimerEstudianteElegibleTrasUnoBloqueado() {
        prepararDevolucion(prestamo, ejemplar);
        Usuario bloqueado = new Usuario();
        Usuario elegible = new Usuario();
        ReflectionTestUtils.setField(bloqueado, "id", 10L);
        ReflectionTestUtils.setField(elegible, "id", 11L);
        SolicitudEspera primera = new SolicitudEspera(libro, bloqueado);
        SolicitudEspera segunda = new SolicitudEspera(libro, elegible);
        when(solicitudes.findByLibroIdAndEstadoOrderByFechaSolicitudAscIdAsc(LIBRO_ID, EstadoSolicitudEspera.PENDIENTE))
            .thenReturn(List.of(primera, segunda));
        when(usuarios.findParaPrestamo(10L)).thenReturn(Optional.of(bloqueado));
        when(usuarios.findParaPrestamo(11L)).thenReturn(Optional.of(elegible));
        when(prestamos.tienePrestamosVencidos(10L)).thenReturn(true);

        servicio.devolver(USUARIO_ID, PRESTAMO_ID);

        ArgumentCaptor<Prestamo> nuevo = ArgumentCaptor.forClass(Prestamo.class);
        verify(prestamos).saveAndFlush(nuevo.capture());
        assertEquals(elegible, nuevo.getValue().getUsuario());
        assertEquals(ejemplar, nuevo.getValue().getEjemplar());
        assertEquals(EstadoPrestamo.DEVUELTO, prestamo.getEstado());
        assertEquals(EstadoEjemplar.PRESTADO, ejemplar.getEstado());
        assertEquals(EstadoSolicitudEspera.PENDIENTE, primera.getEstado());
        assertEquals(EstadoSolicitudEspera.ASIGNADA, segunda.getEstado());
    }

    private void prepararDevolucion(Prestamo propio, Ejemplar localizado) {
        ejemplar.prestar();
        localizado.prestar();
        PrestamoRepository.UbicacionPrestamo ubicacion = new PrestamoRepository.UbicacionPrestamo() {
            public Long getLibroId() { return LIBRO_ID; }
            public Long getEjemplarId() { return EJEMPLAR_ID; }
        };
        when(usuarios.findParaPrestamo(USUARIO_ID)).thenReturn(Optional.of(estudiante));
        when(prestamos.localizarPropio(PRESTAMO_ID, USUARIO_ID)).thenReturn(Optional.of(ubicacion));
        when(libros.findParaDevolucion(LIBRO_ID)).thenReturn(Optional.of(libro));
        when(ejemplares.findByIdAndLibroId(EJEMPLAR_ID, LIBRO_ID)).thenReturn(Optional.of(localizado));
        when(prestamos.findPropioParaActualizar(PRESTAMO_ID, USUARIO_ID)).thenReturn(Optional.of(propio));
    }

    private void comprobarSinCambios() {
        assertEquals(EstadoPrestamo.ACTIVO, prestamo.getEstado());
        assertNull(prestamo.getFechaDevolucion());
        assertEquals(EstadoEjemplar.PRESTADO, ejemplar.getEstado());
        verificarSinEscrituras();
    }

    private void verificarSinEscrituras() {
        verify(prestamos, never()).flush();
        verify(prestamos, never()).saveAndFlush(any());
        verify(solicitudes, never()).findByLibroIdAndEstadoOrderByFechaSolicitudAscIdAsc(any(), any());
    }
}

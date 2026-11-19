package com.biblioteca.backend.prestamo;

import com.biblioteca.backend.catalogo.Ejemplar;
import com.biblioteca.backend.catalogo.EjemplarRepository;
import com.biblioteca.backend.catalogo.Libro;
import com.biblioteca.backend.catalogo.LibroRepository;
import com.biblioteca.backend.espera.EstadoSolicitudEspera;
import com.biblioteca.backend.espera.SolicitudEsperaRepository;
import com.biblioteca.backend.usuario.Usuario;
import com.biblioteca.backend.usuario.UsuarioRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RenovacionServiceUnitTests {
    private static final long USUARIO_ID = 1L;
    private static final long LIBRO_ID = 2L;
    private static final long PRESTAMO_ID = 3L;
    private static final LocalDateTime VENCIMIENTO_FUTURO = LocalDateTime.of(2099, 6, 15, 12, 0);

    @Mock PrestamoRepository prestamos;
    @Mock UsuarioRepository usuarios;
    @Mock LibroRepository libros;
    @Mock EjemplarRepository ejemplares;
    @Mock SolicitudEsperaRepository solicitudes;
    @InjectMocks PrestamoService servicio;

    private Usuario estudiante;
    private Libro libro;
    private Prestamo prestamo;

    @BeforeEach
    void preparar() {
        estudiante = new Usuario();
        libro = new Libro(null, "Libro", "Autor", null, null, null);
        prestamo = new Prestamo(new Ejemplar(libro, "EJ-1"), estudiante);
        ReflectionTestUtils.setField(prestamo, "id", PRESTAMO_ID);
        ReflectionTestUtils.setField(prestamo, "fechaVencimiento", VENCIMIENTO_FUTURO);
    }

    @Test
    void primeraRenovacionSumaTreintaDiasAlVencimientoAnterior() {
        prepararPrestamoPropio();
        RenovacionResponse resultado = servicio.renovar(USUARIO_ID, PRESTAMO_ID);
        comprobarRenovacion(resultado, VENCIMIENTO_FUTURO.plusDays(30), 1);
        verify(solicitudes).existsByLibroIdAndEstado(LIBRO_ID, EstadoSolicitudEspera.PENDIENTE);
        verifyNoInteractions(ejemplares);
    }

    @Test
    void segundaRenovacionSumaTreintaDiasAlUltimoVencimiento() {
        prestamo.renovar();
        LocalDateTime anterior = prestamo.getFechaVencimiento();
        prepararPrestamoPropio();
        RenovacionResponse resultado = servicio.renovar(USUARIO_ID, PRESTAMO_ID);
        comprobarRenovacion(resultado, anterior.plusDays(30), 2);
    }

    @Test
    void rechazaTerceraRenovacionSinModificarElPrestamo() {
        prestamo.renovar();
        prestamo.renovar();
        prepararPrestamoPropio();
        comprobarRechazo("LIMITE_RENOVACIONES");
    }

    @Test
    void rechazaPrestamoDevueltoSinModificarlo() {
        prestamo.devolver();
        LocalDateTime devolucion = prestamo.getFechaDevolucion();
        prepararPrestamoPropio();
        comprobarRechazo("PRESTAMO_NO_ACTIVO");
        assertEquals(EstadoPrestamo.DEVUELTO, prestamo.getEstado());
        assertEquals(devolucion, prestamo.getFechaDevolucion());
    }

    @Test
    void rechazaPrestamoVencidoSinModificarlo() {
        ReflectionTestUtils.setField(prestamo, "fechaVencimiento", LocalDateTime.of(2000, 1, 1, 0, 0));
        prepararPrestamoPropio();
        comprobarRechazo("PRESTAMO_VENCIDO");
    }

    @Test
    void rechazaPrestamoInexistenteSinModificarlo() {
        when(usuarios.findParaPrestamo(USUARIO_ID)).thenReturn(Optional.of(estudiante));
        when(prestamos.localizarPropio(PRESTAMO_ID, USUARIO_ID)).thenReturn(Optional.empty());
        comprobarNoEncontrado(USUARIO_ID);
        verifyNoInteractions(libros, ejemplares);
    }

    @Test
    void rechazaPrestamoAjenoSinModificarlo() {
        long otroId = 9L;
        when(usuarios.findParaPrestamo(otroId)).thenReturn(Optional.of(new Usuario()));
        when(prestamos.localizarPropio(PRESTAMO_ID, otroId)).thenReturn(Optional.empty());
        comprobarNoEncontrado(otroId);
        verify(prestamos).localizarPropio(PRESTAMO_ID, otroId);
        verifyNoInteractions(libros, ejemplares);
    }

    @Test
    void rechazaEstudianteInactivoSinModificarPrestamo() {
        estudiante.setActivo(false);
        prepararPrestamoPropio();
        comprobarRechazo("ESTUDIANTE_BLOQUEADO");
    }

    @Test
    void rechazaEstudianteConOtroPrestamoVencidoSinModificarPrestamo() {
        prepararPrestamoPropio();
        when(prestamos.tienePrestamosVencidos(USUARIO_ID)).thenReturn(true);
        comprobarRechazo("ESTUDIANTE_BLOQUEADO");
    }

    @Test
    void rechazaSolicitudPendienteSinEjemplaresLibres() {
        prepararPrestamoPropio();
        when(solicitudes.existsByLibroIdAndEstado(LIBRO_ID, EstadoSolicitudEspera.PENDIENTE)).thenReturn(true);
        when(ejemplares.findPrestables(LIBRO_ID)).thenReturn(List.of());
        comprobarRechazo("LISTA_ESPERA_PENDIENTE");
        verify(ejemplares).findPrestables(LIBRO_ID);
    }

    @Test
    void permiteSolicitudPendienteSiHayOtroEjemplarLibre() {
        prepararPrestamoPropio();
        Ejemplar libre = new Ejemplar(libro, "EJ-2");
        when(solicitudes.existsByLibroIdAndEstado(LIBRO_ID, EstadoSolicitudEspera.PENDIENTE)).thenReturn(true);
        when(ejemplares.findPrestables(LIBRO_ID)).thenReturn(List.of(libre));

        RenovacionResponse resultado = servicio.renovar(USUARIO_ID, PRESTAMO_ID);

        comprobarRenovacion(resultado, VENCIMIENTO_FUTURO.plusDays(30), 1);
        assertTrue(libre.isPrestable());
        verify(ejemplares).findPrestables(LIBRO_ID);
    }

    private void prepararPrestamoPropio() {
        PrestamoRepository.UbicacionPrestamo ubicacion = new PrestamoRepository.UbicacionPrestamo() {
            public Long getLibroId() { return LIBRO_ID; }
            public Long getEjemplarId() { return 4L; }
        };
        when(usuarios.findParaPrestamo(USUARIO_ID)).thenReturn(Optional.of(estudiante));
        when(prestamos.localizarPropio(PRESTAMO_ID, USUARIO_ID)).thenReturn(Optional.of(ubicacion));
        when(libros.findParaDevolucion(LIBRO_ID)).thenReturn(Optional.of(libro));
        when(prestamos.findPropioParaActualizar(PRESTAMO_ID, USUARIO_ID)).thenReturn(Optional.of(prestamo));
    }

    private void comprobarRenovacion(RenovacionResponse resultado, LocalDateTime fecha, int contador) {
        assertEquals(PRESTAMO_ID, resultado.id());
        assertEquals(EstadoPrestamo.ACTIVO, resultado.estado());
        assertEquals(contador, resultado.numeroRenovaciones());
        assertEquals(fecha, resultado.fechaVencimiento());
        assertEquals(contador, prestamo.getNumeroRenovaciones());
        assertEquals(fecha, prestamo.getFechaVencimiento());
    }

    private void comprobarRechazo(String codigo) {
        LocalDateTime fecha = prestamo.getFechaVencimiento();
        int contador = prestamo.getNumeroRenovaciones();
        EstadoPrestamo estado = prestamo.getEstado();
        LocalDateTime devolucion = prestamo.getFechaDevolucion();
        RenovacionRechazadaException error = assertThrows(RenovacionRechazadaException.class,
            () -> servicio.renovar(USUARIO_ID, PRESTAMO_ID));
        assertEquals(codigo, error.getCodigo());
        comprobarSinCambios(fecha, contador, estado, devolucion);
    }

    private void comprobarNoEncontrado(long usuarioId) {
        LocalDateTime fecha = prestamo.getFechaVencimiento();
        int contador = prestamo.getNumeroRenovaciones();
        assertThrows(PrestamoNoEncontradoException.class, () -> servicio.renovar(usuarioId, PRESTAMO_ID));
        comprobarSinCambios(fecha, contador, EstadoPrestamo.ACTIVO, null);
    }

    private void comprobarSinCambios(LocalDateTime fecha, int contador, EstadoPrestamo estado, LocalDateTime devolucion) {
        assertEquals(fecha, prestamo.getFechaVencimiento());
        assertEquals(contador, prestamo.getNumeroRenovaciones());
        assertEquals(estado, prestamo.getEstado());
        assertEquals(devolucion, prestamo.getFechaDevolucion());
        verify(prestamos, never()).save(any());
        verify(prestamos, never()).saveAndFlush(any());
        verify(prestamos, never()).flush();
    }
}

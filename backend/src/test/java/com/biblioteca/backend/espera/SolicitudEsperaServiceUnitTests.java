package com.biblioteca.backend.espera;

import com.biblioteca.backend.catalogo.*;
import com.biblioteca.backend.usuario.*;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SolicitudEsperaServiceUnitTests {
    @Mock SolicitudEsperaRepository solicitudes;
    @Mock UsuarioRepository usuarios;
    @Mock LibroRepository libros;
    @Mock EjemplarRepository ejemplares;
    @InjectMocks SolicitudEsperaService servicio;
    final Usuario estudiante = new Usuario();
    final Libro libro = new Libro(null, "Libro", "Autor", null, null, null);

    private void prepararAlta() {
        when(usuarios.findParaPrestamo(1L)).thenReturn(Optional.of(estudiante));
        when(libros.findParaPrestamo(2L)).thenReturn(Optional.of(libro));
    }

    @Test void altaPendienteGuardaLaSolicitudDelEstudiante() {
        prepararAlta();
        var resultado = servicio.apuntarse(1L, 2L);
        ArgumentCaptor<SolicitudEspera> captor = ArgumentCaptor.forClass(SolicitudEspera.class);
        verify(solicitudes).saveAndFlush(captor.capture());
        assertSame(estudiante, captor.getValue().getUsuario());
        assertSame(libro, captor.getValue().getLibro());
        assertEquals(EstadoSolicitudEspera.PENDIENTE, resultado.estado());
    }

    @Test void duplicadoSeRechazaSinGuardar() {
        prepararAlta();
        when(solicitudes.existsByLibroIdAndUsuarioIdAndEstado(2L, 1L, EstadoSolicitudEspera.PENDIENTE)).thenReturn(true);
        assertEquals("SOLICITUD_DUPLICADA", assertThrows(SolicitudEsperaRechazadaException.class, () -> servicio.apuntarse(1L, 2L)).getCodigo());
        verify(solicitudes, never()).saveAndFlush(any());
        verifyNoInteractions(ejemplares);
    }

    @Test void ejemplarDisponibleImpideApuntarse() {
        prepararAlta();
        when(ejemplares.findPrestables(2L)).thenReturn(List.of(new Ejemplar(libro, "E1")));
        assertEquals("EJEMPLARES_DISPONIBLES", assertThrows(SolicitudEsperaRechazadaException.class, () -> servicio.apuntarse(1L, 2L)).getCodigo());
        verify(solicitudes, never()).saveAndFlush(any());
    }

    @Test void estudianteInactivoNoPuedeApuntarse() {
        estudiante.setActivo(false);
        when(usuarios.findParaPrestamo(1L)).thenReturn(Optional.of(estudiante));
        assertEquals("ESTUDIANTE_INACTIVO", assertThrows(SolicitudEsperaRechazadaException.class, () -> servicio.apuntarse(1L, 2L)).getCodigo());
        verify(solicitudes, never()).saveAndFlush(any());
    }

    @Test void cancelarCambiaUnaSolicitudPropiaPendiente() {
        SolicitudEspera solicitud = new SolicitudEspera(libro, estudiante);
        when(solicitudes.findByIdAndUsuarioId(3L, 1L)).thenReturn(Optional.of(solicitud));
        assertEquals(EstadoSolicitudEspera.CANCELADA, servicio.cancelar(1L, 3L).estado());
        assertEquals(EstadoSolicitudEspera.CANCELADA, solicitud.getEstado());
    }

    @Test void cancelarInexistenteOAjenaNoLaModifica() {
        SolicitudEspera ajena = new SolicitudEspera(libro, estudiante);
        when(solicitudes.findByIdAndUsuarioId(3L, 9L)).thenReturn(Optional.empty());
        assertThrows(SolicitudEsperaNoEncontradaException.class, () -> servicio.cancelar(9L, 3L));
        assertEquals(EstadoSolicitudEspera.PENDIENTE, ajena.getEstado());
        verify(solicitudes, never()).saveAndFlush(any());
    }

    @Test void cancelarAsignadaNoCambiaElEstado() {
        SolicitudEspera solicitud = new SolicitudEspera(libro, estudiante);
        solicitud.asignar();
        when(solicitudes.findByIdAndUsuarioId(3L, 1L)).thenReturn(Optional.of(solicitud));
        assertEquals("SOLICITUD_ASIGNADA", assertThrows(SolicitudEsperaRechazadaException.class, () -> servicio.cancelar(1L, 3L)).getCodigo());
        assertEquals(EstadoSolicitudEspera.ASIGNADA, solicitud.getEstado());
    }
}

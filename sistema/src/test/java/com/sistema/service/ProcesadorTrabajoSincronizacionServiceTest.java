package com.sistema.service;

import com.sistema.dto.ResultadoImportacionCanal;
import com.sistema.dto.ResultadoPublicacionLote;
import com.sistema.model.CanalVenta;
import com.sistema.model.EstadoTrabajoSincronizacion;
import com.sistema.model.TrabajoSincronizacion;
import com.sistema.repository.TrabajoSincronizacionRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProcesadorTrabajoSincronizacionServiceTest {

    @Test
    void guardaResultadoCompletadoConErroresSinPropagarlosAlHttp() {
        TrabajoSincronizacionRepository repository = mock(TrabajoSincronizacionRepository.class);
        SincronizacionCanalesService sincronizacion = mock(SincronizacionCanalesService.class);
        TrabajoSincronizacion trabajo = trabajo();
        when(repository.findById(10L)).thenReturn(Optional.of(trabajo));

        ResultadoImportacionCanal importacion = new ResultadoImportacionCanal();
        importacion.creado(1L);
        ResultadoPublicacionLote publicacion = new ResultadoPublicacionLote();
        publicacion.exito();
        publicacion.error("SKU-2 / WooCommerce: error de prueba");
        when(sincronizacion.sincronizar(CanalVenta.MERCADO_LIBRE, List.of(CanalVenta.WOOCOMMERCE)))
                .thenReturn(new SincronizacionCanalesService.Resultado(importacion, publicacion));
        ProcesadorTrabajoSincronizacionService procesador =
                new ProcesadorTrabajoSincronizacionService(
                        repository, sincronizacion, mock(PublicacionService.class));

        procesador.ejecutar(10L, 3L, CanalVenta.MERCADO_LIBRE, List.of(CanalVenta.WOOCOMMERCE));

        assertEquals(EstadoTrabajoSincronizacion.COMPLETADA_CON_ERRORES, trabajo.getEstado());
        assertNotNull(trabajo.getIniciadoEn());
        assertNotNull(trabajo.getFinalizadoEn());
        assertTrue(trabajo.getResumen().contains("1 productos creados"));
        assertEquals("SKU-2 / WooCommerce: error de prueba", trabajo.getDetalle());
        verify(repository).saveAndFlush(trabajo);
        verify(repository).save(trabajo);
    }

    @Test
    void procesaPublicacionSeleccionadaYGuardaElResultadoEnElTrabajo() {
        TrabajoSincronizacionRepository repository = mock(TrabajoSincronizacionRepository.class);
        SincronizacionCanalesService sincronizacion = mock(SincronizacionCanalesService.class);
        PublicacionService publicacionService = mock(PublicacionService.class);
        TrabajoSincronizacion trabajo = trabajo();
        when(repository.findById(10L)).thenReturn(Optional.of(trabajo));
        ResultadoPublicacionLote resultado = new ResultadoPublicacionLote();
        resultado.exito();
        resultado.exito();
        resultado.error("CAMP-001 / WooCommerce: error de prueba");
        when(publicacionService.publicar(List.of(4L, 5L, 6L), List.of(CanalVenta.WOOCOMMERCE)))
                .thenReturn(resultado);
        ProcesadorTrabajoSincronizacionService procesador =
                new ProcesadorTrabajoSincronizacionService(
                        repository, sincronizacion, publicacionService);

        procesador.ejecutarPublicacion(10L, 3L, List.of(4L, 5L, 6L),
                List.of(CanalVenta.WOOCOMMERCE));

        assertEquals(EstadoTrabajoSincronizacion.COMPLETADA_CON_ERRORES, trabajo.getEstado());
        assertNotNull(trabajo.getIniciadoEn());
        assertNotNull(trabajo.getFinalizadoEn());
        assertEquals("Publicaciones procesadas correctamente: 2.", trabajo.getResumen());
        assertEquals("CAMP-001 / WooCommerce: error de prueba", trabajo.getDetalle());
        verify(repository).saveAndFlush(trabajo);
        verify(repository).save(trabajo);
    }

    @Test
    void registraComoErrorUnaExcepcionGeneral() {
        TrabajoSincronizacionRepository repository = mock(TrabajoSincronizacionRepository.class);
        SincronizacionCanalesService sincronizacion = mock(SincronizacionCanalesService.class);
        TrabajoSincronizacion trabajo = trabajo();
        when(repository.findById(10L)).thenReturn(Optional.of(trabajo));
        when(sincronizacion.sincronizar(CanalVenta.MERCADO_LIBRE, List.of(CanalVenta.WOOCOMMERCE)))
                .thenThrow(new IllegalStateException("WooCommerce no responde"));
        ProcesadorTrabajoSincronizacionService procesador =
                new ProcesadorTrabajoSincronizacionService(
                        repository, sincronizacion, mock(PublicacionService.class));

        procesador.ejecutar(10L, 3L, CanalVenta.MERCADO_LIBRE, List.of(CanalVenta.WOOCOMMERCE));

        assertEquals(EstadoTrabajoSincronizacion.ERROR, trabajo.getEstado());
        assertEquals("WooCommerce no responde", trabajo.getDetalle());
        assertNotNull(trabajo.getFinalizadoEn());
    }

    private TrabajoSincronizacion trabajo() {
        TrabajoSincronizacion trabajo = new TrabajoSincronizacion();
        trabajo.setId(10L);
        trabajo.setTenantId(3L);
        trabajo.setOrigen(CanalVenta.MERCADO_LIBRE);
        trabajo.setDestinos(CanalVenta.WOOCOMMERCE.name());
        trabajo.setEstado(EstadoTrabajoSincronizacion.PENDIENTE);
        trabajo.setCreadoEn(LocalDateTime.now());
        return trabajo;
    }
}

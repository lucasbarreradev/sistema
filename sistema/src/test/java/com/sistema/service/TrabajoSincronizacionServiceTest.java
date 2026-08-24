package com.sistema.service;

import com.sistema.model.CanalVenta;
import com.sistema.model.EstadoTrabajoSincronizacion;
import com.sistema.model.TipoTrabajoSincronizacion;
import com.sistema.model.TrabajoSincronizacion;
import com.sistema.repository.TrabajoSincronizacionRepository;
import com.sistema.tenant.TenantContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TrabajoSincronizacionServiceTest {

    @Test
    void creaTrabajoDelTenantYLoEnviaAlProcesador() {
        TrabajoSincronizacionRepository repository = mock(TrabajoSincronizacionRepository.class);
        ProcesadorTrabajoSincronizacionService procesador = mock(ProcesadorTrabajoSincronizacionService.class);
        when(repository.findByEstadoIn(anyCollection())).thenReturn(List.of());
        when(repository.existsByEstadoIn(anyCollection())).thenReturn(false);
        when(repository.save(any(TrabajoSincronizacion.class))).thenAnswer(invocacion -> {
            TrabajoSincronizacion trabajo = invocacion.getArgument(0);
            trabajo.setId(25L);
            return trabajo;
        });
        TrabajoSincronizacionService service = new TrabajoSincronizacionService(repository, procesador);

        TrabajoSincronizacion trabajo;
        try (TenantContext.Scope ignored = TenantContext.use(8L)) {
            trabajo = service.iniciar(CanalVenta.MERCADO_LIBRE,
                    List.of(CanalVenta.MERCADO_LIBRE, CanalVenta.WOOCOMMERCE, CanalVenta.WOOCOMMERCE));
        }

        assertEquals(25L, trabajo.getId());
        assertEquals(8L, trabajo.getTenantId());
        assertEquals(CanalVenta.MERCADO_LIBRE, trabajo.getOrigen());
        assertEquals(TipoTrabajoSincronizacion.SINCRONIZACION_CANALES, trabajo.getTipoTrabajo());
        assertEquals("WOOCOMMERCE", trabajo.getDestinos());
        assertEquals(EstadoTrabajoSincronizacion.PENDIENTE, trabajo.getEstado());
        verify(procesador).ejecutar(25L, 8L, CanalVenta.MERCADO_LIBRE, List.of(CanalVenta.WOOCOMMERCE));
    }

    @Test
    void impideDosSincronizacionesActivasParaElMismoTenant() {
        TrabajoSincronizacionRepository repository = mock(TrabajoSincronizacionRepository.class);
        ProcesadorTrabajoSincronizacionService procesador = mock(ProcesadorTrabajoSincronizacionService.class);
        when(repository.findByEstadoIn(anyCollection())).thenReturn(List.of());
        when(repository.existsByEstadoIn(anyCollection())).thenReturn(true);
        TrabajoSincronizacionService service = new TrabajoSincronizacionService(repository, procesador);

        IllegalStateException error;
        try (TenantContext.Scope ignored = TenantContext.use(8L)) {
            error = assertThrows(IllegalStateException.class, () -> service.iniciar(
                    CanalVenta.MERCADO_LIBRE, List.of(CanalVenta.WOOCOMMERCE)));
        }

        assertTrue(error.getMessage().contains("Ya hay un trabajo"));
        verify(repository, never()).save(any());
        verifyNoInteractions(procesador);
    }

    @Test
    void creaPublicacionSeleccionadaEnSegundoPlanoSinDuplicarIdsNiCanales() {
        TrabajoSincronizacionRepository repository = mock(TrabajoSincronizacionRepository.class);
        ProcesadorTrabajoSincronizacionService procesador = mock(ProcesadorTrabajoSincronizacionService.class);
        when(repository.findByEstadoIn(anyCollection())).thenReturn(List.of());
        when(repository.existsByEstadoIn(anyCollection())).thenReturn(false);
        when(repository.save(any(TrabajoSincronizacion.class))).thenAnswer(invocacion -> {
            TrabajoSincronizacion trabajo = invocacion.getArgument(0);
            trabajo.setId(31L);
            return trabajo;
        });
        TrabajoSincronizacionService service = new TrabajoSincronizacionService(repository, procesador);

        TrabajoSincronizacion trabajo;
        try (TenantContext.Scope ignored = TenantContext.use(8L)) {
            trabajo = service.iniciarPublicacion(
                    List.of(4L, 4L, 5L),
                    List.of(CanalVenta.WOOCOMMERCE, CanalVenta.WOOCOMMERCE));
        }

        assertEquals(31L, trabajo.getId());
        assertEquals(8L, trabajo.getTenantId());
        assertEquals(TipoTrabajoSincronizacion.PUBLICACION_SELECCIONADA, trabajo.getTipoTrabajo());
        assertEquals("Sistema \u2192 WooCommerce", trabajo.getFlujoDescripcion());
        assertEquals("WOOCOMMERCE", trabajo.getDestinos());
        assertEquals(EstadoTrabajoSincronizacion.PENDIENTE, trabajo.getEstado());
        verify(procesador).ejecutarPublicacion(
                31L, 8L, List.of(4L, 5L), List.of(CanalVenta.WOOCOMMERCE));
    }

    @Test
    void creaImportacionCompletaEnSegundoPlano() {
        TrabajoSincronizacionRepository repository = mock(TrabajoSincronizacionRepository.class);
        ProcesadorTrabajoSincronizacionService procesador = mock(ProcesadorTrabajoSincronizacionService.class);
        when(repository.findByEstadoIn(anyCollection())).thenReturn(List.of());
        when(repository.existsByEstadoIn(anyCollection())).thenReturn(false);
        when(repository.save(any(TrabajoSincronizacion.class))).thenAnswer(invocacion -> {
            TrabajoSincronizacion trabajo = invocacion.getArgument(0);
            trabajo.setId(44L);
            return trabajo;
        });
        TrabajoSincronizacionService service = new TrabajoSincronizacionService(repository, procesador);

        TrabajoSincronizacion trabajo;
        try (TenantContext.Scope ignored = TenantContext.use(8L)) {
            trabajo = service.iniciarImportacionCompleta(CanalVenta.MERCADO_LIBRE);
        }

        assertEquals(TipoTrabajoSincronizacion.IMPORTACION_COMPLETA, trabajo.getTipoTrabajo());
        assertEquals("Mercado Libre → Sistema", trabajo.getFlujoDescripcion());
        assertEquals(EstadoTrabajoSincronizacion.PENDIENTE, trabajo.getEstado());
        verify(procesador).ejecutarImportacionCompleta(
                44L, 8L, CanalVenta.MERCADO_LIBRE, false);
    }

    @Test
    void propagaLaSeleccionDePublicacionesInactivasALaImportacionCompleta() {
        TrabajoSincronizacionRepository repository = mock(TrabajoSincronizacionRepository.class);
        ProcesadorTrabajoSincronizacionService procesador = mock(ProcesadorTrabajoSincronizacionService.class);
        when(repository.findByEstadoIn(anyCollection())).thenReturn(List.of());
        when(repository.existsByEstadoIn(anyCollection())).thenReturn(false);
        when(repository.save(any(TrabajoSincronizacion.class))).thenAnswer(invocacion -> {
            TrabajoSincronizacion trabajo = invocacion.getArgument(0);
            trabajo.setId(45L);
            return trabajo;
        });
        TrabajoSincronizacionService service = new TrabajoSincronizacionService(repository, procesador);

        try (TenantContext.Scope ignored = TenantContext.use(8L)) {
            service.iniciarImportacionCompleta(CanalVenta.MERCADO_LIBRE, true);
        }

        verify(procesador).ejecutarImportacionCompleta(
                45L, 8L, CanalVenta.MERCADO_LIBRE, true);
    }

    @Test
    void creaImportacionDeUltimasPublicacionesConCantidadYCategoria() {
        TrabajoSincronizacionRepository repository = mock(TrabajoSincronizacionRepository.class);
        ProcesadorTrabajoSincronizacionService procesador = mock(ProcesadorTrabajoSincronizacionService.class);
        when(repository.findByEstadoIn(anyCollection())).thenReturn(List.of());
        when(repository.existsByEstadoIn(anyCollection())).thenReturn(false);
        when(repository.save(any(TrabajoSincronizacion.class))).thenAnswer(invocacion -> {
            TrabajoSincronizacion trabajo = invocacion.getArgument(0);
            trabajo.setId(46L);
            return trabajo;
        });
        TrabajoSincronizacionService service = new TrabajoSincronizacionService(repository, procesador);

        TrabajoSincronizacion trabajo;
        try (TenantContext.Scope ignored = TenantContext.use(8L)) {
            trabajo = service.iniciarImportacionFiltradaMercadoLibre(
                    5, "  neumaticos  ", false);
        }

        assertEquals(TipoTrabajoSincronizacion.IMPORTACION_FILTRADA, trabajo.getTipoTrabajo());
        assertEquals("Mercado Libre → Sistema", trabajo.getFlujoDescripcion());
        assertTrue(trabajo.getResumen().contains("últimas 5"));
        verify(procesador).ejecutarImportacionFiltradaMercadoLibre(
                46L, 8L, 5, "neumaticos", false);
    }

    @Test
    void solicitaCancelarUnTrabajoActivoDelTenantActual() {
        TrabajoSincronizacionRepository repository = mock(TrabajoSincronizacionRepository.class);
        ProcesadorTrabajoSincronizacionService procesador = mock(ProcesadorTrabajoSincronizacionService.class);
        TrabajoSincronizacion trabajo = new TrabajoSincronizacion();
        trabajo.setId(61L);
        trabajo.setTenantId(8L);
        trabajo.setEstado(EstadoTrabajoSincronizacion.PROCESANDO);
        when(repository.findById(61L)).thenReturn(java.util.Optional.of(trabajo));
        TrabajoSincronizacionService service = new TrabajoSincronizacionService(repository, procesador);

        try (TenantContext.Scope ignored = TenantContext.use(8L)) {
            service.solicitarCancelacion(61L);
        }

        assertTrue(trabajo.isCancelacionSolicitada());
        assertTrue(trabajo.getResumen().contains("Cancelación solicitada"));
        verify(repository).save(trabajo);
    }

    @Test
    void noPermiteCancelarUnTrabajoDeOtroTenant() {
        TrabajoSincronizacionRepository repository = mock(TrabajoSincronizacionRepository.class);
        TrabajoSincronizacion trabajo = new TrabajoSincronizacion();
        trabajo.setId(62L);
        trabajo.setTenantId(9L);
        trabajo.setEstado(EstadoTrabajoSincronizacion.PROCESANDO);
        when(repository.findById(62L)).thenReturn(java.util.Optional.of(trabajo));
        TrabajoSincronizacionService service = new TrabajoSincronizacionService(
                repository, mock(ProcesadorTrabajoSincronizacionService.class));

        try (TenantContext.Scope ignored = TenantContext.use(8L)) {
            assertThrows(IllegalArgumentException.class, () -> service.solicitarCancelacion(62L));
        }

        verify(repository, never()).save(any());
    }
}

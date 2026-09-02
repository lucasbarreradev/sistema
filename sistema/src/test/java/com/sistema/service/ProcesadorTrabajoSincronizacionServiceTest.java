package com.sistema.service;

import com.sistema.dto.ResultadoImportacionCanal;
import com.sistema.dto.ResultadoPublicacionLote;
import com.sistema.dto.ProductoCanalImportado;
import com.sistema.model.CanalVenta;
import com.sistema.model.EstadoTrabajoSincronizacion;
import com.sistema.model.TrabajoSincronizacion;
import com.sistema.repository.TrabajoSincronizacionRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProcesadorTrabajoSincronizacionServiceTest {

    @Test
    void preparaLaRevisionCompletaAntesDeMarcarElTrabajoTerminado() {
        TrabajoSincronizacionRepository repository = mock(TrabajoSincronizacionRepository.class);
        RevisionPublicacionService revision = mock(RevisionPublicacionService.class);
        TrabajoSincronizacion trabajo = trabajo();
        when(repository.findById(10L)).thenReturn(Optional.of(trabajo));
        when(revision.prepararEnSegundoPlano(eq(10L), eq(List.of(4L, 5L)),
                eq(List.of(CanalVenta.MERCADO_LIBRE)),
                any(java.util.function.BooleanSupplier.class))).thenReturn(2);
        ProcesadorTrabajoSincronizacionService procesador =
                new ProcesadorTrabajoSincronizacionService(
                        repository, mock(SincronizacionCanalesService.class),
                        mock(PublicacionService.class),
                        mock(ImportacionCanalService.class), revision);

        procesador.ejecutarPreparacionPublicacion(
                10L, 3L, List.of(4L, 5L),
                List.of(CanalVenta.MERCADO_LIBRE));

        assertEquals(EstadoTrabajoSincronizacion.COMPLETADA, trabajo.getEstado());
        assertEquals("Revisión preparada: 2 producto(s) analizados.",
                trabajo.getResumen());
        assertNotNull(trabajo.getFinalizadoEn());
        verify(repository).saveAndFlush(trabajo);
        verify(repository).save(trabajo);
    }

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
        when(sincronizacion.sincronizar(eq(CanalVenta.MERCADO_LIBRE),
                eq(List.of(CanalVenta.WOOCOMMERCE)), any(java.util.function.BooleanSupplier.class)))
                .thenReturn(new SincronizacionCanalesService.Resultado(importacion, publicacion));
        ProcesadorTrabajoSincronizacionService procesador =
                new ProcesadorTrabajoSincronizacionService(
                        repository, sincronizacion, mock(PublicacionService.class),
                        mock(ImportacionCanalService.class),
                        mock(RevisionPublicacionService.class));

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
        when(publicacionService.publicar(eq(List.of(4L, 5L, 6L)),
                eq(List.of(CanalVenta.WOOCOMMERCE)), any(java.util.function.BooleanSupplier.class)))
                .thenReturn(resultado);
        ProcesadorTrabajoSincronizacionService procesador =
                new ProcesadorTrabajoSincronizacionService(
                        repository, sincronizacion, publicacionService,
                        mock(ImportacionCanalService.class),
                        mock(RevisionPublicacionService.class));

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
        when(sincronizacion.sincronizar(eq(CanalVenta.MERCADO_LIBRE),
                eq(List.of(CanalVenta.WOOCOMMERCE)), any(java.util.function.BooleanSupplier.class)))
                .thenThrow(new IllegalStateException("WooCommerce no responde"));
        ProcesadorTrabajoSincronizacionService procesador =
                new ProcesadorTrabajoSincronizacionService(
                        repository, sincronizacion, mock(PublicacionService.class),
                        mock(ImportacionCanalService.class),
                        mock(RevisionPublicacionService.class));

        procesador.ejecutar(10L, 3L, CanalVenta.MERCADO_LIBRE, List.of(CanalVenta.WOOCOMMERCE));

        assertEquals(EstadoTrabajoSincronizacion.ERROR, trabajo.getEstado());
        assertEquals("WooCommerce no responde", trabajo.getDetalle());
        assertNotNull(trabajo.getFinalizadoEn());
    }

    @Test
    void traerTodoImportaLaMismaDescarga() {
        TrabajoSincronizacionRepository repository = mock(TrabajoSincronizacionRepository.class);
        ImportacionCanalService importacion = mock(ImportacionCanalService.class);
        TrabajoSincronizacion trabajo = trabajo();
        when(repository.findById(10L)).thenReturn(Optional.of(trabajo));
        List<ProductoCanalImportado> productos = List.of(
                producto("MLA1", "SKU-1"), producto("MLA2", "SKU-2"));
        ResultadoImportacionCanal resultado = new ResultadoImportacionCanal();
        resultado.creado(1L);
        resultado.actualizado(2L);
        when(importacion.obtenerProductos(eq(CanalVenta.MERCADO_LIBRE), eq(false),
                any(java.util.function.BooleanSupplier.class))).thenReturn(productos);
        when(importacion.importar(eq(CanalVenta.MERCADO_LIBRE), eq(productos),
                any(java.util.function.BooleanSupplier.class))).thenReturn(resultado);
        ProcesadorTrabajoSincronizacionService procesador =
                new ProcesadorTrabajoSincronizacionService(
                        repository, mock(SincronizacionCanalesService.class),
                        mock(PublicacionService.class), importacion,
                        mock(RevisionPublicacionService.class));

        procesador.ejecutarImportacionCompleta(10L, 3L, CanalVenta.MERCADO_LIBRE, false);

        verify(importacion).importar(eq(CanalVenta.MERCADO_LIBRE), eq(productos),
                any(java.util.function.BooleanSupplier.class));
        verify(importacion, never()).importar(CanalVenta.MERCADO_LIBRE);
        assertEquals(EstadoTrabajoSincronizacion.COMPLETADA, trabajo.getEstado());
        assertTrue(trabajo.getResumen().contains("1 productos creados y 1 actualizados"));
    }

    @Test
    void importaLasUltimasPublicacionesFiltradasEnSegundoPlano() {
        TrabajoSincronizacionRepository repository = mock(TrabajoSincronizacionRepository.class);
        ImportacionCanalService importacion = mock(ImportacionCanalService.class);
        TrabajoSincronizacion trabajo = trabajo();
        when(repository.findById(10L)).thenReturn(Optional.of(trabajo));
        List<ProductoCanalImportado> productos = List.of(producto("MLA1", "NEU-1"));
        ResultadoImportacionCanal resultado = new ResultadoImportacionCanal();
        resultado.creado(1L);
        when(importacion.obtenerUltimasPublicacionesMercadoLibre(eq(5), eq("neumaticos"),
                eq(false), any(java.util.function.BooleanSupplier.class))).thenReturn(productos);
        when(importacion.importar(eq(CanalVenta.MERCADO_LIBRE), eq(productos),
                any(java.util.function.BooleanSupplier.class))).thenReturn(resultado);
        ProcesadorTrabajoSincronizacionService procesador =
                new ProcesadorTrabajoSincronizacionService(
                        repository, mock(SincronizacionCanalesService.class),
                        mock(PublicacionService.class), importacion,
                        mock(RevisionPublicacionService.class));

        procesador.ejecutarImportacionFiltradaMercadoLibre(
                10L, 3L, 5, "neumaticos", false);

        assertEquals(EstadoTrabajoSincronizacion.COMPLETADA, trabajo.getEstado());
        assertTrue(trabajo.getResumen().contains("1 productos creados"));
        verify(importacion).importar(eq(CanalVenta.MERCADO_LIBRE), eq(productos),
                any(java.util.function.BooleanSupplier.class));
    }

    @Test
    void cancelaAntesDeComenzarSinPublicarProductos() {
        TrabajoSincronizacionRepository repository = mock(TrabajoSincronizacionRepository.class);
        PublicacionService publicacionService = mock(PublicacionService.class);
        TrabajoSincronizacion trabajo = trabajo();
        trabajo.setCancelacionSolicitada(true);
        when(repository.findById(10L)).thenReturn(Optional.of(trabajo));
        ProcesadorTrabajoSincronizacionService procesador =
                new ProcesadorTrabajoSincronizacionService(
                        repository, mock(SincronizacionCanalesService.class),
                        publicacionService, mock(ImportacionCanalService.class),
                        mock(RevisionPublicacionService.class));

        procesador.ejecutarPublicacion(10L, 3L, List.of(4L, 5L),
                List.of(CanalVenta.WOOCOMMERCE));

        assertEquals(EstadoTrabajoSincronizacion.CANCELADO, trabajo.getEstado());
        assertNotNull(trabajo.getFinalizadoEn());
        assertTrue(trabajo.getResumen().contains("antes de comenzar"));
        verifyNoInteractions(publicacionService);
    }

    @Test
    void propagaLaCancelacionMientrasDescargaLaImportacionCompleta() {
        TrabajoSincronizacionRepository repository = mock(TrabajoSincronizacionRepository.class);
        ImportacionCanalService importacion = mock(ImportacionCanalService.class);
        TrabajoSincronizacion trabajo = trabajo();
        when(repository.findById(10L)).thenReturn(Optional.of(trabajo));
        when(importacion.obtenerProductos(eq(CanalVenta.MERCADO_LIBRE), eq(false),
                any(java.util.function.BooleanSupplier.class))).thenAnswer(invocacion -> {
                    java.util.function.BooleanSupplier cancelacion = invocacion.getArgument(2);
                    assertFalse(cancelacion.getAsBoolean());
                    trabajo.setCancelacionSolicitada(true);
                    assertTrue(cancelacion.getAsBoolean());
                    return List.of();
                });
        ProcesadorTrabajoSincronizacionService procesador =
                new ProcesadorTrabajoSincronizacionService(
                        repository, mock(SincronizacionCanalesService.class),
                        mock(PublicacionService.class), importacion,
                        mock(RevisionPublicacionService.class));

        procesador.ejecutarImportacionCompleta(
                10L, 3L, CanalVenta.MERCADO_LIBRE, false);

        assertEquals(EstadoTrabajoSincronizacion.CANCELADO, trabajo.getEstado());
        verify(importacion, never()).importar(eq(CanalVenta.MERCADO_LIBRE), anyList(),
                any(java.util.function.BooleanSupplier.class));
    }

    private ProductoCanalImportado producto(String idExterno, String sku) {
        return new ProductoCanalImportado(
                idExterno, sku, "Producto " + sku, 3, new BigDecimal("1000"),
                null, null, Map.of(), List.of());
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

package com.sistema.controller;

import com.sistema.dto.RevisionProductoPublicacionDto;
import com.sistema.dto.RevisionPublicacionSesion;
import com.sistema.model.CanalVenta;
import com.sistema.model.EstadoTrabajoSincronizacion;
import com.sistema.model.Producto;
import com.sistema.model.TrabajoSincronizacion;
import com.sistema.service.ProductoService;
import com.sistema.service.PublicacionService;
import com.sistema.service.RevisionPublicacionService;
import com.sistema.service.TrabajoSincronizacionService;
import com.sistema.tenant.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RevisionPublicacionControllerTest {
    private final ProductoService productoService = mock(ProductoService.class);
    private final PublicacionService publicacionService = mock(PublicacionService.class);
    private final RevisionPublicacionService revisionService =
            mock(RevisionPublicacionService.class);
    private final TrabajoSincronizacionService trabajoService =
            mock(TrabajoSincronizacionService.class);
    private final RevisionPublicacionController controller =
            new RevisionPublicacionController(
                    productoService, publicacionService,
                    revisionService, trabajoService);

    @Test
    void iniciaLaPreparacionAntesDeAbrirLaRevision() {
        when(publicacionService.estadoConfiguracion()).thenReturn(
                java.util.Map.of(CanalVenta.MERCADO_LIBRE, true));
        TrabajoSincronizacion trabajo = new TrabajoSincronizacion();
        trabajo.setId(77L);
        when(trabajoService.iniciarPreparacionPublicacion(
                List.of(1L, 2L), List.of(CanalVenta.MERCADO_LIBRE)))
                .thenReturn(trabajo);
        MockHttpSession session = new MockHttpSession();

        String destino;
        try (TenantContext.Scope ignored = TenantContext.use(7L)) {
            destino = controller.revisarSeleccion(
                    List.of(1L, 2L), List.of(CanalVenta.MERCADO_LIBRE),
                    false, "", session, new RedirectAttributesModelMap());
        }

        assertEquals("redirect:/canales/publicar/revision", destino);
        RevisionPublicacionSesion revision = (RevisionPublicacionSesion)
                session.getAttribute(RevisionPublicacionController.ATRIBUTO_SESION);
        assertEquals(77L, revision.trabajoPreparacionId());
    }

    @Test
    void quitaUnProductoSoloDeLaRevision() {
        MockHttpSession session = sesion(List.of(1L, 2L));
        RedirectAttributesModelMap atributos = new RedirectAttributesModelMap();

        String destino;
        try (TenantContext.Scope ignored = TenantContext.use(7L)) {
            destino = controller.quitarDeRevision(1L, session, atributos);
        }

        RevisionPublicacionSesion restante = (RevisionPublicacionSesion)
                session.getAttribute(RevisionPublicacionController.ATRIBUTO_SESION);
        assertEquals(List.of(2L), restante.productoIds());
        assertEquals("redirect:/canales/publicar/revision", destino);
        assertEquals("Producto quitado de esta revisión. No fue eliminado del sistema.",
                atributos.getFlashAttributes().get("mensaje"));
    }

    @Test
    void publicaSoloLosProductosListosElegidos() {
        MockHttpSession session = sesion(List.of(1L, 2L, 3L));
        List<CanalVenta> canales = List.of(CanalVenta.MERCADO_LIBRE);
        Producto producto = new Producto();
        producto.setId(2L);
        RevisionProductoPublicacionDto listo = new RevisionProductoPublicacionDto(
                producto, List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), java.util.Map.of(), java.util.Map.of());
        when(revisionService.revisar(List.of(2L), canales))
                .thenReturn(List.of(listo));
        TrabajoSincronizacion trabajo = new TrabajoSincronizacion();
        trabajo.setId(55L);
        when(trabajoService.iniciarPublicacion(List.of(2L), canales))
                .thenReturn(trabajo);
        RedirectAttributesModelMap atributos = new RedirectAttributesModelMap();

        String destino;
        try (TenantContext.Scope ignored = TenantContext.use(7L)) {
            destino = controller.confirmar(List.of(2L), session, atributos);
        }

        verify(trabajoService).iniciarPublicacion(List.of(2L), canales);
        assertEquals("redirect:/canales", destino);
        assertNull(session.getAttribute(
                RevisionPublicacionController.ATRIBUTO_SESION));
    }

    @Test
    void muestraEsperaMientrasSePreparanLasCategorias() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(RevisionPublicacionController.ATRIBUTO_SESION,
                new RevisionPublicacionSesion(
                        7L, List.of(1L, 2L),
                        List.of(CanalVenta.MERCADO_LIBRE), 77L));
        TrabajoSincronizacion trabajo = new TrabajoSincronizacion();
        trabajo.setId(77L);
        trabajo.setTenantId(7L);
        trabajo.setEstado(EstadoTrabajoSincronizacion.PROCESANDO);
        trabajo.setResumen("Detectando categorías...");
        when(trabajoService.obtenerDelTenantActual(77L)).thenReturn(trabajo);
        ExtendedModelMap model = new ExtendedModelMap();

        String vista;
        try (TenantContext.Scope ignored = TenantContext.use(7L)) {
            vista = controller.revision(session, model,
                    new RedirectAttributesModelMap());
        }

        assertEquals("canales/revision-preparando", vista);
        assertEquals(2, model.get("cantidadProductosRevision"));
        org.mockito.Mockito.verifyNoInteractions(revisionService);
    }

    @Test
    void abreLaTablaCuandoFinalizaLaPreparacion() {
        MockHttpSession session = new MockHttpSession();
        List<CanalVenta> canales = List.of(CanalVenta.MERCADO_LIBRE);
        session.setAttribute(RevisionPublicacionController.ATRIBUTO_SESION,
                new RevisionPublicacionSesion(
                        7L, List.of(1L), canales, 78L));
        TrabajoSincronizacion trabajo = new TrabajoSincronizacion();
        trabajo.setId(78L);
        trabajo.setTenantId(7L);
        trabajo.setEstado(EstadoTrabajoSincronizacion.COMPLETADA);
        when(trabajoService.obtenerDelTenantActual(78L)).thenReturn(trabajo);
        when(revisionService.consumirRevisionPreparada(78L))
                .thenReturn(java.util.Optional.of(List.of()));

        String vista;
        try (TenantContext.Scope ignored = TenantContext.use(7L)) {
            vista = controller.revision(session, new ExtendedModelMap(),
                    new RedirectAttributesModelMap());
        }

        assertEquals("canales/revision-publicacion", vista);
        RevisionPublicacionSesion revision = (RevisionPublicacionSesion)
                session.getAttribute(RevisionPublicacionController.ATRIBUTO_SESION);
        assertNull(revision.trabajoPreparacionId());
        verify(revisionService).consumirRevisionPreparada(78L);
    }

    private MockHttpSession sesion(List<Long> productoIds) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(RevisionPublicacionController.ATRIBUTO_SESION,
                new RevisionPublicacionSesion(
                        7L, productoIds, List.of(CanalVenta.MERCADO_LIBRE)));
        return session;
    }
}

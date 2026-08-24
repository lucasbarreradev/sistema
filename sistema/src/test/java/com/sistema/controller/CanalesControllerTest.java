package com.sistema.controller;

import com.sistema.model.CanalVenta;
import com.sistema.model.TrabajoSincronizacion;
import com.sistema.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class CanalesControllerTest {

    @Test
    void publicarTodosLosResultadosUsaLosIdsDeTodasLasPaginasDeLaBusqueda() {
        ProductoService productoService = mock(ProductoService.class);
        TrabajoSincronizacionService trabajos = mock(TrabajoSincronizacionService.class);
        when(productoService.getIdsProductosListado("neumatico"))
                .thenReturn(List.of(3L, 8L, 14L));
        TrabajoSincronizacion trabajo = new TrabajoSincronizacion();
        trabajo.setId(77L);
        when(trabajos.iniciarPublicacion(
                List.of(3L, 8L, 14L), List.of(CanalVenta.MERCADO_LIBRE)))
                .thenReturn(trabajo);
        CanalesController controller = new CanalesController(
                productoService,
                mock(ImportacionCsvService.class),
                mock(PublicacionService.class),
                mock(ImportacionCanalService.class),
                trabajos,
                mock(MercadoLibreTokenService.class),
                mock(WooCommerceCredencialesService.class),
                mock(TiendanubeCredencialesService.class),
                "", "");
        RedirectAttributesModelMap atributos = new RedirectAttributesModelMap();

        String vista = controller.publicar(
                List.of(3L), List.of(CanalVenta.MERCADO_LIBRE),
                true, "neumatico", atributos);

        assertEquals("redirect:/canales", vista);
        verify(productoService).getIdsProductosListado("neumatico");
        verify(trabajos).iniciarPublicacion(
                List.of(3L, 8L, 14L), List.of(CanalVenta.MERCADO_LIBRE));
        assertEquals(true, atributos.getFlashAttributes().get("mensaje")
                .toString().contains("3 producto(s)"));
    }
}

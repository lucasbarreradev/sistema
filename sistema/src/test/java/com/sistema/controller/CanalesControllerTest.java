package com.sistema.controller;

import com.sistema.model.CanalVenta;
import com.sistema.model.Producto;
import com.sistema.model.TrabajoSincronizacion;
import com.sistema.dto.ErrorSincronizacionDto;
import com.sistema.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.util.List;
import java.util.Optional;

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

    @Test
    void separaTodosLosErroresYLosCamposQueHayQueCompletar() {
        ProductoService productoService = mock(ProductoService.class);
        Producto producto = new Producto();
        producto.setId(15L);
        producto.setSku("BILL-001");
        when(productoService.getProductoBySku("BILL-001")).thenReturn(Optional.of(producto));
        when(productoService.getProductoBySku("BAND-001")).thenReturn(Optional.empty());
        CanalesController controller = new CanalesController(
                productoService,
                mock(ImportacionCsvService.class),
                mock(PublicacionService.class),
                mock(ImportacionCanalService.class),
                mock(TrabajoSincronizacionService.class),
                mock(MercadoLibreTokenService.class),
                mock(WooCommerceCredencialesService.class),
                mock(TiendanubeCredencialesService.class),
                "", "");
        TrabajoSincronizacion trabajo = new TrabajoSincronizacion();
        trabajo.setDetalle("""
                BILL-001 / Mercado Libre: Para publicar el producto, complete en Productos > Editar los campos obligatorios de Mercado Libre: Color, Género. Si esos campos no corresponden, cambie la categoría.
                BAND-001 / Mercado Libre: Mercado Libre no acepta el Motivo de GTIN vacío. Ingrese el GTIN real del producto.
                """);

        List<ErrorSincronizacionDto> errores = controller.mapearErrores(trabajo);

        assertEquals(2, errores.size());
        assertEquals(List.of("Color", "Género"), errores.get(0).getCorrecciones());
        assertEquals(15L, errores.get(0).getProductoId());
        assertEquals(List.of("GTIN real"), errores.get(1).getCorrecciones());
    }
}

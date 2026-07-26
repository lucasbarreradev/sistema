package com.sistema.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sistema.model.CanalVenta;
import com.sistema.model.OrdenCanalProcesada;
import com.sistema.model.Producto;
import com.sistema.model.ProductoVariante;
import com.sistema.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WebhookVentasServiceTest {
    private OrdenCanalProcesadaRepository ordenes;
    private ProductoVarianteRepository variantes;
    private MovimientoInventarioService movimientos;
    private WebhookVentasService service;

    @BeforeEach
    void preparar() {
        ordenes = mock(OrdenCanalProcesadaRepository.class);
        variantes = mock(ProductoVarianteRepository.class);
        movimientos = mock(MovimientoInventarioService.class);
        service = new WebhookVentasService(new ObjectMapper(), mock(MercadoLibreTokenService.class), ordenes,
                mock(PublicacionCanalRepository.class), mock(ProductoRepository.class), variantes, movimientos,
                mock(TiendanubeCredencialesService.class));
    }

    @Test
    void ventaWooDescuentaLaPresentacionUnaSolaVez() {
        Producto producto = new Producto();
        producto.setId(10L);
        ProductoVariante variante = new ProductoVariante();
        variante.setId(20L);
        variante.setProducto(producto);
        when(variantes.findByWooCommerceVariationId("77")).thenReturn(Optional.of(variante));

        service.procesarWooCommerce("""
                {"id":500,"status":"processing","line_items":[
                  {"product_id":99,"variation_id":77,"sku":"SKU-77","quantity":2}
                ]}
                """);

        verify(movimientos).registrarVentaExterna(10L, 20L, 2,
                "Venta WooCommerce / orden 500", CanalVenta.WOOCOMMERCE);
        verify(ordenes).save(any(OrdenCanalProcesada.class));
    }

    @Test
    void ignoraOrdenWooYaProcesada() {
        when(ordenes.existsByCanalAndOrdenId(CanalVenta.WOOCOMMERCE, "500")).thenReturn(true);

        service.procesarWooCommerce("""
                {"id":500,"status":"completed","line_items":[
                  {"product_id":99,"variation_id":77,"sku":"SKU-77","quantity":2}
                ]}
                """);

        verifyNoInteractions(movimientos);
        verify(ordenes, never()).save(any());
    }

    @Test
    void noDescuentaUnaOrdenWooPendiente() {
        service.procesarWooCommerce("""
                {"id":500,"status":"pending","line_items":[
                  {"product_id":99,"variation_id":77,"sku":"SKU-77","quantity":2}
                ]}
                """);

        verifyNoInteractions(movimientos, ordenes);
    }
}

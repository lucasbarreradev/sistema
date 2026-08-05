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
    private ProductoRepository productos;
    private VentaRepository ventas;
    private MovimientoInventarioService movimientos;
    private WebhookVentasService service;

    @BeforeEach
    void preparar() {
        ordenes = mock(OrdenCanalProcesadaRepository.class);
        variantes = mock(ProductoVarianteRepository.class);
        productos = mock(ProductoRepository.class);
        ventas = mock(VentaRepository.class);
        movimientos = mock(MovimientoInventarioService.class);
        service = new WebhookVentasService(new ObjectMapper(), mock(MercadoLibreTokenService.class), ordenes,
                mock(PublicacionCanalRepository.class), productos, variantes, movimientos,
                mock(TiendanubeCredencialesService.class), ventas);
    }

    @Test
    void ventaWooDescuentaLaPresentacionUnaSolaVez() {
        Producto producto = new Producto();
        producto.setId(10L);
        ProductoVariante variante = new ProductoVariante();
        variante.setId(20L);
        variante.setProducto(producto);
        when(variantes.findByWooCommerceVariationId("77")).thenReturn(Optional.of(variante));
        when(variantes.findById(20L)).thenReturn(Optional.of(variante));
        when(productos.findById(10L)).thenReturn(Optional.of(producto));

        service.procesarWooCommerce("""
                {"id":500,"status":"processing","line_items":[
                  {"product_id":99,"variation_id":77,"sku":"SKU-77","quantity":2}
                ]}
                """);

        verify(movimientos).registrarVentaExterna(10L, 20L, 2,
                "Venta WooCommerce / orden 500", CanalVenta.WOOCOMMERCE);
        verify(ordenes).save(any(OrdenCanalProcesada.class));
        verify(ventas).save(argThat(v -> v.getCanalVenta() == CanalVenta.WOOCOMMERCE
                && "500".equals(v.getOrdenExternaId()) && v.getItems().size() == 1
                && v.getItems().get(0).getCantidad() == 2));
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

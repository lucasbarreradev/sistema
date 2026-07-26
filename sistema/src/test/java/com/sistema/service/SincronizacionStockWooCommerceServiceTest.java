package com.sistema.service;

import com.sistema.model.*;
import com.sistema.repository.ProductoRepository;
import com.sistema.repository.PublicacionCanalRepository;
import com.sistema.service.canal.WooCommercePublicador;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SincronizacionStockWooCommerceServiceTest {
    private ProductoRepository productos;
    private PublicacionCanalRepository publicaciones;
    private WooCommercePublicador publicador;
    private SincronizacionStockWooCommerceService service;
    private Producto producto;
    private PublicacionCanal publicacion;

    @BeforeEach
    void configurar() {
        productos = mock(ProductoRepository.class);
        publicaciones = mock(PublicacionCanalRepository.class);
        publicador = mock(WooCommercePublicador.class);
        service = new SincronizacionStockWooCommerceService(productos, publicaciones, publicador);
        producto = new Producto();
        producto.setId(10L);
        producto.setCantidad(7);
        publicacion = new PublicacionCanal();
        publicacion.setProducto(producto);
        publicacion.setCanal(CanalVenta.WOOCOMMERCE);
        publicacion.setIdExterno("55");
        publicacion.setEstado(EstadoPublicacion.PUBLICADO);
        when(productos.findById(10L)).thenReturn(Optional.of(producto));
        when(publicaciones.findByProductoIdAndCanal(10L, CanalVenta.WOOCOMMERCE))
                .thenReturn(Optional.of(publicacion));
    }

    @Test
    void enviaElNuevoStockDespuesDeLaVenta() {
        service.sincronizar(new StockProductoCambiadoEvent(10L));
        verify(publicador).sincronizarStock(producto, "55");
        verify(publicaciones).save(publicacion);
        assertNull(publicacion.getUltimoError());
        assertNotNull(publicacion.getFechaActualizacion());
    }

    @Test
    void registraElErrorSinPropagarloALaVenta() {
        doThrow(new IllegalStateException("Woo no disponible"))
                .when(publicador).sincronizarStock(producto, "55");
        service.sincronizar(new StockProductoCambiadoEvent(10L));
        assertEquals(EstadoPublicacion.ERROR, publicacion.getEstado());
        assertTrue(publicacion.getUltimoError().contains("Woo no disponible"));
        verify(publicaciones).save(publicacion);
    }
}

package com.sistema.service;

import com.sistema.model.CanalVenta;
import com.sistema.model.EstadoPublicacion;
import com.sistema.model.Producto;
import com.sistema.model.PublicacionCanal;
import com.sistema.repository.ProductoRepository;
import com.sistema.repository.PublicacionCanalRepository;
import com.sistema.service.canal.TiendanubePublicador;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SincronizacionStockTiendaNubeServiceTest {
    private ProductoRepository productos;
    private PublicacionCanalRepository publicaciones;
    private TiendanubePublicador publicador;
    private SincronizacionStockTiendaNubeService service;
    private Producto producto;
    private PublicacionCanal publicacion;

    @BeforeEach
    void configurar() {
        productos = mock(ProductoRepository.class);
        publicaciones = mock(PublicacionCanalRepository.class);
        publicador = mock(TiendanubePublicador.class);
        service = new SincronizacionStockTiendaNubeService(productos, publicaciones, publicador);
        producto = new Producto();
        producto.setId(10L);
        producto.setCantidad(6);
        publicacion = new PublicacionCanal();
        publicacion.setProducto(producto);
        publicacion.setCanal(CanalVenta.TIENDANUBE);
        publicacion.setIdExterno("88");
        publicacion.setEstado(EstadoPublicacion.PUBLICADO);
        when(productos.findById(10L)).thenReturn(Optional.of(producto));
        when(publicaciones.findByProductoIdAndCanal(10L, CanalVenta.TIENDANUBE))
                .thenReturn(Optional.of(publicacion));
        when(publicador.configurado()).thenReturn(true);
    }

    @Test
    void enviaElNuevoStockDespuesDeLaVenta() {
        service.sincronizar(new StockProductoCambiadoEvent(10L));

        verify(publicador).sincronizarStock(producto, "88");
        verify(publicaciones).save(publicacion);
        assertNull(publicacion.getUltimoError());
        assertNotNull(publicacion.getFechaActualizacion());
    }

    @Test
    void registraElErrorSinPropagarloALaVenta() {
        doThrow(new IllegalStateException("Tiendanube no disponible"))
                .when(publicador).sincronizarStock(producto, "88");

        service.sincronizar(new StockProductoCambiadoEvent(10L));

        assertEquals(EstadoPublicacion.ERROR, publicacion.getEstado());
        assertTrue(publicacion.getUltimoError().contains("Tiendanube no disponible"));
        verify(publicaciones).save(publicacion);
    }

    @Test
    void omiteLaSincronizacionSiLaCuentaEstaDesconectada() {
        when(publicador.configurado()).thenReturn(false);

        service.sincronizar(new StockProductoCambiadoEvent(10L));

        verifyNoInteractions(productos, publicaciones);
        verify(publicador, never()).sincronizarStock(any(), anyString());
        assertEquals(EstadoPublicacion.PUBLICADO, publicacion.getEstado());
        assertNull(publicacion.getUltimoError());
    }
}

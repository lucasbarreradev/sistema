package com.sistema.service;

import com.sistema.model.CanalVenta;
import com.sistema.model.EstadoPublicacion;
import com.sistema.model.Producto;
import com.sistema.model.PublicacionCanal;
import com.sistema.repository.ProductoRepository;
import com.sistema.repository.PublicacionCanalRepository;
import com.sistema.service.canal.MercadoLibrePublicador;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SincronizacionStockMercadoLibreServiceTest {
    private ProductoRepository productos;
    private PublicacionCanalRepository publicaciones;
    private MercadoLibrePublicador publicador;
    private SincronizacionStockMercadoLibreService service;
    private Producto producto;
    private PublicacionCanal publicacion;

    @BeforeEach
    void configurar() {
        productos = mock(ProductoRepository.class);
        publicaciones = mock(PublicacionCanalRepository.class);
        publicador = mock(MercadoLibrePublicador.class);
        service = new SincronizacionStockMercadoLibreService(productos, publicaciones, publicador);

        producto = new Producto();
        producto.setId(10L);
        producto.setMercadoLibreId("MLA123");
        producto.setCantidad(3);
        publicacion = new PublicacionCanal();
        publicacion.setProducto(producto);
        publicacion.setCanal(CanalVenta.MERCADO_LIBRE);
        publicacion.setIdExterno("MLA123");
        publicacion.setEstado(EstadoPublicacion.IMPORTADO);

        when(productos.findById(10L)).thenReturn(Optional.of(producto));
        when(publicaciones.findByProductoIdAndCanal(10L, CanalVenta.MERCADO_LIBRE))
                .thenReturn(Optional.of(publicacion));
    }

    @Test
    void actualizaElStockYRegistraLaSincronizacion() {
        service.sincronizar(new StockProductoCambiadoEvent(10L));

        verify(publicador).sincronizarStock(producto, "MLA123");
        verify(publicaciones).save(publicacion);
        assertEquals(EstadoPublicacion.IMPORTADO, publicacion.getEstado());
        assertNull(publicacion.getUltimoError());
        assertNotNull(publicacion.getFechaActualizacion());
    }

    @Test
    void conservaLaVentaYRegistraElErrorDelCanal() {
        doThrow(new IllegalStateException("API no disponible"))
                .when(publicador).sincronizarStock(producto, "MLA123");

        service.sincronizar(new StockProductoCambiadoEvent(10L));

        assertEquals(EstadoPublicacion.ERROR, publicacion.getEstado());
        assertTrue(publicacion.getUltimoError().contains("API no disponible"));
        verify(publicaciones).save(publicacion);
    }
}

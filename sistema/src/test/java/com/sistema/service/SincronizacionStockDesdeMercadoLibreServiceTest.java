package com.sistema.service;

import com.sistema.dto.ProductoCanalImportado;
import com.sistema.model.CanalVenta;
import com.sistema.model.Producto;
import com.sistema.model.PublicacionCanal;
import com.sistema.repository.MovimientoInventarioRepository;
import com.sistema.repository.ProductoRepository;
import com.sistema.repository.ProductoVarianteRepository;
import com.sistema.repository.PublicacionCanalRepository;
import com.sistema.service.canal.MercadoLibreImportador;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class SincronizacionStockDesdeMercadoLibreServiceTest {
    @Test
    void traeElStockVendidoEnMercadoLibreAlProductoLocal() {
        PublicacionCanalRepository publicaciones = mock(PublicacionCanalRepository.class);
        ProductoRepository productos = mock(ProductoRepository.class);
        ProductoVarianteRepository variantes = mock(ProductoVarianteRepository.class);
        MovimientoInventarioRepository movimientos = mock(MovimientoInventarioRepository.class);
        MercadoLibreImportador importador = mock(MercadoLibreImportador.class);
        SincronizacionStockDesdeMercadoLibreService service = new SincronizacionStockDesdeMercadoLibreService(
                publicaciones, productos, variantes, movimientos, importador);

        Producto producto = new Producto();
        producto.setId(10L);
        producto.setCantidad(9);
        PublicacionCanal publicacion = new PublicacionCanal();
        publicacion.setId(20L);
        publicacion.setProducto(producto);
        publicacion.setCanal(CanalVenta.MERCADO_LIBRE);
        publicacion.setIdExterno("MLA123");
        when(publicaciones.findWithProductoById(20L)).thenReturn(Optional.of(publicacion));
        when(importador.obtenerProducto("MLA123")).thenReturn(new ProductoCanalImportado(
                "MLA123", "SKU-1", "Producto", 7, BigDecimal.TEN, null, "MLA1", Map.of(), List.of()));

        service.sincronizarPublicacion(20L);

        assertEquals(7, producto.getCantidad());
        verify(productos).save(producto);
        verify(movimientos).save(argThat(movimiento -> movimiento.getStockPrevio() == 9
                && movimiento.getStockPosterior() == 7 && movimiento.getCantidad() == 2));
    }
}

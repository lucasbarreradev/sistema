package com.sistema.service;

import com.sistema.dto.ResultadoPublicacionLote;
import com.sistema.model.CanalVenta;
import com.sistema.model.Producto;
import com.sistema.repository.ProductoRepository;
import com.sistema.repository.PublicacionCanalRepository;
import com.sistema.service.canal.PublicadorCanal;
import com.sistema.service.canal.ResultadoPublicacion;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PublicacionServiceTest {

    @Test
    void detieneElLoteAntesDelSiguienteProductoCuandoSeSolicitaCancelar() {
        ProductoRepository productos = mock(ProductoRepository.class);
        PublicacionCanalRepository publicaciones = mock(PublicacionCanalRepository.class);
        PublicadorCanal publicador = mock(PublicadorCanal.class);
        Producto primero = producto(1L, "SKU-1");
        Producto segundo = producto(2L, "SKU-2");
        when(publicador.canal()).thenReturn(CanalVenta.WOOCOMMERCE);
        when(publicador.publicar(any(Producto.class), any())).thenReturn(new ResultadoPublicacion("101"));
        when(productos.findById(1L)).thenReturn(Optional.of(primero));
        when(productos.findById(2L)).thenReturn(Optional.of(segundo));
        when(publicaciones.findByProductoIdAndCanal(anyLong(), eq(CanalVenta.WOOCOMMERCE)))
                .thenReturn(Optional.empty());
        PublicacionService service = new PublicacionService(
                productos, publicaciones, List.of(publicador));
        AtomicInteger controles = new AtomicInteger();

        ResultadoPublicacionLote resultado = service.publicar(
                List.of(1L, 2L), List.of(CanalVenta.WOOCOMMERCE),
                () -> controles.incrementAndGet() > 2);

        assertEquals(1, resultado.getExitosas());
        verify(publicador, times(1)).publicar(any(Producto.class), any());
        verify(productos, never()).findById(2L);
    }

    private Producto producto(Long id, String sku) {
        Producto producto = new Producto();
        producto.setId(id);
        producto.setSku(sku);
        producto.setDescripcion(sku);
        return producto;
    }
}

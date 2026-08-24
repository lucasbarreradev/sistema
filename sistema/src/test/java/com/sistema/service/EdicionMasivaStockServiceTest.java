package com.sistema.service;

import com.sistema.model.MovimientoInventario;
import com.sistema.model.Producto;
import com.sistema.repository.MovimientoInventarioRepository;
import com.sistema.repository.ProductoRepository;
import com.sistema.repository.ProductoVarianteRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EdicionMasivaStockServiceTest {

    @Test
    void fijaStockRegistraMovimientoYOmitelosProductosConVariantes() {
        ProductoRepository productoRepository = mock(ProductoRepository.class);
        ProductoVarianteRepository varianteRepository = mock(ProductoVarianteRepository.class);
        MovimientoInventarioRepository movimientoRepository =
                mock(MovimientoInventarioRepository.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        Producto simple = producto(1L, 4);
        Producto conVariantes = producto(2L, 12);
        when(productoRepository.findAllById(List.of(1L, 2L)))
                .thenReturn(List.of(simple, conVariantes));
        when(varianteRepository.existsByProductoId(1L)).thenReturn(false);
        when(varianteRepository.existsByProductoId(2L)).thenReturn(true);
        EdicionMasivaStockService service = new EdicionMasivaStockService(
                productoRepository, varianteRepository, movimientoRepository, publisher);

        var resultado = service.fijarStock(List.of(1L, 2L), 10);

        assertEquals(1, resultado.actualizados());
        assertEquals(1, resultado.omitidosConVariantes());
        assertEquals(10, simple.getCantidad());
        assertEquals(12, conVariantes.getCantidad());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<MovimientoInventario>> captor =
                ArgumentCaptor.forClass(Iterable.class);
        verify(movimientoRepository).saveAll(captor.capture());
        MovimientoInventario movimiento = captor.getValue().iterator().next();
        assertEquals(MovimientoInventario.Tipo.AJUSTE, movimiento.getTipo());
        assertEquals(4, movimiento.getStockPrevio());
        assertEquals(10, movimiento.getStockPosterior());
        assertEquals(6, movimiento.getCantidad());
        verify(publisher).publishEvent(new StockProductoCambiadoEvent(1L));
    }

    @Test
    void rechazaUnaSeleccionCompuestaSoloPorProductosConVariantes() {
        ProductoRepository productoRepository = mock(ProductoRepository.class);
        ProductoVarianteRepository varianteRepository = mock(ProductoVarianteRepository.class);
        Producto producto = producto(2L, 12);
        when(productoRepository.findAllById(List.of(2L))).thenReturn(List.of(producto));
        when(varianteRepository.existsByProductoId(2L)).thenReturn(true);
        EdicionMasivaStockService service = new EdicionMasivaStockService(
                productoRepository, varianteRepository,
                mock(MovimientoInventarioRepository.class),
                mock(ApplicationEventPublisher.class));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.fijarStock(List.of(2L), 10));

        assertTrue(error.getMessage().contains("variantes"));
        verify(productoRepository, never()).saveAll(any());
    }

    private Producto producto(Long id, int stock) {
        Producto producto = new Producto();
        producto.setId(id);
        producto.setCantidad(stock);
        producto.setSku("SKU-" + id);
        return producto;
    }
}

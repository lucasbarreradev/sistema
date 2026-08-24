package com.sistema.service;

import com.sistema.model.MovimientoInventario;
import com.sistema.model.Producto;
import com.sistema.model.ProductoVariante;
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
    void fijaElStockDeTodasLasVariantesYRecalculaElTotalDelProducto() {
        ProductoRepository productoRepository = mock(ProductoRepository.class);
        ProductoVarianteRepository varianteRepository = mock(ProductoVarianteRepository.class);
        MovimientoInventarioRepository movimientoRepository =
                mock(MovimientoInventarioRepository.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        Producto producto = producto(2L, 12);
        ProductoVariante talleM = variante(21L, producto, 4);
        ProductoVariante talleL = variante(22L, producto, 8);
        when(productoRepository.findAllById(List.of(2L))).thenReturn(List.of(producto));
        when(varianteRepository.findByProductoIdOrderByNombreAsc(2L))
                .thenReturn(List.of(talleM, talleL));
        EdicionMasivaStockService service = new EdicionMasivaStockService(
                productoRepository, varianteRepository, movimientoRepository, publisher);

        var resultado = service.ajustarStock(
                List.of(2L), 10, EdicionMasivaStockService.Operacion.FIJAR);

        assertEquals(1, resultado.productosActualizados());
        assertEquals(2, resultado.variantesActualizadas());
        assertEquals(10, talleM.getStock());
        assertEquals(10, talleL.getStock());
        assertEquals(20, producto.getCantidad());
        verify(varianteRepository).saveAll(List.of(talleM, talleL));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<MovimientoInventario>> captor =
                ArgumentCaptor.forClass(Iterable.class);
        verify(movimientoRepository).saveAll(captor.capture());
        List<MovimientoInventario> movimientos = new java.util.ArrayList<>();
        captor.getValue().forEach(movimientos::add);
        assertEquals(2, movimientos.size());
        MovimientoInventario movimiento = movimientos.get(0);
        assertEquals(MovimientoInventario.Tipo.AJUSTE, movimiento.getTipo());
        assertEquals(4, movimiento.getStockPrevio());
        assertEquals(10, movimiento.getStockPosterior());
        assertEquals(6, movimiento.getCantidad());
        assertSame(talleM, movimiento.getVariante());
        verify(publisher).publishEvent(new StockProductoCambiadoEvent(2L));
    }

    @Test
    void sumaStockEnProductosSinVariantes() {
        ProductoRepository productoRepository = mock(ProductoRepository.class);
        ProductoVarianteRepository varianteRepository = mock(ProductoVarianteRepository.class);
        Producto producto = producto(1L, 4);
        when(productoRepository.findAllById(List.of(1L))).thenReturn(List.of(producto));
        when(varianteRepository.findByProductoIdOrderByNombreAsc(1L)).thenReturn(List.of());
        EdicionMasivaStockService service = new EdicionMasivaStockService(
                productoRepository, varianteRepository,
                mock(MovimientoInventarioRepository.class),
                mock(ApplicationEventPublisher.class));

        var resultado = service.ajustarStock(
                List.of(1L), 3, EdicionMasivaStockService.Operacion.SUMAR);

        assertEquals(7, producto.getCantidad());
        assertEquals(1, resultado.productosActualizados());
        assertEquals(0, resultado.variantesActualizadas());
        verify(productoRepository).saveAll(List.of(producto));
    }

    @Test
    void restaStockDeVariantesSinPermitirValoresNegativos() {
        ProductoRepository productoRepository = mock(ProductoRepository.class);
        ProductoVarianteRepository varianteRepository = mock(ProductoVarianteRepository.class);
        Producto producto = producto(3L, 7);
        ProductoVariante talleM = variante(31L, producto, 2);
        ProductoVariante talleL = variante(32L, producto, 5);
        when(productoRepository.findAllById(List.of(3L))).thenReturn(List.of(producto));
        when(varianteRepository.findByProductoIdOrderByNombreAsc(3L))
                .thenReturn(List.of(talleM, talleL));
        EdicionMasivaStockService service = new EdicionMasivaStockService(
                productoRepository, varianteRepository,
                mock(MovimientoInventarioRepository.class),
                mock(ApplicationEventPublisher.class));

        var resultado = service.ajustarStock(
                List.of(3L), 3, EdicionMasivaStockService.Operacion.RESTAR);

        assertEquals(0, talleM.getStock());
        assertEquals(2, talleL.getStock());
        assertEquals(2, producto.getCantidad());
        assertEquals(2, resultado.variantesActualizadas());
    }

    private Producto producto(Long id, int stock) {
        Producto producto = new Producto();
        producto.setId(id);
        producto.setCantidad(stock);
        producto.setSku("SKU-" + id);
        return producto;
    }

    private ProductoVariante variante(Long id, Producto producto, int stock) {
        ProductoVariante variante = new ProductoVariante();
        variante.setId(id);
        variante.setProducto(producto);
        variante.setSku("VAR-" + id);
        variante.setStock(stock);
        return variante;
    }
}

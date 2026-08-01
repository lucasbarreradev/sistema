package com.sistema.service;

import com.sistema.model.Producto;
import com.sistema.model.ProductoVariante;
import com.sistema.model.FormaPago;
import com.sistema.repository.ProductoRepository;
import com.sistema.repository.ProductoVarianteRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductoVarianteServiceTest {

    @Test
    void generaSkuYCodigoDeBarrasCuandoNoSeIngresan() {
        ProductoVarianteRepository variantes = mock(ProductoVarianteRepository.class);
        ProductoRepository productos = mock(ProductoRepository.class);
        Producto producto = producto(1L, "REME-001");
        AtomicReference<ProductoVariante> guardada = new AtomicReference<>();
        when(productos.findById(1L)).thenReturn(Optional.of(producto));
        when(variantes.save(any(ProductoVariante.class))).thenAnswer(invocacion -> {
            ProductoVariante valor = invocacion.getArgument(0);
            guardada.set(valor);
            return valor;
        });
        when(variantes.existsByProductoId(1L)).thenReturn(true);
        when(variantes.findByProductoIdOrderByNombreAsc(1L)).thenAnswer(i ->
                guardada.get() == null ? List.of() : List.of(guardada.get()));

        ProductoVariante nueva = new ProductoVariante();
        nueva.setTalle("M");
        nueva.setStock(4);

        ProductoVariante resultado = new ProductoVarianteService(variantes, productos).guardar(1L, nueva);

        assertEquals("REME-001-001", resultado.getSku());
        assertTrue(resultado.getCodigoBarras().matches("EMP\\d{7}"));
        assertEquals(4, producto.getCantidad());
    }

    @Test
    void alEditarConSkuVacioConservaSkuYCodigoExistentes() {
        ProductoVarianteRepository variantes = mock(ProductoVarianteRepository.class);
        ProductoRepository productos = mock(ProductoRepository.class);
        Producto producto = producto(1L, "REME-001");
        ProductoVariante existente = new ProductoVariante();
        existente.setId(8L);
        existente.setProducto(producto);
        existente.setSku("REME-001-002");
        existente.setCodigoBarras("EMP1234567");
        existente.setTalle("S");
        existente.setStock(2);
        when(productos.findById(1L)).thenReturn(Optional.of(producto));
        when(variantes.findById(8L)).thenReturn(Optional.of(existente));
        when(variantes.save(any(ProductoVariante.class))).thenAnswer(i -> i.getArgument(0));
        when(variantes.existsByProductoId(1L)).thenReturn(true);
        when(variantes.findByProductoIdOrderByNombreAsc(1L)).thenReturn(List.of(existente));

        ProductoVariante edicion = new ProductoVariante();
        edicion.setId(8L);
        edicion.setSku(" ");
        edicion.setTalle("M");
        edicion.setStock(3);

        ProductoVariante resultado = new ProductoVarianteService(variantes, productos).guardar(1L, edicion);

        assertEquals("REME-001-002", resultado.getSku());
        assertEquals("EMP1234567", resultado.getCodigoBarras());
        assertEquals("M", resultado.getTalle());
    }

    @Test
    void alImportarGeneraCodigoInternoAunqueElCanalTraigaUnGtin() {
        ProductoVarianteRepository variantes = mock(ProductoVarianteRepository.class);
        ProductoRepository productos = mock(ProductoRepository.class);
        Producto producto = producto(1L, "NEUM-001");
        when(variantes.save(any(ProductoVariante.class))).thenAnswer(i -> i.getArgument(0));
        when(variantes.existsByProductoId(1L)).thenReturn(true);
        when(variantes.findByProductoIdOrderByNombreAsc(1L)).thenAnswer(i -> List.of());
        ProductoVariante importada = new ProductoVariante();
        importada.setTalle("16");
        importada.setCodigoBarras("1234560022045");

        ProductoVariante resultado = new ProductoVarianteService(variantes, productos).guardarImportada(producto, importada);

        assertTrue(resultado.getCodigoBarras().matches("EMP\\d{7}"));
    }

    @Test
    void permiteDejarPrecioCompraVacioAunqueElProductoNoTengaPrecioGeneral() {
        ProductoVarianteRepository variantes = mock(ProductoVarianteRepository.class);
        ProductoRepository productos = mock(ProductoRepository.class);
        Producto producto = new Producto();
        producto.setId(1L);
        producto.setSku("REME-001");
        producto.setPrecioContado(BigDecimal.TEN);
        producto.setPrecioTarjeta(BigDecimal.TEN);
        producto.setPrecioCuentaCorriente(BigDecimal.TEN);
        when(productos.findById(1L)).thenReturn(Optional.of(producto));
        when(variantes.save(any(ProductoVariante.class))).thenAnswer(i -> i.getArgument(0));
        when(variantes.existsByProductoId(1L)).thenReturn(true);
        ProductoVariante nueva = new ProductoVariante();
        nueva.setTalle("M");
        nueva.setStock(2);

        ProductoVariante guardada =
                new ProductoVarianteService(variantes, productos).guardar(1L, nueva);

        assertNull(guardada.getPrecioCompra());
    }

    @Test
    void permiteGuardarSinPreciosDeTarjetaNiCuentaCorriente() {
        ProductoVarianteRepository variantes = mock(ProductoVarianteRepository.class);
        ProductoRepository productos = mock(ProductoRepository.class);
        Producto producto = new Producto();
        producto.setId(1L);
        producto.setSku("REME-001");
        when(productos.findById(1L)).thenReturn(Optional.of(producto));
        when(variantes.save(any(ProductoVariante.class))).thenAnswer(i -> i.getArgument(0));
        when(variantes.existsByProductoId(1L)).thenReturn(true);
        when(variantes.findByProductoIdOrderByNombreAsc(1L)).thenReturn(List.of());

        ProductoVariante nueva = new ProductoVariante();
        nueva.setTalle("M");
        nueva.setStock(2);
        nueva.setPrecioContado(new BigDecimal("1500"));

        ProductoVariante guardada =
                new ProductoVarianteService(variantes, productos).guardar(1L, nueva);

        assertNull(guardada.getPrecioCompra());
        assertNull(guardada.getPrecioTarjeta());
        assertNull(guardada.getPrecioCuentaCorriente());
        assertEquals(new BigDecimal("1500"), guardada.precio(FormaPago.TARJETA));
        assertEquals(new BigDecimal("1500"), guardada.precio(FormaPago.CUENTA_CORRIENTE));
    }

    @Test
    void elListadoMuestraElRangoDePreciosDeLasVariantes() {
        Producto producto = new Producto();
        ProductoVariante primera = new ProductoVariante();
        primera.setProducto(producto);
        primera.setPrecioContado(new BigDecimal("1500"));
        ProductoVariante segunda = new ProductoVariante();
        segunda.setProducto(producto);
        segunda.setPrecioContado(new BigDecimal("1800"));
        producto.setVariantes(List.of(primera, segunda));

        assertEquals("1500 - 1800", producto.getPrecioContadoListado());
        assertEquals("1500 - 1800", producto.getPrecioTarjetaListado());
        assertEquals("1500 - 1800", producto.getPrecioCuentaCorrienteListado());
    }

    private Producto producto(Long id, String sku) {
        Producto producto = new Producto();
        producto.setId(id);
        producto.setSku(sku);
        producto.setPrecioCompra(BigDecimal.ONE);
        producto.setPrecioContado(BigDecimal.TEN);
        producto.setPrecioTarjeta(BigDecimal.TEN);
        producto.setPrecioCuentaCorriente(BigDecimal.TEN);
        return producto;
    }
}

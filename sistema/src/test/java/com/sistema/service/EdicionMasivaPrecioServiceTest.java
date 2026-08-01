package com.sistema.service;

import com.sistema.dto.ProductoCanalImportado;
import com.sistema.dto.VarianteCanalImportada;
import com.sistema.model.Producto;
import com.sistema.model.ProductoVariante;
import com.sistema.repository.ProductoRepository;
import com.sistema.repository.ProductoVarianteRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

class EdicionMasivaPrecioServiceTest {

    @Test
    void reducePrecioDelProductoImportadoYDeSusVariantes() {
        EdicionMasivaPrecioService service = new EdicionMasivaPrecioService(
                mock(ProductoRepository.class), mock(ProductoVarianteRepository.class));
        VarianteCanalImportada variante = new VarianteCanalImportada(
                "VAR-1", "SKU-1-M", "M", "M", "Negro", 3,
                new BigDecimal("100"), null, null, null, Map.of("SIZE", "M"),
                null, false);
        ProductoCanalImportado producto = new ProductoCanalImportado(
                "MLA1", "SKU-1", "Remera", 3, new BigDecimal("219999"),
                null, "MLA1", Map.of(), List.of(variante));

        ProductoCanalImportado ajustado = service
                .ajustarImportados(List.of(producto), new BigDecimal("-15"))
                .get(0);

        assertEquals(new BigDecimal("186999.15"), ajustado.precio());
        assertEquals(new BigDecimal("85.00"), ajustado.variantes().get(0).precio());
        assertEquals(new BigDecimal("219999"), producto.precio());
    }

    @Test
    void ajustaSoloPreciosDeVentaDelSistemaYPreservaCompraYValoresVacios() {
        ProductoRepository productos = mock(ProductoRepository.class);
        ProductoVarianteRepository variantes = mock(ProductoVarianteRepository.class);
        EdicionMasivaPrecioService service =
                new EdicionMasivaPrecioService(productos, variantes);

        Producto producto = new Producto();
        producto.setId(7L);
        producto.setPrecioCompra(new BigDecimal("50"));
        producto.setPrecioContado(new BigDecimal("100"));
        producto.setPrecioTarjeta(new BigDecimal("120"));

        ProductoVariante variante = new ProductoVariante();
        variante.setPrecioCompra(new BigDecimal("40"));
        variante.setPrecioContado(new BigDecimal("80"));
        variante.setPrecioCuentaCorriente(new BigDecimal("90"));

        when(productos.findAllById(List.of(7L))).thenReturn(List.of(producto));
        when(variantes.findByProductoIdOrderByNombreAsc(7L))
                .thenReturn(List.of(variante));

        var resultado = service.ajustarProductos(List.of(7L), new BigDecimal("10"));

        assertEquals(1, resultado.productos());
        assertEquals(1, resultado.variantes());
        assertEquals(new BigDecimal("110.00"), producto.getPrecioContado());
        assertEquals(new BigDecimal("132.00"), producto.getPrecioTarjeta());
        assertNull(producto.getPrecioCuentaCorriente());
        assertEquals(new BigDecimal("50"), producto.getPrecioCompra());
        assertEquals(new BigDecimal("88.00"), variante.getPrecioContado());
        assertEquals(new BigDecimal("99.00"), variante.getPrecioCuentaCorriente());
        assertEquals(new BigDecimal("40"), variante.getPrecioCompra());
        verify(productos).saveAll(List.of(producto));
        verify(variantes).saveAll(List.of(variante));
    }
}

package com.sistema.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sistema.dto.AtributoVarianteMl;
import com.sistema.model.CanalVenta;
import com.sistema.model.Producto;
import com.sistema.model.ProductoVariante;
import com.sistema.repository.ProductoRepository;
import com.sistema.repository.ProductoVarianteRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RevisionPublicacionServiceTest {
    private final ProductoRepository productos = mock(ProductoRepository.class);
    private final ProductoVarianteRepository variantes = mock(ProductoVarianteRepository.class);
    private final MercadoLibreAtributosVarianteService atributos =
            mock(MercadoLibreAtributosVarianteService.class);
    private final RevisionPublicacionService service = new RevisionPublicacionService(
            productos, variantes, atributos, new ObjectMapper());

    @Test
    void marcaCategoriaComoPendienteSinPublicar() {
        Producto producto = producto(1L);
        when(productos.findAllById(List.of(1L))).thenReturn(List.of(producto));
        when(variantes.findByProductoIdInOrderByProductoIdAscNombreAsc(List.of(1L)))
                .thenReturn(List.of());

        var revision = service.revisar(
                List.of(1L), List.of(CanalVenta.MERCADO_LIBRE)).get(0);

        assertFalse(revision.isListo());
        assertTrue(revision.faltantes().contains("Categoría de Mercado Libre"));
        verify(atributos, never()).obtenerPorCategoria(any());
    }

    @Test
    void informaElAtributoObligatorioQueFalta() {
        Producto producto = producto(2L);
        producto.setMercadoLibreCategoriaId("MLA123");
        producto.setFotoUrlExterna("https://img.test/foto.jpg");
        when(productos.findAllById(List.of(2L))).thenReturn(List.of(producto));
        when(variantes.findByProductoIdInOrderByProductoIdAscNombreAsc(List.of(2L)))
                .thenReturn(List.of());
        when(atributos.obtenerPorCategoria("MLA123")).thenReturn(
                new MercadoLibreAtributosVarianteService.Resultado("MLA123", List.of(
                        new AtributoVarianteMl("BRAND", "Marca", "string",
                                List.of(), List.of(), "", true, false))));

        var revision = service.revisar(
                List.of(2L), List.of(CanalVenta.MERCADO_LIBRE)).get(0);

        assertEquals(List.of("Marca"), revision.atributosFaltantes());
        assertEquals(List.of("Marca"), revision.atributosObligatorios());
        assertTrue(revision.isMarcaObligatoria());
        assertFalse(revision.isModeloObligatorio());
        assertTrue(revision.faltantes().get(0).contains("Marca"));
    }

    @Test
    void consultaUnaCategoriaUnaSolaVezParaTodoElLote() {
        Producto primero = producto(10L);
        Producto segundo = producto(11L);
        primero.setMercadoLibreCategoriaId("MLA123");
        segundo.setMercadoLibreCategoriaId("MLA123");
        primero.setFotoUrlExterna("https://img.test/1.jpg");
        segundo.setFotoUrlExterna("https://img.test/2.jpg");
        List<Long> ids = List.of(10L, 11L);
        when(productos.findAllById(ids)).thenReturn(List.of(primero, segundo));
        when(variantes.findByProductoIdInOrderByProductoIdAscNombreAsc(ids))
                .thenReturn(List.of());
        when(atributos.obtenerPorCategoria("MLA123")).thenReturn(
                new MercadoLibreAtributosVarianteService.Resultado(
                        "MLA123", List.of()));

        assertEquals(2, service.revisar(
                ids, List.of(CanalVenta.MERCADO_LIBRE)).size());

        verify(atributos).obtenerPorCategoria("MLA123");
    }

    @Test
    void guardaDatosGeneralesStockYPrecioDeCadaVariante() {
        Producto producto = producto(3L);
        ProductoVariante variante = new ProductoVariante();
        variante.setId(30L);
        variante.setProducto(producto);
        variante.setStock(1);
        variante.setPrecioContado(BigDecimal.TEN);
        when(productos.findById(3L)).thenReturn(Optional.of(producto));
        when(variantes.findByProductoIdOrderByNombreAsc(3L)).thenReturn(List.of(variante));

        service.actualizar(
                3L, "Título nuevo", "Descripción nueva", "MLA999",
                "BIG MISTY", "BM-500", null, new BigDecimal("8000"),
                "me2", true, true, "gold_pro",
                List.of(30L), List.of(7), List.of(new BigDecimal("9500")));

        assertEquals("Título nuevo", producto.getDescripcion());
        assertEquals("MLA999", producto.getMercadoLibreCategoriaId());
        assertEquals("BIG MISTY", producto.getMercadoLibreMarca());
        assertEquals("gold_pro", producto.getMercadoLibreListingTypeId());
        assertEquals(7, producto.getCantidad());
        assertEquals(7, variante.getStock());
        assertEquals(new BigDecimal("9500"), variante.getPrecioContado());
        assertTrue(producto.getMercadoLibreEnvioGratis());
        assertTrue(producto.getMercadoLibreRetiroPersonal());
        verify(variantes).saveAll(any());
        verify(productos).save(producto);
    }

    private Producto producto(Long id) {
        Producto producto = new Producto();
        producto.setId(id);
        producto.setSku("SKU-" + id);
        producto.setDescripcion("Producto de prueba");
        producto.setCantidad(2);
        producto.setPrecioContado(new BigDecimal("1000"));
        return producto;
    }
}

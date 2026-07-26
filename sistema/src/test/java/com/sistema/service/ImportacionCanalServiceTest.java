package com.sistema.service;

import com.sistema.dto.ProductoCanalImportado;
import com.sistema.dto.ResultadoImportacionCanal;
import com.sistema.dto.VarianteCanalImportada;
import com.sistema.dto.AtributoVarianteMl;
import com.sistema.model.CanalVenta;
import com.sistema.model.Producto;
import com.sistema.model.PublicacionCanal;
import com.sistema.repository.ProductoRepository;
import com.sistema.repository.ProductoVarianteRepository;
import com.sistema.repository.PublicacionCanalRepository;
import com.sistema.service.canal.ImportadorCanal;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ImportacionCanalServiceTest {
    @Test
    void actualizaPorIdExternoSinCrearOtroProducto() {
        ProductoRepository productos = mock(ProductoRepository.class);
        ProductoService productoService = mock(ProductoService.class);
        PublicacionCanalRepository publicaciones = mock(PublicacionCanalRepository.class);
        ImportadorCanal importador = mock(ImportadorCanal.class);
        Producto existente = new Producto();
        existente.setId(10L);
        existente.setSku("SKU-1");
        PublicacionCanal mapeo = new PublicacionCanal();
        mapeo.setProducto(existente);
        mapeo.setCanal(CanalVenta.WOOCOMMERCE);
        mapeo.setIdExterno("55");
        when(importador.canal()).thenReturn(CanalVenta.WOOCOMMERCE);
        when(importador.configurado()).thenReturn(true);
        when(importador.obtenerProductos()).thenReturn(List.of(new ProductoCanalImportado(
                "55", "SKU-1", "Producto actualizado", 8, new BigDecimal("123.45"), "https://img.test/foto.jpg", null, Map.of(), List.of())));
        when(publicaciones.findByCanalAndIdExterno(CanalVenta.WOOCOMMERCE, "55")).thenReturn(Optional.of(mapeo));
        ImportacionCanalService service = new ImportacionCanalService(productos, productoService, publicaciones, List.of(importador),
                mock(ProductoVarianteRepository.class), mock(ProductoVarianteService.class),
                new com.fasterxml.jackson.databind.ObjectMapper());

        ResultadoImportacionCanal resultado = service.importar(CanalVenta.WOOCOMMERCE);

        assertEquals(0, resultado.getCreados());
        assertEquals(1, resultado.getActualizados());
        assertEquals("Producto actualizado", existente.getDescripcion());
        assertEquals(new BigDecimal("123.45"), existente.getPrecioContado());
        verify(productoService).saveProducto(existente);
        verify(publicaciones).save(any(PublicacionCanal.class));
    }

    @Test
    void guardaItemsDeUnaFamiliaComoVariantesYConsolidaDuplicadosSinMovimientos() {
        ProductoRepository productos = mock(ProductoRepository.class);
        ProductoService productoService = mock(ProductoService.class);
        PublicacionCanalRepository publicaciones = mock(PublicacionCanalRepository.class);
        ProductoVarianteRepository variantesRepo = mock(ProductoVarianteRepository.class);
        ProductoVarianteService variantesService = mock(ProductoVarianteService.class);
        ImportadorCanal importador = mock(ImportadorCanal.class);
        Producto principal = new Producto(); principal.setId(10L); principal.setSku("REM-001");
        Producto duplicado = new Producto(); duplicado.setId(11L); duplicado.setSku("REM-002");
        PublicacionCanal pub1 = publicacion(principal, "MLA1");
        PublicacionCanal pub2 = publicacion(duplicado, "MLA2");
        Map<String, Object> datos = Map.of("familyId", "MLAF123", "familyName", "Remera");
        List<VarianteCanalImportada> variantes = List.of(
                new VarianteCanalImportada("MLA1", "REM-M", "M", "M", "Negro", 3,
                        BigDecimal.TEN, null, null, null, Map.of("SIZE", "M"), "https://img/1.jpg", true),
                new VarianteCanalImportada("MLA2", "REM-L", "L", "L", "Negro", 4,
                        BigDecimal.TEN, null, null, null, Map.of("SIZE", "L"), "https://img/2.jpg", true));
        when(importador.canal()).thenReturn(CanalVenta.MERCADO_LIBRE);
        when(importador.configurado()).thenReturn(true);
        when(importador.obtenerProductos()).thenReturn(List.of(new ProductoCanalImportado(
                "MLA1", null, "Remera", 7, BigDecimal.TEN, "https://img/1.jpg",
                "MLA1", datos, variantes)));
        when(publicaciones.findByCanalAndIdExterno(CanalVenta.MERCADO_LIBRE, "MLA1")).thenReturn(Optional.of(pub1));
        when(publicaciones.findByCanalAndIdExterno(CanalVenta.MERCADO_LIBRE, "MLA2")).thenReturn(Optional.of(pub2));
        when(variantesRepo.findBySkuIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(variantesRepo.findByProductoIdAndMercadoLibreItemId(anyLong(), anyString())).thenReturn(Optional.empty());

        ImportacionCanalService service = new ImportacionCanalService(productos, productoService, publicaciones,
                List.of(importador), variantesRepo, variantesService, new com.fasterxml.jackson.databind.ObjectMapper());

        service.importar(CanalVenta.MERCADO_LIBRE);

        assertEquals("MLAF123", principal.getMercadoLibreFamilyId());
        verify(variantesService, times(2)).guardarImportada(eq(principal), argThat(v ->
                v.getMercadoLibreItemId() != null && v.getFotoUrlExterna() != null));
        verify(productoService).deleteProducto(11L);
    }

    @Test
    void creaUnaPresentacionConAtributosParaUnItemSimpleDeMercadoLibre() {
        ProductoRepository productos = mock(ProductoRepository.class);
        ProductoService productoService = mock(ProductoService.class);
        PublicacionCanalRepository publicaciones = mock(PublicacionCanalRepository.class);
        ProductoVarianteRepository variantesRepo = mock(ProductoVarianteRepository.class);
        ProductoVarianteService variantesService = mock(ProductoVarianteService.class);
        MercadoLibreAtributosVarianteService atributosService =
                mock(MercadoLibreAtributosVarianteService.class);
        ImportadorCanal importador = mock(ImportadorCanal.class);
        Producto producto = new Producto();
        producto.setId(20L);
        producto.setSku("llanta87");
        PublicacionCanal publicacion = publicacion(producto, "MLA3663170578");
        Map<String, Object> datos = Map.of("atributosItem", Map.of(
                "BRAND", "Sunny",
                "SECTION_WIDTH", "195 mm",
                "ASPECT_RATIO", "55",
                "RIM_DIAMETER", "16 pulgadas",
                "SELLER_PACKAGE_HEIGHT", "62 cm"));
        when(importador.canal()).thenReturn(CanalVenta.MERCADO_LIBRE);
        when(importador.configurado()).thenReturn(true);
        when(importador.obtenerProductos()).thenReturn(List.of(new ProductoCanalImportado(
                "MLA3663170578", "llanta87", "Llanta Sunny", 4, new BigDecimal("100000"),
                "https://img.test/llanta.jpg", "MLA22195", datos, List.of())));
        when(publicaciones.findByCanalAndIdExterno(
                CanalVenta.MERCADO_LIBRE, "MLA3663170578")).thenReturn(Optional.of(publicacion));
        when(atributosService.obtener(producto)).thenReturn(
                new MercadoLibreAtributosVarianteService.Resultado("MLA22195", List.of(
                        atributo("BRAND", "Marca"),
                        atributo("SECTION_WIDTH", "Ancho de sección"),
                        atributo("ASPECT_RATIO", "Relación de aspecto"),
                        atributo("RIM_DIAMETER", "Diámetro de la llanta"))));
        when(variantesRepo.findBySkuIgnoreCase("llanta87")).thenReturn(Optional.empty());
        when(variantesRepo.findByProductoIdAndMercadoLibreItemId(
                20L, "MLA3663170578")).thenReturn(Optional.empty());

        ImportacionCanalService service = new ImportacionCanalService(
                productos, productoService, publicaciones, List.of(importador), variantesRepo,
                variantesService, new com.fasterxml.jackson.databind.ObjectMapper(), atributosService);

        service.importar(CanalVenta.MERCADO_LIBRE);

        verify(variantesService).guardarImportada(eq(producto), argThat(variante ->
                "llanta87".equals(variante.getSku())
                        && "MLA3663170578".equals(variante.getMercadoLibreItemId())
                        && variante.getMercadoLibreAtributosJson().contains("\"SECTION_WIDTH\":\"195 mm\"")
                        && variante.getMercadoLibreAtributosJson().contains("\"RIM_DIAMETER\":\"16 pulgadas\"")
                        && !variante.getMercadoLibreAtributosJson().contains("SELLER_PACKAGE_HEIGHT")));
    }

    private AtributoVarianteMl atributo(String id, String nombre) {
        return new AtributoVarianteMl(id, nombre, "string", List.of(), List.of(), "", true);
    }

    private PublicacionCanal publicacion(Producto producto, String id) {
        PublicacionCanal publicacion = new PublicacionCanal();
        publicacion.setProducto(producto);
        publicacion.setCanal(CanalVenta.MERCADO_LIBRE);
        publicacion.setIdExterno(id);
        return publicacion;
    }
}

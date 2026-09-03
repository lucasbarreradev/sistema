package com.sistema.service;

import com.sistema.dto.ProductoCanalImportado;
import com.sistema.dto.ResultadoImportacionCanal;
import com.sistema.dto.VarianteCanalImportada;
import com.sistema.dto.AtributoVarianteMl;
import com.sistema.model.CanalVenta;
import com.sistema.model.Producto;
import com.sistema.model.ProductoVariante;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ImportacionCanalServiceTest {
    @Test
    void noTraeNiImportaProductosSinStock() {
        ProductoRepository productos = mock(ProductoRepository.class);
        ProductoService productoService = mock(ProductoService.class);
        PublicacionCanalRepository publicaciones = mock(PublicacionCanalRepository.class);
        ImportadorCanal importador = mock(ImportadorCanal.class);
        when(importador.canal()).thenReturn(CanalVenta.TIENDANUBE);
        when(importador.configurado()).thenReturn(true);
        ProductoCanalImportado agotado = new ProductoCanalImportado(
                "TN-0", "BOT-0", "Botella agotada", 0, BigDecimal.TEN,
                null, null, Map.of(), List.of());
        ProductoCanalImportado disponible = new ProductoCanalImportado(
                "TN-1", "BOT-1", "Botella disponible", 2, BigDecimal.TEN,
                null, null, Map.of(), List.of());
        when(importador.obtenerProductos()).thenReturn(List.of(agotado, disponible));
        ImportacionCanalService service = new ImportacionCanalService(
                productos, productoService, publicaciones, List.of(importador),
                mock(ProductoVarianteRepository.class), mock(ProductoVarianteService.class),
                new com.fasterxml.jackson.databind.ObjectMapper());

        List<ProductoCanalImportado> recibidos =
                service.obtenerProductos(CanalVenta.TIENDANUBE);

        assertEquals(List.of(disponible), recibidos);
    }

    @Test
    void traeProductoSiAlgunaVarianteTieneStock() {
        ImportadorCanal importador = mock(ImportadorCanal.class);
        when(importador.canal()).thenReturn(CanalVenta.TIENDANUBE);
        when(importador.configurado()).thenReturn(true);
        ProductoCanalImportado producto = new ProductoCanalImportado(
                "TN-2", "BOT-2", "Botella con variantes", 0, BigDecimal.TEN,
                null, null, Map.of(), List.of(
                new VarianteCanalImportada("1", "BOT-500", "500 ml", "", "", 0,
                        BigDecimal.TEN, null, null, null, Map.of(), null, false),
                new VarianteCanalImportada("2", "BOT-750", "750 ml", "", "", 3,
                        BigDecimal.TEN, null, null, null, Map.of(), null, false)));
        when(importador.obtenerProductos()).thenReturn(List.of(producto));
        ImportacionCanalService service = new ImportacionCanalService(
                mock(ProductoRepository.class), mock(ProductoService.class),
                mock(PublicacionCanalRepository.class), List.of(importador),
                mock(ProductoVarianteRepository.class), mock(ProductoVarianteService.class),
                new com.fasterxml.jackson.databind.ObjectMapper());

        assertEquals(List.of(producto),
                service.obtenerProductos(CanalVenta.TIENDANUBE));
    }

    @Test
    void noImportaProductosCuandoLaCancelacionYaFueSolicitada() {
        ProductoRepository productos = mock(ProductoRepository.class);
        ProductoService productoService = mock(ProductoService.class);
        PublicacionCanalRepository publicaciones = mock(PublicacionCanalRepository.class);
        ImportadorCanal importador = mock(ImportadorCanal.class);
        when(importador.canal()).thenReturn(CanalVenta.WOOCOMMERCE);
        when(importador.configurado()).thenReturn(true);
        ProductoCanalImportado producto = new ProductoCanalImportado(
                "55", "SKU-1", "Producto", 8, BigDecimal.TEN,
                null, null, Map.of(), List.of());
        ImportacionCanalService service = new ImportacionCanalService(
                productos, productoService, publicaciones, List.of(importador),
                mock(ProductoVarianteRepository.class), mock(ProductoVarianteService.class),
                new com.fasterxml.jackson.databind.ObjectMapper());

        ResultadoImportacionCanal resultado = service.importar(
                CanalVenta.WOOCOMMERCE, List.of(producto), () -> true);

        assertEquals(0, resultado.getCreados());
        assertEquals(0, resultado.getActualizados());
        verifyNoInteractions(productoService, publicaciones);
    }

    @Test
    void noImportaVariantesEnCeroSiMercadoLibreInformaStockGeneral() {
        ProductoRepository productos = mock(ProductoRepository.class);
        ProductoService productoService = mock(ProductoService.class);
        PublicacionCanalRepository publicaciones = mock(PublicacionCanalRepository.class);
        ProductoVarianteRepository variantesRepo = mock(ProductoVarianteRepository.class);
        ProductoVarianteService variantesService = mock(ProductoVarianteService.class);
        ImportadorCanal importador = mock(ImportadorCanal.class);
        List<VarianteCanalImportada> variantes = List.of(
                new VarianteCanalImportada("101", "ASIC-40", "40 AR", "40 AR", "", 0,
                        BigDecimal.TEN, null, null, null, Map.of("SIZE", "40 AR"), null, false),
                new VarianteCanalImportada("102", "ASIC-41", "41 AR", "41 AR", "", 0,
                        BigDecimal.TEN, null, null, null, Map.of("SIZE", "41 AR"), null, false));
        when(importador.canal()).thenReturn(CanalVenta.MERCADO_LIBRE);
        when(importador.configurado()).thenReturn(true);
        when(importador.obtenerProductos()).thenReturn(List.of(new ProductoCanalImportado(
                "MLA1", "ASIC-001", "Zapatilla Asics", 6, BigDecimal.TEN,
                null, "MLA109027", Map.of(), variantes)));

        ImportacionCanalService service = new ImportacionCanalService(
                productos, productoService, publicaciones, List.of(importador), variantesRepo,
                variantesService, new com.fasterxml.jackson.databind.ObjectMapper());

        ResultadoImportacionCanal resultado = service.importar(CanalVenta.MERCADO_LIBRE);

        assertEquals(0, resultado.getCreados());
        assertEquals(0, resultado.getActualizados());
        assertTrue(resultado.getErrores().get(0).contains("no para sus presentaciones"));
        verifyNoInteractions(productoService, variantesService);
    }

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
        assertEquals("Producto actualizado", existente.getWooCommerceTitulo());
        assertEquals(new BigDecimal("123.45"), existente.getPrecioContado());
        verify(productoService).saveProducto(existente);
        verify(publicaciones).save(any(PublicacionCanal.class));
    }

    @Test
    void importarTiendanubeNoPisaTituloNiAtributosDeMercadoLibre() {
        ProductoRepository productos = mock(ProductoRepository.class);
        ProductoService productoService = mock(ProductoService.class);
        PublicacionCanalRepository publicaciones = mock(PublicacionCanalRepository.class);
        ProductoVarianteRepository variantesRepo = mock(ProductoVarianteRepository.class);
        ProductoVarianteService variantesService = mock(ProductoVarianteService.class);
        ImportadorCanal importador = mock(ImportadorCanal.class);
        when(importador.canal()).thenReturn(CanalVenta.TIENDANUBE);
        when(importador.configurado()).thenReturn(true);
        Producto producto = new Producto();
        producto.setId(30L);
        producto.setSku("VASO-1");
        producto.setMercadoLibreTitulo("Set de vasos para beber");
        producto.setMercadoLibreMarca("Marca ML");
        PublicacionCanal mapeo = publicacion(producto, "TN-30");
        ProductoVariante variante = new ProductoVariante();
        variante.setId(301L);
        variante.setProducto(producto);
        variante.setSku("VASO-NEGRO");
        variante.setMercadoLibreGtin("7790000000001");
        variante.setMercadoLibreAtributosJson(
                "{\"COLOR\":\"Negro ML\",\"MATERIAL\":\"Vidrio\"}");
        when(publicaciones.findByCanalAndIdExterno(
                CanalVenta.TIENDANUBE, "TN-30")).thenReturn(Optional.of(mapeo));
        when(variantesRepo.findByTiendaNubeVariationId("TN-V1"))
                .thenReturn(Optional.of(variante));
        VarianteCanalImportada varianteTn = new VarianteCanalImportada(
                "TN-V1", "VASO-NEGRO", "Negro TN", "", "Negro TN", 5,
                BigDecimal.TEN, null, null, "7799999999999",
                Map.of("COLOR", "Negro TN"), null, false);
        ProductoCanalImportado remoto = new ProductoCanalImportado(
                "TN-30", "VASO-1", "Vasos Tiendanube", 5, BigDecimal.TEN,
                null, null, Map.of("marca", "Marca TN"), List.of(varianteTn));
        ImportacionCanalService service = new ImportacionCanalService(
                productos, productoService, publicaciones, List.of(importador),
                variantesRepo, variantesService,
                new com.fasterxml.jackson.databind.ObjectMapper());

        service.importar(CanalVenta.TIENDANUBE, List.of(remoto));

        assertEquals("Vasos Tiendanube", producto.getTiendaNubeTitulo());
        assertEquals("Set de vasos para beber", producto.getMercadoLibreTitulo());
        assertEquals("Marca ML", producto.getMercadoLibreMarca());
        assertEquals("7790000000001", variante.getMercadoLibreGtin());
        assertEquals("{\"COLOR\":\"Negro ML\",\"MATERIAL\":\"Vidrio\"}",
                variante.getMercadoLibreAtributosJson());
        assertEquals("{\"COLOR\":\"Negro TN\"}",
                variante.getTiendaNubeAtributosJson());
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
    void identificaVariantesPorIdExternoAntesQuePorSkuRepetido() {
        ProductoRepository productos = mock(ProductoRepository.class);
        ProductoService productoService = mock(ProductoService.class);
        PublicacionCanalRepository publicaciones = mock(PublicacionCanalRepository.class);
        ProductoVarianteRepository variantesRepo = mock(ProductoVarianteRepository.class);
        ProductoVarianteService variantesService = mock(ProductoVarianteService.class);
        ImportadorCanal importador = mock(ImportadorCanal.class);
        Producto producto = new Producto();
        producto.setId(10L);
        producto.setSku("ZAPA-020");
        PublicacionCanal publicacion = publicacion(producto, "MLA2452926932");
        var talle40 = new com.sistema.model.ProductoVariante();
        talle40.setId(101L);
        talle40.setProducto(producto);
        talle40.setSku(" null ");
        var talle41 = new com.sistema.model.ProductoVariante();
        talle41.setId(102L);
        talle41.setProducto(producto);
        List<VarianteCanalImportada> variantes = List.of(
                new VarianteCanalImportada("186255490018", "null", "40 AR", "40 AR", "", 2,
                        BigDecimal.TEN, null, null, null, Map.of("SIZE", "40 AR"), null, false),
                new VarianteCanalImportada("186255490022", "ZAPA-020", "41 AR", "41 AR", "", 3,
                        BigDecimal.TEN, null, null, null, Map.of("SIZE", "41 AR"), null, false));
        when(importador.canal()).thenReturn(CanalVenta.MERCADO_LIBRE);
        when(importador.configurado()).thenReturn(true);
        when(importador.obtenerProductos()).thenReturn(List.of(new ProductoCanalImportado(
                "MLA2452926932", "ZAPA-020", "Zapatilla", 5, BigDecimal.TEN,
                null, "MLA109027", Map.of(), variantes)));
        when(publicaciones.findByCanalAndIdExterno(
                CanalVenta.MERCADO_LIBRE, "MLA2452926932")).thenReturn(Optional.of(publicacion));
        when(variantesRepo.findByMercadoLibreVariationId("186255490018"))
                .thenReturn(Optional.of(talle40));
        when(variantesRepo.findByMercadoLibreVariationId("186255490022"))
                .thenReturn(Optional.of(talle41));

        ImportacionCanalService service = new ImportacionCanalService(
                productos, productoService, publicaciones, List.of(importador),
                variantesRepo, variantesService, new com.fasterxml.jackson.databind.ObjectMapper());

        service.importar(CanalVenta.MERCADO_LIBRE);

        verify(variantesService).guardarImportada(eq(producto), same(talle40));
        verify(variantesService).guardarImportada(eq(producto), same(talle41));
        verify(variantesRepo, never()).findBySkuIgnoreCase(anyString());
        assertEquals("186255490018", talle40.getMercadoLibreVariationId());
        assertEquals("186255490022", talle41.getMercadoLibreVariationId());
        assertEquals(null, talle40.getSku());
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

    @Test
    void consolidaUnItemSimpleEnLaVarianteExistenteConElMismoSku() {
        ProductoRepository productos = mock(ProductoRepository.class);
        ProductoService productoService = mock(ProductoService.class);
        PublicacionCanalRepository publicaciones = mock(PublicacionCanalRepository.class);
        ProductoVarianteRepository variantesRepo = mock(ProductoVarianteRepository.class);
        ProductoVarianteService variantesService = mock(ProductoVarianteService.class);
        ImportadorCanal importador = mock(ImportadorCanal.class);

        Producto principal = new Producto();
        principal.setId(10L);
        principal.setSku("NEUM-046");
        principal.setDescripcion("Neumático Firestone 195/65 R15");
        ProductoVariante variante = new ProductoVariante();
        variante.setId(101L);
        variante.setProducto(principal);
        variante.setSku("10353003");
        variante.setStock(1);

        Producto duplicado = new Producto();
        duplicado.setId(20L);
        duplicado.setSku("10353003");
        PublicacionCanal mapeoDuplicado = publicacion(duplicado, "MLA123");

        when(importador.canal()).thenReturn(CanalVenta.MERCADO_LIBRE);
        when(importador.configurado()).thenReturn(true);
        when(variantesRepo.findBySkuIgnoreCase("10353003")).thenReturn(Optional.of(variante));
        when(variantesRepo.existsByProductoId(20L)).thenReturn(false);
        when(publicaciones.findByCanalAndIdExterno(CanalVenta.MERCADO_LIBRE, "MLA123"))
                .thenReturn(Optional.of(mapeoDuplicado));
        when(publicaciones.findByProductoIdAndCanal(10L, CanalVenta.MERCADO_LIBRE))
                .thenReturn(Optional.empty());

        ProductoCanalImportado remoto = new ProductoCanalImportado(
                "MLA123", "10353003", "Neumático Firestone", 7,
                new BigDecimal("125000"), "https://img.test/neumatico.jpg",
                "MLA22195", Map.of(), List.of());
        ImportacionCanalService service = new ImportacionCanalService(
                productos, productoService, publicaciones, List.of(importador),
                variantesRepo, variantesService, new com.fasterxml.jackson.databind.ObjectMapper());

        ResultadoImportacionCanal resultado = service.importar(
                CanalVenta.MERCADO_LIBRE, List.of(remoto));

        assertEquals(List.of(10L), resultado.getProductoIds());
        assertEquals(7, variante.getStock());
        assertEquals(new BigDecimal("125000"), variante.getPrecioContado());
        assertEquals("MLA123", variante.getMercadoLibreItemId());
        assertEquals("https://img.test/neumatico.jpg", variante.getFotoUrlExterna());
        verify(variantesService).guardarImportada(principal, variante);
        verify(publicaciones).delete(mapeoDuplicado);
        verify(productoService).deleteProducto(20L);
        verify(productoService, never()).saveProducto(duplicado);
    }

    @Test
    void noPisaOtroProductoDelMismoCanalAunqueRepitaElSku() {
        ProductoRepository productos = mock(ProductoRepository.class);
        ProductoService productoService = mock(ProductoService.class);
        PublicacionCanalRepository publicaciones = mock(PublicacionCanalRepository.class);
        ProductoVarianteRepository variantesRepo = mock(ProductoVarianteRepository.class);
        ImportadorCanal importador = mock(ImportadorCanal.class);
        Producto existente = new Producto();
        existente.setId(10L);
        existente.setSku("SKU-REPETIDO");
        existente.setDescripcion("Producto anterior");
        PublicacionCanal vinculoAnterior = new PublicacionCanal();
        vinculoAnterior.setProducto(existente);
        vinculoAnterior.setCanal(CanalVenta.TIENDANUBE);
        vinculoAnterior.setIdExterno("TN-1");
        when(importador.canal()).thenReturn(CanalVenta.TIENDANUBE);
        when(importador.configurado()).thenReturn(true);
        when(productos.findBySkuIgnoreCase("SKU-REPETIDO")).thenReturn(Optional.of(existente));
        when(publicaciones.findByProductoIdAndCanal(10L, CanalVenta.TIENDANUBE))
                .thenReturn(Optional.of(vinculoAnterior));

        ProductoCanalImportado remoto = new ProductoCanalImportado(
                "TN-2", "SKU-REPETIDO", "Producto distinto", 2, BigDecimal.TEN,
                null, null, Map.of(), List.of());
        ImportacionCanalService service = new ImportacionCanalService(
                productos, productoService, publicaciones, List.of(importador), variantesRepo,
                mock(ProductoVarianteService.class), new com.fasterxml.jackson.databind.ObjectMapper());

        ResultadoImportacionCanal resultado = service.importar(
                CanalVenta.TIENDANUBE, List.of(remoto));

        assertEquals(1, resultado.getCreados());
        assertEquals("Producto anterior", existente.getDescripcion());
        verify(productoService).saveProducto(argThat(producto -> producto != existente
                && "Producto distinto".equals(producto.getDescripcion())
                && producto.getSku() == null));
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

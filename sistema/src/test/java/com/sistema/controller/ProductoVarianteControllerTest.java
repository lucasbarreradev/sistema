package com.sistema.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sistema.model.ProductoVariante;
import com.sistema.model.Producto;
import com.sistema.dto.AtributoVarianteMl;
import com.sistema.service.ImagenWooCommerceService;
import com.sistema.service.MercadoLibreAtributosVarianteService;
import com.sistema.service.ProductoService;
import com.sistema.service.ProductoVarianteService;
import com.sistema.service.TenantPublicResourceService;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;

import java.util.Map;
import java.util.Optional;
import java.util.List;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductoVarianteControllerTest {
    private final ProductoVarianteService varianteService =
            mock(ProductoVarianteService.class);
    private final MercadoLibreAtributosVarianteService atributosService =
            mock(MercadoLibreAtributosVarianteService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ProductoVarianteController controller = new ProductoVarianteController(
            mock(ProductoService.class), varianteService,
            atributosService,
            mock(TenantPublicResourceService.class),
            mock(ImagenWooCommerceService.class), objectMapper);

    @Test
    void marcaYModeloPermitenValoresFueraDeLasSugerencias() {
        assertTrue(atributo("BRAND", "Marca", true, false).isPermiteValorLibre());
        assertTrue(atributo("MODEL", "Modelo", true, false).isPermiteValorLibre());
        assertTrue(atributo("MANUFACTURER", "Fabricante", true, false).isPermiteValorLibre());
        assertFalse(atributo("COLOR", "Color", true, true).isPermiteValorLibre());
    }

    @Test
    void conservaTalleYColorCuandoSeUsaElFormularioAlternativo() {
        ProductoVariante variante = new ProductoVariante();
        variante.setTalle("M");
        variante.setColor("Negro");

        controller.aplicarAtributosDinamicos(
                variante, Map.of("talle", "M", "color", "Negro"));

        assertEquals("M", variante.getTalle());
        assertEquals("Negro", variante.getColor());
        assertNull(variante.getMercadoLibreAtributosJson());
    }

    @Test
    void alEditarConservaAtributosGuardadosQueNoVolvieronEnElFormulario()
            throws Exception {
        ProductoVariante existente = new ProductoVariante();
        existente.setId(8L);
        existente.setMercadoLibreAtributosJson(
                "{\"SIZE\":\"M\",\"MATERIAL\":\"Algodón\"}");
        when(varianteService.buscar(8L)).thenReturn(Optional.of(existente));

        ProductoVariante edicion = new ProductoVariante();
        edicion.setId(8L);
        controller.aplicarAtributosDinamicos(
                edicion, Map.of("atributo_SIZE", "L"));

        Map<String, String> atributos = objectMapper.readValue(
                edicion.getMercadoLibreAtributosJson(), new TypeReference<>() {});
        assertEquals("L", atributos.get("SIZE"));
        assertEquals("Algodón", atributos.get("MATERIAL"));
        assertEquals("L", edicion.getTalle());
    }

    @Test
    @SuppressWarnings("unchecked")
    void alEditarCompletaCamposObligatoriosDesdeElProductoGeneral() {
        Producto producto = new Producto();
        producto.setId(554L);
        producto.setMercadoLibreCategoriaId("MLA66334");
        producto.setMercadoLibreAtributosJson("""
                [
                  {"id":"GARMENT_TYPE","value_name":"Campera"},
                  {"id":"COLOR","value_name":"Naranja"}
                ]
                """);
        ProductoVariante variante = new ProductoVariante();
        variante.setId(829L);
        variante.setProducto(producto);
        variante.setTalle("S");
        variante.setMercadoLibreAtributosJson("{\"SIZE\":\"S\"}");
        when(atributosService.obtener(producto)).thenReturn(
                new MercadoLibreAtributosVarianteService.Resultado(
                        "MLA66334", List.of(
                        atributo("GARMENT_TYPE", "Tipo de prenda", true, false),
                        atributo("COLOR", "Color", true, true),
                        atributo("SIZE", "Talle", true, true))));
        ExtendedModelMap model = new ExtendedModelMap();
        model.addAttribute("variantes", List.of(variante));

        controller.prepararAtributos(producto, variante, model);

        Map<String, String> valores =
                (Map<String, String>) model.get("valoresAtributos");
        assertEquals("Campera", valores.get("GARMENT_TYPE"));
        assertEquals("Naranja", valores.get("COLOR"));
        assertEquals("S", valores.get("SIZE"));
        assertEquals(List.of("GARMENT_TYPE"), ((List<AtributoVarianteMl>)
                model.get("atributosGenerales")).stream().map(AtributoVarianteMl::id).toList());
        assertEquals(List.of("COLOR", "SIZE"), ((List<AtributoVarianteMl>)
                model.get("atributosDeVariante")).stream().map(AtributoVarianteMl::id).toList());
        Map<Long, Map<String, String>> valoresPorVariante =
                (Map<Long, Map<String, String>>) model.get("valoresAtributosVariantes");
        assertEquals("S", valoresPorVariante.get(829L).get("SIZE"));
    }

    @Test
    void construyeElNombreSoloConLosAtributosQueRealmenteVarian() {
        ProductoVariante variante = new ProductoVariante();
        Map<String, String> parametros = new LinkedHashMap<>();
        parametros.put("atributo_COLOR", "Celeste");
        parametros.put("atributo_SIZE", "40 AR");
        parametros.put("atributo_BRAND", "Asics");
        parametros.put("atributo_MODEL", "Gel-Cumulus 27");
        parametros.put("atributo_VALUE_ADDED_TAX", "21 %");
        parametros.put("atributo_IMPORT_DUTY", "0 %");
        parametros.put("es_variacion_COLOR", "true");
        parametros.put("es_variacion_SIZE", "true");

        controller.aplicarAtributosDinamicos(variante, parametros);

        assertEquals("Celeste / 40 AR", variante.getNombre());
        assertEquals("Celeste", variante.getColor());
        assertEquals("40 AR", variante.getTalle());
    }

    @Test
    void alEditarMililitrosConservaElNombreImportadoDeTiendanube() {
        ProductoVariante existente = new ProductoVariante();
        existente.setId(91L);
        existente.setNombre("Presentación única");
        when(varianteService.buscar(91L)).thenReturn(Optional.of(existente));

        ProductoVariante edicion = new ProductoVariante();
        edicion.setId(91L);
        controller.aplicarAtributosDinamicos(edicion, Map.of(
                "atributo_CAPACITY", "500",
                "unidad_CAPACITY", "ml",
                "es_variacion_CAPACITY", "true"));

        assertNull(edicion.getNombre());
        assertEquals("{\"CAPACITY\":\"500 ml\"}",
                edicion.getMercadoLibreAtributosJson());
    }

    @Test
    void elNombreEscritoNoSeReemplazaConLosAtributosDinamicos() {
        ProductoVariante variante = new ProductoVariante();
        variante.setNombre("Black vetiver");

        controller.aplicarAtributosDinamicos(variante, Map.of(
                "atributo_FRAGRANCE", "Vetiver negro",
                "es_variacion_FRAGRANCE", "true"));

        assertEquals("Black vetiver", variante.getNombre());
    }

    @Test
    void separaLosAtributosGeneralesDeLosAtributosDeVariante() throws Exception {
        Producto producto = new Producto();
        ProductoVariante variante = new ProductoVariante();
        Map<String, String> parametros = new LinkedHashMap<>();
        parametros.put("atributos_separados", "true");
        parametros.put("atributo_BRAND", "BIG MISTY");
        parametros.put("atributo_MODEL", "BM-500");
        parametros.put("atributo_COLOR", "Negro");
        parametros.put("es_variacion_COLOR", "true");

        controller.aplicarAtributosGenerales(producto, parametros);
        controller.aplicarAtributosDinamicos(variante, parametros);

        assertEquals("BIG MISTY", producto.getMercadoLibreMarca());
        assertEquals("BM-500", producto.getMercadoLibreModelo());
        Map<String, String> atributosVariante = objectMapper.readValue(
                variante.getMercadoLibreAtributosJson(), new TypeReference<>() {});
        assertEquals(Map.of("COLOR", "Negro"), atributosVariante);
    }

    private AtributoVarianteMl atributo(
            String id, String nombre, boolean obligatorio, boolean permiteVariar) {
        return new AtributoVarianteMl(
                id, nombre, "string", List.of(), List.of(), "",
                obligatorio, permiteVariar);
    }
}

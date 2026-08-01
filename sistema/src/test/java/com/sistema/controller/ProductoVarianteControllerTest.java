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
import static org.junit.jupiter.api.Assertions.assertNull;
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

        controller.prepararAtributos(producto, variante, model);

        Map<String, String> valores =
                (Map<String, String>) model.get("valoresAtributos");
        assertEquals("Campera", valores.get("GARMENT_TYPE"));
        assertEquals("Naranja", valores.get("COLOR"));
        assertEquals("S", valores.get("SIZE"));
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

    private AtributoVarianteMl atributo(
            String id, String nombre, boolean obligatorio, boolean permiteVariar) {
        return new AtributoVarianteMl(
                id, nombre, "string", List.of(), List.of(), "",
                obligatorio, permiteVariar);
    }
}

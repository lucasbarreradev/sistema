package com.sistema.service.canal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sistema.dto.ProductoCanalImportado;
import com.sistema.dto.VarianteCanalImportada;
import com.sistema.service.MercadoLibreTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class MercadoLibreImportadorTest {

    @Test
    void consultaSoloPublicacionesActivasPorDefecto() {
        verificarUrlBusqueda(false,
                "https://api.mercadolibre.com/users/123/items/search?status=active&limit=50&offset=0");
    }

    @Test
    void omiteElFiltroDeEstadoCuandoSeSolicitanPublicacionesInactivas() {
        verificarUrlBusqueda(true,
                "https://api.mercadolibre.com/users/123/items/search?limit=50&offset=0");
    }

    @Test
    void consultaLaCantidadElegidaOrdenadaDesdeLaPublicacionMasReciente() {
        verificarUrlUltimas(5, "", false,
                "https://api.mercadolibre.com/users/123/items/search?status=active&orders=start_time_desc&limit=5");
    }

    @Test
    void resuelveElNombreDeCategoriaAntesDeBuscarPublicacionesRecientes() {
        MercadoLibreTokenService tokenService = mock(MercadoLibreTokenService.class);
        when(tokenService.obtenerAccessToken()).thenReturn("token");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
        MercadoLibreImportador importador = new MercadoLibreImportador(
                tokenService, new ObjectMapper(), builder.build());
        servidor.expect(requestTo("https://api.mercadolibre.com/users/me"))
                .andRespond(withSuccess("{\"id\":123}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("https://api.mercadolibre.com/sites/MLA/domain_discovery/search?limit=1&q=neumaticos"))
                .andRespond(withSuccess("[{\"category_id\":\"MLA22195\"}]",
                        MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("https://api.mercadolibre.com/users/123/items/search?status=active&orders=start_time_desc&limit=8&category_id=MLA22195"))
                .andRespond(withSuccess("{\"results\":[]}", MediaType.APPLICATION_JSON));

        assertTrue(importador.obtenerUltimasPublicaciones(
                8, "neumaticos", false, () -> false).isEmpty());
        servidor.verify();
    }

    @Test
    void detieneLaDescargaAntesDeConsultarElSiguienteProducto() {
        MercadoLibreTokenService tokenService = mock(MercadoLibreTokenService.class);
        when(tokenService.obtenerAccessToken()).thenReturn("token");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
        MercadoLibreImportador importador = new MercadoLibreImportador(
                tokenService, new ObjectMapper(), builder.build());
        servidor.expect(requestTo("https://api.mercadolibre.com/users/me"))
                .andRespond(withSuccess("{\"id\":123}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("https://api.mercadolibre.com/users/123/items/search?status=active&limit=50&offset=0"))
                .andRespond(withSuccess(
                        "{\"results\":[\"MLA1\",\"MLA2\"],\"paging\":{\"total\":2}}",
                        MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("https://api.mercadolibre.com/items/MLA1?include_attributes=all"))
                .andRespond(withSuccess("{\"id\":\"MLA1\",\"title\":\"Producto 1\",\"price\":10}",
                        MediaType.APPLICATION_JSON));
        AtomicInteger controles = new AtomicInteger();

        List<ProductoCanalImportado> productos = importador.obtenerProductos(
                false, () -> controles.incrementAndGet() > 3);

        assertTrue(productos.isEmpty());
        servidor.verify();
    }

    @Test
    void agrupaItemsUserProductsComoVariantesDeUnaFamilia() {
        MercadoLibreImportador importador = new MercadoLibreImportador(
                mock(MercadoLibreTokenService.class), new ObjectMapper());

        ProductoCanalImportado talleM = item("MLA1", "REM-001-M", "M", "Negro", 3);
        ProductoCanalImportado talleL = item("MLA2", "REM-001-L", "L", "Negro", 5);

        List<ProductoCanalImportado> resultado = importador.agruparUserProducts(List.of(talleM, talleL));

        assertEquals(1, resultado.size());
        ProductoCanalImportado familia = resultado.get(0);
        assertEquals("Remera Puma", familia.descripcion());
        assertNull(familia.sku());
        assertEquals(8, familia.cantidad());
        assertEquals(2, familia.variantes().size());
        assertTrue(familia.variantes().stream().allMatch(v -> v.itemMercadoLibre()));
        assertTrue(familia.variantes().stream().allMatch(v -> v.fotoUrl() != null));
        assertEquals(List.of("MLA1", "MLA2"), familia.variantes().stream()
                .map(v -> v.idExterno()).toList());
        assertEquals(List.of("M", "L"), familia.variantes().stream()
                .map(v -> v.talle()).toList());
    }

    @Test
    void importaTallesSkuYFotoDeLasVariacionesDelItem() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        MercadoLibreImportador importador = new MercadoLibreImportador(
                mock(MercadoLibreTokenService.class), objectMapper);
        var variaciones = objectMapper.readTree("""
                [
                  {
                    "id": 187111873168,
                    "seller_sku": "ASIC-40",
                    "available_quantity": 2,
                    "price": 259900,
                    "picture_ids": ["97923480750"],
                    "attribute_combinations": [
                      {"id": "COLOR_SECONDARY_COLOR", "value_name": "Agua"},
                      {"id": "SIZE", "value_name": "40 AR"}
                    ],
                    "attributes": []
                  },
                  {
                    "id": 187111873172,
                    "seller_custom_field": "ASIC-41",
                    "available_quantity": 1,
                    "price": 259900,
                    "picture_ids": ["98392648203"],
                    "attribute_combinations": [
                      {"id": "COLOR_SECONDARY_COLOR", "value_name": "Agua"},
                      {"id": "SIZE", "value_name": "41 AR"}
                    ],
                    "attributes": []
                  }
                ]
                """);
        var fotos = objectMapper.readTree("""
                [
                  {"id": "97923480750", "secure_url": "https://http2.mlstatic.com/foto-40.jpg"},
                  {"id": "98392648203", "secure_url": "https://http2.mlstatic.com/foto-41.jpg"}
                ]
                """);

        List<VarianteCanalImportada> resultado = importador.mapearVariantes(variaciones, fotos);

        assertEquals(2, resultado.size());
        assertEquals(List.of("ASIC-40", "ASIC-41"),
                resultado.stream().map(VarianteCanalImportada::sku).toList());
        assertEquals(List.of("40 AR", "41 AR"),
                resultado.stream().map(VarianteCanalImportada::talle).toList());
        assertEquals(List.of("https://http2.mlstatic.com/foto-40.jpg",
                        "https://http2.mlstatic.com/foto-41.jpg"),
                resultado.stream().map(VarianteCanalImportada::fotoUrl).toList());
    }

    @Test
    void noConvierteElSkuJsonNuloEnElTextoNull() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        MercadoLibreImportador importador = new MercadoLibreImportador(
                mock(MercadoLibreTokenService.class), objectMapper);
        var variaciones = objectMapper.readTree("""
                [{
                  "id": 187111873168,
                  "seller_sku": null,
                  "seller_custom_field": null,
                  "available_quantity": 1,
                  "price": 259900,
                  "attribute_combinations": [
                    {"id": "SIZE", "value_name": "41 AR"}
                  ],
                  "attributes": [
                    {"id": "SELLER_SKU", "value_id": null, "value_name": null}
                  ]
                }]
                """);

        List<VarianteCanalImportada> resultado =
                importador.mapearVariantes(variaciones, objectMapper.createArrayNode());

        assertEquals(1, resultado.size());
        assertTrue(resultado.get(0).sku() == null || resultado.get(0).sku().isBlank());
        assertEquals("41 AR", resultado.get(0).talle());
        assertEquals(1, resultado.get(0).stock());
    }

    @Test
    void sumaElStockRealDeTodasLasUbicacionesDelUserProduct() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        MercadoLibreImportador importador = new MercadoLibreImportador(
                mock(MercadoLibreTokenService.class), objectMapper);
        var stock = objectMapper.readTree("""
                {
                  "locations": [
                    {"type": "meli_facility", "quantity": 5},
                    {"type": "selling_address", "quantity": 9}
                  ]
                }
                """);

        assertEquals(14, importador.sumarStockUbicaciones(stock));
    }

    @Test
    void obtieneElStockDisponibleDelInventarioFull() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        MercadoLibreImportador importador = new MercadoLibreImportador(
                mock(MercadoLibreTokenService.class), objectMapper);
        var stock = objectMapper.readTree("""
                {
                  "inventory_id": "ABC123",
                  "total": 20,
                  "available_quantity": 7,
                  "not_available_quantity": 13
                }
                """);

        assertEquals(7, importador.stockFulfillment(stock));
    }

    @Test
    void consultaDetalleAunqueElResumenConTalleTraigaUserProductId() {
        MercadoLibreTokenService tokenService = mock(MercadoLibreTokenService.class);
        when(tokenService.obtenerAccessToken()).thenReturn("token");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
        MercadoLibreImportador importador = new MercadoLibreImportador(
                tokenService, new ObjectMapper(), builder.build());

        servidor.expect(requestTo("https://api.mercadolibre.com/items/MLA1?include_attributes=all"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "id": "MLA1",
                          "title": "Zapatilla azul",
                          "category_id": "MLA109027",
                          "price": 100,
                          "available_quantity": 0,
                          "attributes": [],
                          "pictures": [],
                          "variations": [{
                            "id": 501,
                            "user_product_id": "MLAU-501",
                            "available_quantity": 0,
                            "price": 100,
                            "attribute_combinations": [
                              {"id": "SIZE", "value_name": "41 AR"}
                            ],
                            "attributes": []
                          }]
                        }
                        """, MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("https://api.mercadolibre.com/items/MLA1/description"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"plain_text\":\"Zapatilla\"}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("https://api.mercadolibre.com/categories/MLA109027"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"name\":\"Zapatillas\"}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(
                        "https://api.mercadolibre.com/items/MLA1/variations/501?include_attributes=all"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "id": 501,
                          "available_quantity": 4
                        }
                        """, MediaType.APPLICATION_JSON));

        ProductoCanalImportado resultado = importador.obtenerProducto("MLA1");

        assertEquals(1, resultado.variantes().size());
        assertEquals("41 AR", resultado.variantes().get(0).talle());
        assertEquals(4, resultado.variantes().get(0).stock());
        assertEquals("Zapatillas", resultado.datosCanal().get("categoriaNombre"));
        servidor.verify();
    }

    private void verificarUrlBusqueda(boolean incluirInactivas, String urlEsperada) {
        MercadoLibreTokenService tokenService = mock(MercadoLibreTokenService.class);
        when(tokenService.obtenerAccessToken()).thenReturn("token");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
        MercadoLibreImportador importador = new MercadoLibreImportador(
                tokenService, new ObjectMapper(), builder.build());
        servidor.expect(requestTo("https://api.mercadolibre.com/users/me"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\":123}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(urlEsperada))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"results\":[],\"paging\":{\"total\":0}}",
                        MediaType.APPLICATION_JSON));

        assertTrue(importador.obtenerProductos(incluirInactivas).isEmpty());
        servidor.verify();
    }

    private void verificarUrlUltimas(int cantidad, String categoria,
                                     boolean incluirInactivas, String urlEsperada) {
        MercadoLibreTokenService tokenService = mock(MercadoLibreTokenService.class);
        when(tokenService.obtenerAccessToken()).thenReturn("token");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
        MercadoLibreImportador importador = new MercadoLibreImportador(
                tokenService, new ObjectMapper(), builder.build());
        servidor.expect(requestTo("https://api.mercadolibre.com/users/me"))
                .andRespond(withSuccess("{\"id\":123}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(urlEsperada))
                .andRespond(withSuccess("{\"results\":[]}", MediaType.APPLICATION_JSON));

        assertTrue(importador.obtenerUltimasPublicaciones(
                cantidad, categoria, incluirInactivas, () -> false).isEmpty());
        servidor.verify();
    }

    private ProductoCanalImportado item(String id, String sku, String talle, String color, int stock) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("familyId", "MLAF123");
        datos.put("familyName", "Remera Puma");
        datos.put("atributosItem", new LinkedHashMap<>(Map.of(
                "BRAND", "Puma", "COLOR", color, "SIZE", talle, "SELLER_SKU", sku)));
        return new ProductoCanalImportado(id, sku, "Remera Puma " + color + " " + talle,
                stock, new BigDecimal("2000"), "https://img.test/" + id + ".jpg",
                "MLA448691", datos, List.of());
    }
}

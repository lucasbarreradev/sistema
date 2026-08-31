package com.sistema.service.canal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.sistema.model.Producto;
import com.sistema.model.ProductoVariante;
import com.sistema.service.MercadoLibreTokenService;
import com.sistema.repository.ProductoVarianteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class MercadoLibrePublicadorTest {
    private MercadoLibrePublicador publicador;
    private ProductoVarianteRepository variantes;
    private MercadoLibreTokenService tokens;

    @BeforeEach
    void configurar() {
        variantes = mock(ProductoVarianteRepository.class);
        tokens = mock(MercadoLibreTokenService.class);
        when(tokens.obtenerAccessToken()).thenReturn("token");
        when(variantes.findByProductoIdOrderByNombreAsc(any())).thenReturn(List.of());
        publicador = new MercadoLibrePublicador(tokens, new ObjectMapper(), variantes);
        ReflectionTestUtils.setField(publicador, "categoryId", "");
        ReflectionTestUtils.setField(publicador, "listingTypeId", "gold_special");
        ReflectionTestUtils.setField(publicador, "userProducts", false);
        ReflectionTestUtils.setField(publicador, "publicBaseUrl", "");
    }

    @Test
    @SuppressWarnings("unchecked")
    void construyeLosCamposCompletosDeMercadoLibre() {
        Producto producto = productoBase();
        producto.setMercadoLibreOfficialStoreId(7L);
        producto.setMercadoLibreMarca("Marca original");
        producto.setMercadoLibreModelo("Modelo 1");
        producto.setMercadoLibreGtin("7791234567890");
        producto.setMercadoLibreGarantiaTipo("Garantía del vendedor");
        producto.setMercadoLibreGarantiaTiempo("90 días");
        producto.setMercadoLibreVideoId("abc123");
        producto.setMercadoLibreEnvioGratis(true);
        producto.setMercadoLibreRetiroPersonal(true);
        producto.setMercadoLibreModoEnvio("me2");
        producto.setMercadoLibreCondicion("used");
        producto.setMercadoLibreTiempoDisponibilidad(4);
        producto.setMercadoLibreListingTypeId("gold_pro");
        producto.setFotoUrlExterna("https://img.test/principal.jpg");
        producto.setFotosUrlsExternas("https://img.test/segunda.jpg\nhttps://img.test/principal.jpg");
        producto.setMercadoLibreAtributosJson("[{\"id\":\"BRAND\",\"value_id\":\"123\"},{\"id\":\"COLOR\",\"value_name\":\"Negro\"}]");

        Map<String, Object> payload = publicador.construirPayload(producto, true);

        assertEquals(7L, payload.get("official_store_id"));
        assertEquals(List.of("marketplace"), payload.get("channels"));
        assertEquals(Map.of("mode", "me2", "free_shipping", true, "local_pick_up", true), payload.get("shipping"));
        assertEquals("used", payload.get("condition"));
        assertEquals("gold_pro", payload.get("listing_type_id"));
        assertEquals(2, ((List<?>) payload.get("pictures")).size());
        List<Map<String, Object>> atributos = (List<Map<String, Object>>) payload.get("attributes");
        assertTrue(atributos.stream().anyMatch(a -> "SELLER_SKU".equals(a.get("id")) && "SKU-1".equals(a.get("value_name"))));
        assertTrue(atributos.stream().anyMatch(a -> "ITEM_CONDITION".equals(a.get("id")) && "2230284".equals(a.get("value_id"))));
        assertTrue(atributos.stream().anyMatch(a -> "BRAND".equals(a.get("id")) && "123".equals(a.get("value_id"))));
        assertTrue(atributos.stream().anyMatch(a -> "COLOR".equals(a.get("id"))));
        assertEquals(3, ((List<?>) payload.get("sale_terms")).size());
    }

    @Test
    void rechazaAtributosJsonInvalidosAntesDePublicar() {
        Producto producto = productoBase();
        producto.setMercadoLibreAtributosJson("no-es-json");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> publicador.construirPayload(producto, true));

        assertTrue(error.getMessage().contains("JSON"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void agregaGuiaYFilaAUnProductoSimple() {
        Producto producto = productoBase();
        producto.setMercadoLibreGuiaTallesId("515255");
        producto.setMercadoLibreGuiaTallesFilaId("515255:2");

        Map<String, Object> payload = publicador.construirPayload(producto, true);

        List<Map<String, Object>> atributos = (List<Map<String, Object>>) payload.get("attributes");
        assertTrue(atributos.stream().anyMatch(a -> "SIZE_GRID_ID".equals(a.get("id"))
                && "515255".equals(a.get("value_name"))));
        assertTrue(atributos.stream().anyMatch(a -> "SIZE_GRID_ROW_ID".equals(a.get("id"))
                && "515255:2".equals(a.get("value_name"))));
    }

    @Test
    @SuppressWarnings("unchecked")
    void publicaUnaPresentacionComoProductoSimpleConSusAtributos() {
        Producto producto = productoBase();
        producto.setId(10L);
        producto.setUsaVariantes(true);
        ProductoVariante variante = new ProductoVariante();
        variante.setProducto(producto); variante.setSku("SKU-M-NEGRO"); variante.setTalle("M"); variante.setColor("Negro");
        variante.setMercadoLibreAtributosJson("{\"SIZE\":\"M\",\"COLOR\":\"Negro\",\"FABRIC_DESIGN\":\"Lisa\"}");
        variante.setStock(3); variante.setPrecioContado(new BigDecimal("1200")); variante.setMercadoLibreVariationId("9001");
        when(variantes.findByProductoIdOrderByNombreAsc(10L)).thenReturn(List.of(variante));

        Map<String, Object> payload = publicador.construirPayload(producto, false);

        assertFalse(payload.containsKey("variations"));
        assertEquals(3, payload.get("available_quantity"));
        List<Map<String, Object>> atributosPropios = (List<Map<String, Object>>) payload.get("attributes");
        assertTrue(atributosPropios.stream().anyMatch(a -> "SELLER_SKU".equals(a.get("id"))
                && "SKU-M-NEGRO".equals(a.get("value_name"))));
        assertTrue(atributosPropios.stream().anyMatch(a -> "FABRIC_DESIGN".equals(a.get("id"))
                && "Lisa".equals(a.get("value_name"))));
    }

    @Test
    void usaElPrecioDeLaVarianteSiElProductoNoTienePrecioGeneral() {
        Producto producto = productoBase();
        producto.setId(10L);
        producto.setPrecioContado(null);
        producto.setUsaVariantes(true);
        ProductoVariante variante = new ProductoVariante();
        variante.setProducto(producto);
        variante.setSku("SKU-M");
        variante.setTalle("M");
        variante.setStock(2);
        variante.setPrecioContado(new BigDecimal("1250.00"));
        when(variantes.findByProductoIdOrderByNombreAsc(10L)).thenReturn(List.of(variante));

        Map<String, Object> payload = publicador.construirPayload(producto, false);

        assertEquals(new BigDecimal("1250.00"), payload.get("price"));
    }

    @Test
    void usaTitleYNoFamilyNameCuandoHayUnaSolaPresentacion() {
        ReflectionTestUtils.setField(publicador, "userProducts", true);
        Producto producto = productoBase();
        producto.setId(10L);
        producto.setUsaVariantes(true);
        ProductoVariante variante = new ProductoVariante();
        variante.setProducto(producto);
        variante.setSku("SKU-M");
        variante.setTalle("M");
        variante.setStock(2);
        variante.setPrecioContado(new BigDecimal("1250.00"));
        when(variantes.findByProductoIdOrderByNombreAsc(10L)).thenReturn(List.of(variante));

        Map<String, Object> payload = publicador.construirPayload(producto, true);

        assertEquals("Producto de prueba", payload.get("title"));
        assertFalse(payload.containsKey("family_name"));
        assertFalse(payload.containsKey("variations"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void construyeUnItemUserProductIndependientePorVariante() {
        ReflectionTestUtils.setField(publicador, "userProducts", true);
        Producto producto = productoBase();
        producto.setId(10L);
        ProductoVariante variante = new ProductoVariante();
        variante.setProducto(producto);
        variante.setSku("REM-NIK-M-NEGRA");
        variante.setTalle("M");
        variante.setColor("Negro");
        variante.setStock(3);
        variante.setPrecioContado(new BigDecimal("1500.00"));
        when(variantes.findByProductoIdOrderByNombreAsc(10L)).thenReturn(List.of(variante));

        Map<String, Object> payload = publicador.construirPayloadVarianteUserProduct(producto, variante, true);

        assertEquals("Producto de prueba", payload.get("family_name"));
        assertFalse(payload.containsKey("title"));
        assertFalse(payload.containsKey("variations"));
        assertEquals(new BigDecimal("1500.00"), payload.get("price"));
        assertEquals(3, payload.get("available_quantity"));
        List<Map<String, Object>> atributos = (List<Map<String, Object>>) payload.get("attributes");
        assertTrue(atributos.stream().anyMatch(a -> "SELLER_SKU".equals(a.get("id"))
                && "REM-NIK-M-NEGRA".equals(a.get("value_name"))));
        assertTrue(atributos.stream().anyMatch(a -> "SIZE".equals(a.get("id"))
                && "M".equals(a.get("value_name"))));
        assertTrue(atributos.stream().anyMatch(a -> "COLOR".equals(a.get("id"))
                && "Negro".equals(a.get("value_name"))));
    }

    @Test
    @SuppressWarnings("unchecked")
    void enviaElMotivoCuandoLaVarianteNoTieneGtin() {
        ReflectionTestUtils.setField(publicador, "userProducts", true);
        Producto producto = productoBase(); producto.setId(10L);
        ProductoVariante variante = new ProductoVariante();
        variante.setProducto(producto); variante.setSku("CEL-AZUL"); variante.setStock(1);
        variante.setPrecioContado(BigDecimal.TEN);
        variante.setMercadoLibreAtributosJson("{\"COLOR\":\"Azul\",\"EMPTY_GTIN_REASON\":\"El producto no tiene código registrado\"}");
        when(variantes.findByProductoIdOrderByNombreAsc(10L)).thenReturn(List.of(variante));

        Map<String, Object> payload = publicador.construirPayloadVarianteUserProduct(producto, variante, true);

        List<Map<String, Object>> atributos = (List<Map<String, Object>>) payload.get("attributes");
        assertTrue(atributos.stream().anyMatch(a -> "EMPTY_GTIN_REASON".equals(a.get("id"))));
    }

    @Test
    @SuppressWarnings("unchecked")
    void noReenviaAtributosDeSoloLecturaImportados() {
        Producto producto = productoBase();
        producto.setMercadoLibreAtributosJson("[{\"id\":\"HAZMAT_TRANSPORTABILITY\",\"value_name\":\"Exceptuado\"},"
                + "{\"id\":\"BATTERY_CAPACITY\",\"value_name\":\"4.9 Ah\"}]");
        Map<String, Set<String>> cache = (Map<String, Set<String>>) ReflectionTestUtils
                .getField(publicador, "atributosSoloLecturaPorCategoria");
        assertNotNull(cache);
        cache.put("MLA1234", Set.of("HAZMAT_TRANSPORTABILITY"));

        Map<String, Object> payload = publicador.construirPayload(producto, true);

        List<Map<String, Object>> atributos = (List<Map<String, Object>>) payload.get("attributes");
        assertFalse(atributos.stream().anyMatch(a -> "HAZMAT_TRANSPORTABILITY".equals(a.get("id"))));
        assertTrue(atributos.stream().anyMatch(a -> "BATTERY_CAPACITY".equals(a.get("id"))));
    }

    @Test
    @SuppressWarnings("unchecked")
    void completaComoNoAplicaLosObligatoriosQueNoPuedeInferir() throws Exception {
        Producto producto = productoBase();
        JsonNode definiciones = new ObjectMapper().readTree("""
                [
                  {"id":"BRAND","name":"Marca","value_type":"string","tags":{"required":true}},
                  {"id":"MODEL","name":"Modelo","value_type":"string","tags":{"required":true}},
                  {"id":"COLLECTION_NAME","name":"Nombre de la colección","value_type":"string","tags":{"required":true}}
                ]
                """);

        publicador.autocompletarAtributosObligatorios(producto, List.of(), definiciones);
        List<Map<String, Object>> atributos = (List<Map<String, Object>>)
                publicador.construirPayload(producto, true).get("attributes");

        assertTrue(atributos.stream().filter(a -> Set.of("BRAND", "MODEL", "COLLECTION_NAME").contains(a.get("id")))
                .allMatch(a -> "-1".equals(a.get("value_id")) && a.containsKey("value_name")
                        && a.get("value_name") == null));
    }

    @Test
    @SuppressWarnings("unchecked")
    void detectaUnBooleanoPermitidoEnElTitulo() throws Exception {
        Producto producto = productoBase();
        producto.setDescripcion("Cargador portátil con USB");
        JsonNode definiciones = new ObjectMapper().readTree("""
                [{
                  "id":"WITH_USB","name":"Con USB","value_type":"boolean",
                  "tags":{"required":true},
                  "values":[{"id":"242085","name":"Sí"},{"id":"242084","name":"No"}]
                }]
                """);

        publicador.autocompletarAtributosObligatorios(producto, List.of(), definiciones);
        List<Map<String, Object>> atributos = (List<Map<String, Object>>)
                publicador.construirPayload(producto, true).get("attributes");

        assertTrue(atributos.stream().anyMatch(a -> "WITH_USB".equals(a.get("id"))
                && "242085".equals(a.get("value_id"))));
    }

    @Test
    void alActualizarPuedeReintentarSinFichaTecnicaSiElOriginalNoTraiaGtin() {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("price", BigDecimal.TEN);
        payload.put("available_quantity", 2);
        payload.put("attributes", List.of(Map.of("id", "EMPTY_GTIN_REASON", "value_name", "No registrado")));
        payload.put("variations", List.of(Map.of("id", "1")));

        Map<String, Object> reintento = publicador.prepararReintentoSinAtributos(payload);

        assertEquals(BigDecimal.TEN, reintento.get("price"));
        assertEquals(2, reintento.get("available_quantity"));
        assertFalse(reintento.containsKey("attributes"));
        assertFalse(reintento.containsKey("variations"));
    }

    @Test
    void alActualizarCatalogoPuedeReintentarSinFotos() {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("price", BigDecimal.TEN);
        payload.put("available_quantity", 2);
        payload.put("pictures", List.of(Map.of("source", "https://img.test/foto.jpg")));

        Map<String, Object> reintento = publicador.prepararReintentoSinFotos(payload);

        assertEquals(BigDecimal.TEN, reintento.get("price"));
        assertEquals(2, reintento.get("available_quantity"));
        assertFalse(reintento.containsKey("pictures"));
    }

    @Test
    void noIntentaModificarUnaPublicacionDeOtroVendedor() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
        MercadoLibrePublicador publicadorConApi = new MercadoLibrePublicador(
                tokens, new ObjectMapper(), variantes, builder.build());

        servidor.expect(requestTo("https://api.mercadolibre.com/items/MLA123"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"id\":\"MLA123\",\"seller_id\":111,\"status\":\"active\"}",
                        MediaType.APPLICATION_JSON));

        assertTrue(publicadorConApi.requiereNuevaPublicacion("MLA123", 222L));
        servidor.verify();
    }

    @Test
    void creaOtraPublicacionSiLaCuentaNoPuedeConsultarElItemImportado() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
        MercadoLibrePublicador publicadorConApi = new MercadoLibrePublicador(
                tokens, new ObjectMapper(), variantes, builder.build());

        servidor.expect(requestTo("https://api.mercadolibre.com/items/MLA403"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"access_denied\",\"status\":403}"));

        assertTrue(publicadorConApi.requiereNuevaPublicacion("MLA403", 222L));
        servidor.verify();
    }

    @Test
    void explicaCuandoLaPublicacionPropiaEstaEnRevision() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
        MercadoLibrePublicador publicadorConApi = new MercadoLibrePublicador(
                tokens, new ObjectMapper(), variantes, builder.build());

        servidor.expect(requestTo("https://api.mercadolibre.com/items/MLA3897297956"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"id\":\"MLA3897297956\",\"seller_id\":222,\"status\":\"under_review\","
                                + "\"sub_status\":[\"held\"]}",
                        MediaType.APPLICATION_JSON));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> publicadorConApi.requiereNuevaPublicacion("MLA3897297956", 222L));

        assertTrue(error.getMessage().contains("under_review"));
        assertTrue(error.getMessage().contains("no creó una publicación duplicada"));
        servidor.verify();
    }

    @Test
    void validaElDigitoDeControlDelGtin() {
        assertFalse(publicador.gtinValido("12345678"));
        assertTrue(publicador.gtinValido("7898945080293"));
    }

    @Test
    void noEnviaFamilyNameNiTitleAlActualizarUnUserProductExistente() {
        ReflectionTestUtils.setField(publicador, "userProducts", true);
        Producto producto = productoBase();
        producto.setMercadoLibreId("MLA123456789");

        Map<String, Object> payload = publicador.construirPayload(producto, false);

        assertFalse(payload.containsKey("family_name"));
        assertFalse(payload.containsKey("title"));
        assertEquals(new BigDecimal("1000.00"), payload.get("price"));
        assertEquals(4, payload.get("available_quantity"));
    }

    @Test
    void noEnviaFamilyNameAlActualizarUnItemDeVarianteExistente() {
        ReflectionTestUtils.setField(publicador, "userProducts", true);
        Producto producto = productoBase();
        producto.setId(10L);
        ProductoVariante variante = new ProductoVariante();
        variante.setProducto(producto);
        variante.setSku("SKU-L");
        variante.setTalle("L");
        variante.setStock(2);
        variante.setPrecioContado(new BigDecimal("1300.00"));
        when(variantes.findByProductoIdOrderByNombreAsc(10L)).thenReturn(List.of(variante));

        Map<String, Object> payload = publicador.construirPayloadVarianteUserProduct(producto, variante, false);

        assertFalse(payload.containsKey("family_name"));
        assertFalse(payload.containsKey("title"));
    }

    @Test
    void permiteLaMismaFotoParaVariantesDeDistintoColor() {
        Producto producto = productoBase();
        ProductoVariante naranja = varianteVisual(producto, "Naranja", "foto-igual");
        ProductoVariante coral = varianteVisual(producto, "Coral", "foto-igual");

        assertDoesNotThrow(() -> publicador.construirPayloadVarianteUserProduct(producto, naranja, true));
        assertDoesNotThrow(() -> publicador.construirPayloadVarianteUserProduct(producto, coral, true));
    }

    @Test
    @SuppressWarnings("unchecked")
    void usaPresentacionPersonalizadaSiFaltanAtributosDeVariacion() {
        Producto producto = productoBase();
        producto.setId(10L);
        ProductoVariante primera = new ProductoVariante();
        primera.setProducto(producto); primera.setSku("MANT-003-001"); primera.setNombre("Presentación 1");
        primera.setStock(2); primera.setPrecioContado(BigDecimal.TEN);
        ProductoVariante segunda = new ProductoVariante();
        segunda.setProducto(producto); segunda.setSku("MANT-003-002"); segunda.setNombre("Presentación 2");
        segunda.setStock(3); segunda.setPrecioContado(BigDecimal.TEN);
        when(variantes.findByProductoIdOrderByNombreAsc(10L)).thenReturn(List.of(primera, segunda));

        Map<String, Object> payload = publicador.construirPayload(producto, true);

        List<Map<String, Object>> variaciones = (List<Map<String, Object>>) payload.get("variations");
        List<Map<String, Object>> primeraCombinacion =
                (List<Map<String, Object>>) variaciones.get(0).get("attribute_combinations");
        List<Map<String, Object>> segundaCombinacion =
                (List<Map<String, Object>>) variaciones.get(1).get("attribute_combinations");

        assertEquals("Presentación", primeraCombinacion.get(0).get("name"));
        assertEquals("Presentación 1", primeraCombinacion.get(0).get("value_name"));
        assertEquals("Presentación 2", segundaCombinacion.get(0).get("value_name"));
    }

    private ProductoVariante varianteVisual(Producto producto, String color, String foto) {
        ProductoVariante variante = new ProductoVariante();
        variante.setProducto(producto);
        variante.setSku("SKU-" + color);
        variante.setColor(color);
        variante.setMercadoLibreAtributosJson("{\"COLOR\":\"" + color + "\",\"SIZE\":\"M\"}");
        variante.setFotoContenido(foto.getBytes(StandardCharsets.UTF_8));
        variante.setFotoNombre("foto.jpg");
        variante.setFotoTipoContenido("image/jpeg");
        return variante;
    }

    private Producto productoBase() {
        Producto producto = new Producto();
        producto.setSku("SKU-1");
        producto.setDescripcion("Producto de prueba");
        producto.setCantidad(4);
        producto.setPrecioContado(new BigDecimal("1000.00"));
        producto.setMercadoLibreCategoriaId("MLA1234");
        producto.setFotoUrlExterna("https://img.test/producto.jpg");
        return producto;
    }
}

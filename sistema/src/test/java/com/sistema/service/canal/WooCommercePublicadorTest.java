package com.sistema.service.canal;

import com.sistema.model.Producto;
import com.sistema.model.ProductoVariante;
import com.sistema.repository.ProductoVarianteRepository;
import com.sistema.service.WooCommerceCredencialesService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;

class WooCommercePublicadorTest {

    private final WooCommercePublicador publicador =
            new WooCommercePublicador(mock(ProductoVarianteRepository.class),
                    mock(com.sistema.service.WooCommerceCredencialesService.class));

    @Test
    void enviaAtributosVisiblesEnUnaPresentacionSimple() {
        ProductoVariante unica = variante("{\"BRAND\":\"Nike\",\"MODEL\":\"Air\",\"SIZE\":\"M\"}");

        List<Map<String, Object>> atributos = publicador.atributosWoo(List.of(unica));

        assertEquals(3, atributos.size());
        assertTrue(atributos.stream().allMatch(a -> Boolean.TRUE.equals(a.get("visible"))));
        assertTrue(atributos.stream().allMatch(a -> Boolean.FALSE.equals(a.get("variation"))));
        assertTrue(atributos.stream().anyMatch(a -> "Marca".equals(a.get("name"))));
        assertTrue(atributos.stream().anyMatch(a -> "Modelo".equals(a.get("name"))));
    }

    @Test
    void soloMarcaComoVariacionLosAtributosQueCambian() {
        ProductoVariante roja = variante("{\"BRAND\":\"Nike\",\"MODEL\":\"Air\",\"COLOR\":\"Rojo\"}");
        ProductoVariante azul = variante("{\"BRAND\":\"Nike\",\"MODEL\":\"Air\",\"COLOR\":\"Azul\"}");

        List<Map<String, Object>> atributos = publicador.atributosWoo(List.of(roja, azul));

        assertTrue(atributos.stream().filter(a -> "Color".equals(a.get("name")))
                .allMatch(a -> Boolean.TRUE.equals(a.get("variation"))));
        assertFalse(atributos.stream().filter(a -> "Marca".equals(a.get("name")) || "Modelo".equals(a.get("name")))
                .anyMatch(a -> Boolean.TRUE.equals(a.get("variation"))));
    }

    @Test
    void unificaLosAliasDeColorYTalleDeMercadoLibreParaWooCommerce() {
        ProductoVariante variante = variante(
                "{\"COLOR\":\"Amarillo\",\"MAIN_COLOR\":\"Amarillo\","
                        + "\"SIZE\":\"M\",\"FILTRABLE_SIZE\":\"M\","
                        + "\"DISPLAY_SIZE\":\"6.8 pulgadas\"}");

        List<Map<String, Object>> atributos = publicador.atributosWoo(List.of(variante));

        assertEquals(3, atributos.size());
        assertTrue(atributos.stream().anyMatch(a -> "Color".equals(a.get("name"))));
        assertTrue(atributos.stream().anyMatch(a -> "Talle".equals(a.get("name"))));
        assertFalse(atributos.stream().anyMatch(a -> "Color principal".equals(a.get("name"))));
        assertFalse(atributos.stream().anyMatch(a -> "Equivalencias".equals(a.get("name"))));
        assertTrue(atributos.stream().anyMatch(a -> "Tamaño de pantalla".equals(a.get("name"))));
    }

    @Test
    void noCreaSelectoresDuplicadosParaVariantesConAliasDeColorYTalle() {
        ProductoVariante talleS = variante(
                "{\"COLOR\":\"Negro\",\"MAIN_COLOR\":\"Negro\","
                        + "\"SIZE\":\"S\",\"FILTRABLE_SIZE\":\"S\"}");
        ProductoVariante talleL = variante(
                "{\"COLOR\":\"Negro\",\"MAIN_COLOR\":\"Negro\","
                        + "\"SIZE\":\"L\",\"FILTRABLE_SIZE\":\"L\"}");

        List<Map<String, Object>> atributos = publicador.atributosWoo(List.of(talleS, talleL));

        assertEquals(2, atributos.size());
        assertTrue(atributos.stream().filter(a -> "Talle".equals(a.get("name")))
                .allMatch(a -> Boolean.TRUE.equals(a.get("variation"))));
        assertTrue(atributos.stream().filter(a -> "Color".equals(a.get("name")))
                .allMatch(a -> Boolean.FALSE.equals(a.get("variation"))));
    }

    @Test
    void excluyeMetadatosInternosDeMercadoLibreTambienEnLasVariantes() {
        ProductoVariante variante = variante("""
                {
                  "COLOR":"Negro",
                  "SIZE":"M",
                  "WITH_VIRTUAL_TRY_ON":"Sí",
                  "SELLER_PACKAGE_HEIGHT":"40 cm",
                  "SELLER_PACKAGE_WIDTH":"25 cm",
                  "VALUE_ADDED_TAX":"21 %",
                  "SIZE_GRID_ROW_ID":"123:4"
                }
                """);

        List<Map<String, Object>> atributos = publicador.atributosWoo(List.of(variante));

        assertEquals(2, atributos.size());
        assertTrue(atributos.stream().anyMatch(a -> "Color".equals(a.get("name"))));
        assertTrue(atributos.stream().anyMatch(a -> "Talle".equals(a.get("name"))));
        assertFalse(atributos.toString().contains("VIRTUAL_TRY_ON"));
        assertFalse(atributos.toString().contains("PACKAGE"));
        assertFalse(atributos.toString().contains("IVA"));
        assertFalse(atributos.toString().contains("GRID"));
    }

    @Test
    void actualizaProductoExistenteCuandoWooCommerceYaTieneElSku() {
        ProductoVarianteRepository variantes = mock(ProductoVarianteRepository.class);
        WooCommerceCredencialesService credenciales = mock(WooCommerceCredencialesService.class);
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
        WooCommercePublicador publicador = new WooCommercePublicador(variantes, credenciales, builder.build());
        Producto producto = new Producto();
        producto.setId(10L);
        producto.setSku("CELU-001");
        producto.setDescripcion("Celular");
        producto.setMercadoLibreDescripcion("DescripciÃ³n completa del producto.\nSegunda lÃ­nea.");
        producto.setMercadoLibreMarca("Samsung");
        producto.setMercadoLibreModelo("Galaxy S24");
        producto.setMercadoLibreAtributosJson("""
                [
                  {"id":"DISPLAY_SIZE","name":"TamaÃ±o de pantalla","value_name":"6.8 pulgadas"},
                  {"id":"PROCESSOR_MODEL","name":"Modelo del procesador","value_name":"Snapdragon"},
                  {"id":"SELLER_PACKAGE_HEIGHT","name":"Alto del paquete","value_name":"20 cm"}
                ]
                """);
        producto.setPrecioContado(new BigDecimal("1000"));
        producto.setCantidad(5);
        when(variantes.findByProductoIdOrderByNombreAsc(10L)).thenReturn(List.of());
        when(credenciales.configurado()).thenReturn(true);
        when(credenciales.obtener()).thenReturn(
                new WooCommerceCredencialesService.Credenciales("https://woo.test", "ck_test", "cs_test"));

        servidor.expect(requestTo(
                        "https://woo.test/wp-json/wc/v3/products?sku=CELU-001&status=any&per_page=100"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(
                        "https://woo.test/wp-json/wc/v3/products?sku=CELU-001&status=trash&per_page=100"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[{\"id\":2396,\"sku\":\"CELU-001\"}]", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("https://woo.test/wp-json/wc/v3/products/2396"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"sku\":\"CELU-001\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "\"description\":\"<p>DescripciÃ³n completa del producto.<br>Segunda lÃ­nea.</p>\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"short_description\":\"<ul>")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"name\":\"Marca\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"options\":[\"Samsung\"]")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "\"name\":\"TamaÃ±o de pantalla\"")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Alto del paquete"))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"stock_quantity\":5")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"stock_status\":\"instock\"")))
                .andRespond(withSuccess("{\"id\":2396,\"sku\":\"CELU-001\"}", MediaType.APPLICATION_JSON));

        ResultadoPublicacion resultado = publicador.publicar(producto, null);

        assertEquals("2396", resultado.idExterno());
        servidor.verify();
    }

    @Test
    void reintentaUnaActualizacionCuandoWooCommerceCierraLaConexionSinResponder() {
        ProductoVarianteRepository variantes = mock(ProductoVarianteRepository.class);
        WooCommerceCredencialesService credenciales = mock(WooCommerceCredencialesService.class);
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
        WooCommercePublicador publicador =
                new WooCommercePublicador(variantes, credenciales, builder.build());
        Producto producto = new Producto();
        producto.setId(20L);
        producto.setSku("ZAPA-001");
        producto.setDescripcion("Zapatilla");
        producto.setPrecioContado(new BigDecimal("1000"));
        producto.setCantidad(1);
        when(variantes.findByProductoIdOrderByNombreAsc(20L)).thenReturn(List.of());
        when(credenciales.configurado()).thenReturn(true);
        when(credenciales.obtener()).thenReturn(
                new WooCommerceCredencialesService.Credenciales(
                        "https://woo.test", "ck_test", "cs_test"));

        servidor.expect(requestTo("https://woo.test/wp-json/wc/v3/products/1692"))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withException(new IOException("HTTP/1.1 header parser received no bytes")));
        servidor.expect(requestTo("https://woo.test/wp-json/wc/v3/products/1692"))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withSuccess("{\"id\":1692,\"sku\":\"ZAPA-001\"}",
                        MediaType.APPLICATION_JSON));

        ResultadoPublicacion resultado = publicador.publicar(producto, "1692");

        assertEquals("1692", resultado.idExterno());
        servidor.verify();
    }

    @Test
    void publicaProductoVariableYVariantesConEstadoDeStockExplicito() {
        ProductoVarianteRepository variantes = mock(ProductoVarianteRepository.class);
        WooCommerceCredencialesService credenciales = mock(WooCommerceCredencialesService.class);
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
        WooCommercePublicador publicador = new WooCommercePublicador(variantes, credenciales, builder.build());
        Producto producto = new Producto();
        producto.setId(10L);
        producto.setSku("REME-001");
        producto.setDescripcion("Remera");
        producto.setFotoUrlExterna("https://img.test/remera.jpg");

        ProductoVariante talleM = variante("{\"COLOR\":\"Agua\",\"SIZE\":\"M\"}");
        talleM.setId(1L);
        talleM.setProducto(producto);
        talleM.setSku("REME-001-M");
        talleM.setStock(3);
        talleM.setPrecioContado(new BigDecimal("1000"));
        talleM.setWooCommerceVariationId("101");
        ProductoVariante talleL = variante("{\"COLOR\":\"Agua\",\"SIZE\":\"L\"}");
        talleL.setId(2L);
        talleL.setProducto(producto);
        talleL.setSku("REME-001-L");
        talleL.setStock(0);
        talleL.setPrecioContado(new BigDecimal("1000"));
        talleL.setWooCommerceVariationId("102");

        when(variantes.findByProductoIdOrderByNombreAsc(10L)).thenReturn(List.of(talleL, talleM));
        when(credenciales.configurado()).thenReturn(true);
        when(credenciales.obtener()).thenReturn(
                new WooCommerceCredencialesService.Credenciales("https://woo.test", "ck_test", "cs_test"));

        servidor.expect(requestTo("https://woo.test/wp-json/wc/v3/products/200"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"type\":\"variable\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"manage_stock\":false")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"stock_status\":\"instock\"")))
                .andRespond(withSuccess("{\"id\":200}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("https://woo.test/wp-json/wc/v3/products/200/variations/102"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"stock_quantity\":0")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"stock_status\":\"outofstock\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "\"image\":{\"src\":\"https://img.test/remera.jpg\"}")))
                .andRespond(withSuccess("{\"id\":102}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("https://woo.test/wp-json/wc/v3/products/200/variations/101"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"stock_quantity\":3")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"stock_status\":\"instock\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "\"image\":{\"src\":\"https://img.test/remera.jpg\"}")))
                .andRespond(withSuccess("{\"id\":101}", MediaType.APPLICATION_JSON));

        ResultadoPublicacion resultado = publicador.publicar(producto, "200");

        assertEquals("200", resultado.idExterno());
        servidor.verify();
    }

    @Test
    void publicaUnaUnicaPresentacionComoSimpleConSuStockReal() {
        ProductoVarianteRepository variantes = mock(ProductoVarianteRepository.class);
        WooCommerceCredencialesService credenciales = mock(WooCommerceCredencialesService.class);
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
        WooCommercePublicador publicador = new WooCommercePublicador(variantes, credenciales, builder.build());
        Producto producto = new Producto();
        producto.setId(30L);
        producto.setSku("ASIC-001");
        producto.setDescripcion("Zapatilla Asics");
        ProductoVariante talleUnico = variante("{\"COLOR\":\"Agua\",\"SIZE\":\"41 AR\"}");
        talleUnico.setProducto(producto);
        talleUnico.setSku("ASIC-001-41");
        talleUnico.setStock(6);
        talleUnico.setPrecioContado(new BigDecimal("259900"));

        when(variantes.findByProductoIdOrderByNombreAsc(30L)).thenReturn(List.of(talleUnico));
        when(variantes.findBySkuIgnoreCase("ASIC-001-41")).thenReturn(Optional.of(talleUnico));
        when(credenciales.configurado()).thenReturn(true);
        when(credenciales.obtener()).thenReturn(
                new WooCommerceCredencialesService.Credenciales("https://woo.test", "ck_test", "cs_test"));

        servidor.expect(requestTo("https://woo.test/wp-json/wc/v3/products/300"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"type\":\"simple\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"sku\":\"ASIC-001-41\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"stock_quantity\":6")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"stock_status\":\"instock\"")))
                .andRespond(withSuccess("{\"id\":300}", MediaType.APPLICATION_JSON));

        ResultadoPublicacion resultado = publicador.publicar(producto, "300");

        assertEquals("300", resultado.idExterno());
        servidor.verify();
    }

    @Test
    void recreaProductoCuandoWooCommerceInformaQueElIdGuardadoYaNoEsValido() {
        ProductoVarianteRepository variantes = mock(ProductoVarianteRepository.class);
        WooCommerceCredencialesService credenciales = mock(WooCommerceCredencialesService.class);
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
        WooCommercePublicador publicador = new WooCommercePublicador(variantes, credenciales, builder.build());
        Producto producto = new Producto();
        producto.setId(10L);
        producto.setSku("ANIL-001-001");
        producto.setDescripcion("Anillo");
        producto.setPrecioContado(new BigDecimal("20000"));
        producto.setCantidad(1);
        when(variantes.findByProductoIdOrderByNombreAsc(10L)).thenReturn(List.of());
        when(credenciales.configurado()).thenReturn(true);
        when(credenciales.obtener()).thenReturn(
                new WooCommerceCredencialesService.Credenciales("https://woo.test", "ck_test", "cs_test"));

        servidor.expect(requestTo("https://woo.test/wp-json/wc/v3/products/2534"))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"woocommerce_rest_product_invalid_id\","
                                + "\"message\":\"ID no válido.\",\"data\":{\"status\":400}}"));
        servidor.expect(requestTo(
                        "https://woo.test/wp-json/wc/v3/products?sku=ANIL-001-001&status=any&per_page=100"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(
                        "https://woo.test/wp-json/wc/v3/products?sku=ANIL-001-001&status=trash&per_page=100"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("https://woo.test/wp-json/wc/v3/products"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"id\":3001,\"sku\":\"ANIL-001-001\"}",
                        MediaType.APPLICATION_JSON));

        ResultadoPublicacion resultado = publicador.publicar(producto, "2534");

        assertEquals("3001", resultado.idExterno());
        servidor.verify();
    }

    @Test
    void rechazaProductoSimpleSiSuSkuYaPerteneceAOtraVariante() {
        ProductoVarianteRepository variantes = mock(ProductoVarianteRepository.class);
        WooCommerceCredencialesService credenciales = mock(WooCommerceCredencialesService.class);
        WooCommercePublicador publicador = new WooCommercePublicador(
                variantes, credenciales, RestClient.create());
        Producto producto = new Producto();
        producto.setId(20L);
        producto.setSku("REME-002");
        producto.setDescripcion("Remera independiente");
        Producto propietario = new Producto();
        propietario.setId(10L);
        propietario.setSku("REME-001");
        propietario.setDescripcion("Remera");
        ProductoVariante ocupada = new ProductoVariante();
        ocupada.setProducto(propietario);
        ocupada.setSku("REME-002");
        when(variantes.findByProductoIdOrderByNombreAsc(20L)).thenReturn(List.of());
        when(variantes.findBySkuIgnoreCase("REME-002")).thenReturn(Optional.of(ocupada));
        when(credenciales.configurado()).thenReturn(true);
        when(credenciales.obtener()).thenReturn(
                new WooCommerceCredencialesService.Credenciales("https://woo.test", "ck_test", "cs_test"));

        IllegalStateException error = assertThrows(
                IllegalStateException.class, () -> publicador.publicar(producto, null));

        assertTrue(error.getMessage().contains("REME-001 / Remera"));
        assertTrue(error.getMessage().contains("SKU únicos"));
    }

    private ProductoVariante variante(String atributos) {
        ProductoVariante variante = new ProductoVariante();
        variante.setSku("SKU");
        variante.setMercadoLibreAtributosJson(atributos);
        return variante;
    }
}

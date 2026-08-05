package com.sistema.service.canal;

import com.sistema.model.Producto;
import com.sistema.model.ProductoVariante;
import com.sistema.repository.ProductoVarianteRepository;
import com.sistema.service.TiendanubeCredencialesService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class TiendaNubePublicadorTest {
    @Test
    void actualizaProductoSimpleConLosEndpointsDeProductoYVarianteSeparados() {
        ProductoVarianteRepository variantes = mock(ProductoVarianteRepository.class);
        TiendanubeCredencialesService credenciales = mock(TiendanubeCredencialesService.class);
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
        TiendanubePublicador publicador = new TiendanubePublicador(variantes, credenciales, builder.build());

        Producto producto = new Producto();
        producto.setId(577L);
        producto.setSku("ANIL-001-001");
        producto.setDescripcion("Anillo de oro y diamante");
        producto.setPrecioContado(new BigDecimal("20000"));
        ProductoVariante variante = variante(producto, "ANIL-001-001", "12", "Amarillo", 3);

        when(variantes.findByProductoIdOrderByNombreAsc(577L)).thenReturn(List.of(variante));
        configurarCredenciales(credenciales);

        servidor.expect(requestTo("https://api.tiendanube.com/v1/555/products/999"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(content().string(not(containsString("\"variants\""))))
                .andExpect(content().string(not(containsString("\"images\""))))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("https://api.tiendanube.com/v1/555/products/999/variants"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[{\"id\":888,\"sku\":\"ANIL-001-001\"}]", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("https://api.tiendanube.com/v1/555/products/999/variants/888"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(content().string(not(containsString("\"variants\""))))
                .andExpect(content().string(not(containsString("stock_management"))))
                .andRespond(withSuccess("{\"id\":888,\"sku\":\"ANIL-001-001\"}", MediaType.APPLICATION_JSON));

        ResultadoPublicacion resultado = publicador.publicar(producto, "999");

        assertEquals("999", resultado.idExterno());
        assertEquals("888", variante.getTiendaNubeVariationId());
        servidor.verify();
    }

    @Test
    void publicaSoloAtributosQueDefinenLasVariantes() {
        ProductoVarianteRepository variantes = mock(ProductoVarianteRepository.class);
        TiendanubeCredencialesService credenciales = mock(TiendanubeCredencialesService.class);
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
        TiendanubePublicador publicador = new TiendanubePublicador(variantes, credenciales, builder.build());

        Producto producto = new Producto();
        producto.setId(554L);
        producto.setSku("CAMP-002");
        producto.setDescripcion("Campera micropolar");
        producto.setPrecioContado(new BigDecimal("129999"));
        ProductoVariante xl = variante(producto, "CAMP-002-001", "XL", null, 1);
        xl.setMercadoLibreAtributosJson(
                "{\"FILTRABLE_SIZE\":\"XL\",\"SELLER_PACKAGE_HEIGHT\":\"37 cm\",\"SIZE\":\"XL\"}");
        ProductoVariante s = variante(producto, "CAMP-002-002", "S", null, 1);
        s.setMercadoLibreAtributosJson(
                "{\"FILTRABLE_SIZE\":\"S\",\"SELLER_PACKAGE_HEIGHT\":\"36 cm\",\"SIZE\":\"S\"}");

        when(variantes.findByProductoIdOrderByNombreAsc(554L)).thenReturn(List.of(xl, s));
        configurarCredenciales(credenciales);

        servidor.expect(requestTo("https://api.tiendanube.com/v1/555/products"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("\"attributes\":[{\"es\":\"Talle\"}]")))
                .andExpect(content().string(containsString("\"values\":[{\"es\":\"XL\"}]")))
                .andExpect(content().string(containsString("\"values\":[{\"es\":\"S\"}]")))
                .andExpect(content().string(not(containsString("Equivalencias"))))
                .andExpect(content().string(not(containsString("Seller package"))))
                .andRespond(withSuccess(
                        "{\"id\":321,\"variants\":[{\"id\":901,\"sku\":\"CAMP-002-001\"},"
                                + "{\"id\":902,\"sku\":\"CAMP-002-002\"}]}",
                        MediaType.APPLICATION_JSON));

        ResultadoPublicacion resultado = publicador.publicar(producto, null);

        assertEquals("321", resultado.idExterno());
        assertEquals("901", xl.getTiendaNubeVariationId());
        assertEquals("902", s.getTiendaNubeVariationId());
        servidor.verify();
    }

    @Test
    void recreaProductoCuandoElIdGuardadoYaNoExiste() {
        ProductoVarianteRepository variantes = mock(ProductoVarianteRepository.class);
        TiendanubeCredencialesService credenciales = mock(TiendanubeCredencialesService.class);
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
        TiendanubePublicador publicador = new TiendanubePublicador(variantes, credenciales, builder.build());
        ReflectionTestUtils.setField(publicador, "publicBaseUrl", "https://stock.example");

        Producto producto = new Producto();
        producto.setId(457L);
        producto.setSku("ANIL-001");
        producto.setDescripcion("Anillo de oro y diamante");
        producto.setPrecioContado(new BigDecimal("20000"));
        ProductoVariante variante = new ProductoVariante();
        variante.setId(432L);
        variante.setProducto(producto);
        variante.setSku("ANIL-001-001");
        variante.setStock(8);
        variante.setPrecioContado(new BigDecimal("20000"));
        variante.setTiendaNubeVariationId("123456");

        when(variantes.findByProductoIdOrderByNombreAsc(457L)).thenReturn(List.of(variante));
        when(credenciales.configurado()).thenReturn(true);
        when(credenciales.obtener()).thenReturn(
                new TiendanubeCredencialesService.Credenciales("555", "token", "SistemaStock/1.0"));

        servidor.expect(requestTo("https://api.tiendanube.com/v1/555/products/357357629"))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":404,\"message\":\"Not Found\"}"));
        servidor.expect(requestTo("https://api.tiendanube.com/v1/555/products"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(not(containsString("\"id\":\"123456\""))))
                .andRespond(withSuccess(
                        "{\"id\":999,\"variants\":[{\"id\":777,\"sku\":\"ANIL-001-001\"}]}",
                        MediaType.APPLICATION_JSON));

        ResultadoPublicacion resultado = publicador.publicar(producto, "357357629");

        assertEquals("999", resultado.idExterno());
        assertEquals("777", variante.getTiendaNubeVariationId());
        verify(variantes, atLeastOnce()).save(variante);
        servidor.verify();
    }

    private static ProductoVariante variante(Producto producto, String sku, String talle,
                                             String color, int stock) {
        ProductoVariante variante = new ProductoVariante();
        variante.setProducto(producto);
        variante.setSku(sku);
        variante.setTalle(talle);
        variante.setColor(color);
        variante.setStock(stock);
        variante.setPrecioContado(producto.getPrecioContado());
        return variante;
    }

    private static void configurarCredenciales(TiendanubeCredencialesService credenciales) {
        when(credenciales.configurado()).thenReturn(true);
        when(credenciales.obtener()).thenReturn(
                new TiendanubeCredencialesService.Credenciales("555", "token", "SistemaStock/1.0"));
    }
}

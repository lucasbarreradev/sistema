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
}

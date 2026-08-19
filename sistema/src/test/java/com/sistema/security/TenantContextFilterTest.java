package com.sistema.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sistema.service.MercadoLibreTokenService;
import com.sistema.service.TenantPublicResourceService;
import com.sistema.service.TiendanubeCredencialesService;
import com.sistema.service.WooCommerceCredencialesService;
import com.sistema.tenant.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TenantContextFilterTest {
    @Test
    void estableceTenantAntesDeContinuarConWebhookMercadoLibreYSinConsumirElBody() throws Exception {
        verificarWebhookMercadoLibre("/webhooks/mercadolibre");
    }

    @Test
    void estableceTenantEnElCallbackAnteriorDeMercadoLibre() throws Exception {
        verificarWebhookMercadoLibre("/canales/mercadolibre/callback");
    }

    @Test
    void estableceTenantAntesDeAbrirHibernateParaUnaFotoPublica() throws Exception {
        TenantPublicResourceService recursosPublicos = mock(TenantPublicResourceService.class);
        when(recursosPublicos.buscarTenantProducto(933L)).thenReturn(Optional.of(7L));
        TenantContextFilter filtro = new TenantContextFilter(new ObjectMapper(),
                mock(MercadoLibreTokenService.class), mock(WooCommerceCredencialesService.class),
                mock(TiendanubeCredencialesService.class), recursosPublicos);
        String ruta = "/productos/933/foto/woocommerce.jpg";
        MockHttpServletRequest request = new MockHttpServletRequest("GET", ruta);
        request.setServletPath(ruta);

        filtro.doFilter(request, new MockHttpServletResponse(), (procesada, response) ->
                assertEquals(7L, TenantContext.require()));

        assertNull(TenantContext.get());
    }

    private void verificarWebhookMercadoLibre(String ruta) throws Exception {
        MercadoLibreTokenService mercadoLibre = mock(MercadoLibreTokenService.class);
        when(mercadoLibre.resolverTenantPorUsuario(3543745002L)).thenReturn(5L);
        TenantContextFilter filtro = new TenantContextFilter(new ObjectMapper(), mercadoLibre,
                mock(WooCommerceCredencialesService.class), mock(TiendanubeCredencialesService.class),
                mock(TenantPublicResourceService.class));
        String payload = """
                {"user_id":3543745002,"topic":"orders_v2","resource":"/orders/20000175644537"}
                """;
        MockHttpServletRequest request = new MockHttpServletRequest("POST", ruta);
        request.setServletPath(ruta);
        request.setContentType("application/json");
        request.setContent(payload.getBytes(StandardCharsets.UTF_8));

        filtro.doFilter(request, new MockHttpServletResponse(), (procesada, response) -> {
            assertEquals(5L, TenantContext.require());
            assertEquals(payload, new String(procesada.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
        });

        assertNull(TenantContext.get());
    }
}

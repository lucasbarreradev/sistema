package com.sistema.service;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class WebhookSeguridadServiceTest {
    @Test
    void validaFirmaHmacDeWooCommerce() throws Exception {
        String payload = "{\"id\":123}";
        String secreto = "secreto-de-prueba";
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String firma = Base64.getEncoder().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        WebhookSeguridadService seguridad = new WebhookSeguridadService(secreto, "", "500", "");

        assertTrue(seguridad.firmaWooValida(payload, firma));
        assertFalse(seguridad.firmaWooValida(payload + "x", firma));
        assertTrue(seguridad.aplicacionMercadoLibreValida("500"));
        assertFalse(seguridad.aplicacionMercadoLibreValida("999"));
    }

    @Test
    void usaClaveDeCifradoComoRespaldo() {
        WebhookSeguridadService seguridad = new WebhookSeguridadService("", "clave-cifrado", "", "");
        assertTrue(seguridad.configurado());
        assertTrue(seguridad.secretoTiendaNubeValido("clave-cifrado"));
    }

    @Test
    void validaFirmaOficialDeTiendanube() throws Exception {
        String payload = "{\"store_id\":123,\"event\":\"order/paid\",\"id\":456}";
        String clientSecret = "client-secret-de-prueba";
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(clientSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String firma = java.util.HexFormat.of()
                .formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        WebhookSeguridadService seguridad =
                new WebhookSeguridadService("webhook-secret", "", "", clientSecret);

        assertTrue(seguridad.firmaTiendaNubeValida(payload, firma));
        assertFalse(seguridad.firmaTiendaNubeValida(payload + "x", firma));
    }
}

package com.sistema.service;

import com.sistema.model.CanalVenta;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MensajeErrorIntegracionTest {

    @Test
    void explicaLaDireccionPendienteSinMostrarJson() {
        String json = "{\"cause\":[\"address_pending\"],"
                + "\"message\":\"seller.unable_to_list\","
                + "\"error\":\"User is unable to list.\",\"status\":403}";

        String mensaje = MensajeErrorIntegracion.paraUsuario(
                CanalVenta.MERCADO_LIBRE, "403 Forbidden: \"" + json + "\"");

        assertTrue(mensaje.contains("dirección de la cuenta vendedora"));
        assertTrue(mensaje.contains("completá"));
        assertFalse(mensaje.contains("address_pending"));
        assertFalse(mensaje.contains("{"));
    }

    @Test
    void agrupaLosAtributosObligatoriosEnUnMensajeClaro() {
        String json = "{\"cause\":["
                + "{\"type\":\"error\",\"code\":\"field.required.BRAND\","
                + "\"message\":\"El campo \\\"Marca\\\" es obligatorio.\"},"
                + "{\"type\":\"error\",\"code\":\"field.required.MODEL\","
                + "\"message\":\"El campo \\\"Modelo\\\" es obligatorio.\"}],"
                + "\"error\":\"validation_error\",\"status\":400}";

        String mensaje = MensajeErrorIntegracion.paraUsuario(
                CanalVenta.MERCADO_LIBRE, json);

        assertTrue(mensaje.contains("atributos obligatorios"));
        assertTrue(mensaje.contains("Marca"));
        assertTrue(mensaje.contains("Modelo"));
        assertFalse(mensaje.contains("validation_error"));
    }

    @Test
    void conservaElDetalleTecnicoParaElLogPeroNoParaLaPantalla() {
        String json = "{\"message\":\"seller.unable_to_list\","
                + "\"cause\":[\"address_pending\"],\"status\":403}";
        HttpClientErrorException error = HttpClientErrorException.create(
                HttpStatus.FORBIDDEN, "Forbidden", HttpHeaders.EMPTY,
                json.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);

        String detalleTecnico = MensajeErrorIntegracion.detalleTecnico(error);
        String mensajeUsuario = MensajeErrorIntegracion.paraUsuario(
                CanalVenta.MERCADO_LIBRE, error);

        assertTrue(detalleTecnico.contains("address_pending"));
        assertFalse(mensajeUsuario.contains("address_pending"));
        assertFalse(mensajeUsuario.contains("{"));
    }

    @Test
    void traduceUnJsonDesconocidoSinExponerlo() {
        String mensaje = MensajeErrorIntegracion.paraUsuario(
                CanalVenta.MERCADO_LIBRE,
                "400 Bad Request: \"{\"message\":\"technical.code\","
                        + "\"error\":\"unknown_error\",\"status\":400}\"");

        assertTrue(mensaje.contains("rechazó la operación"));
        assertFalse(mensaje.contains("technical.code"));
        assertFalse(mensaje.contains("{"));
    }
}

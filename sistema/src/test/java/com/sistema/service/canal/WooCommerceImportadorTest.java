package com.sistema.service.canal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WooCommerceImportadorTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void normalizaColorTalleYConservaLosDemasAtributosDeVariacion() throws Exception {
        assertEquals("COLOR", WooCommerceImportador.idAtributo(
                JSON.readTree("{\"name\":\"Color\",\"slug\":\"pa_color\"}")));
        assertEquals("SIZE", WooCommerceImportador.idAtributo(
                JSON.readTree("{\"name\":\"Tamaño\",\"slug\":\"pa_tamano\"}")));
        assertEquals("TIPO_DE_AROMA", WooCommerceImportador.idAtributo(
                JSON.readTree("{\"name\":\"Tipo de aroma\",\"slug\":\"pa_tipo-de-aroma\"}")));
    }
}

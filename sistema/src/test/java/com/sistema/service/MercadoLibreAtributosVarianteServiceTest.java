package com.sistema.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class MercadoLibreAtributosVarianteServiceTest {
    private final MercadoLibreAtributosVarianteService service =
            new MercadoLibreAtributosVarianteService(
                    mock(MercadoLibreTokenService.class));
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void agregaContextoAConsultasQueSuelenSerAmbiguas() {
        assertTrue(service.prepararConsultaCategoria("Manopla Kitchen")
                .startsWith("agarradera manopla de cocina"));
        assertTrue(service.prepararConsultaCategoria("Vela Cedro Verbena")
                .startsWith("vela decorativa para el hogar"));
        assertTrue(service.prepararConsultaCategoria("Bolsito Capibara")
                .startsWith("cartera bolso pequeño"));
        assertTrue(service.prepararConsultaCategoria(
                        "Set de viaje con envases")
                .startsWith("neceser organizador de viaje"));
    }

    @Test
    void eligeSahumeriosEnLugarDePortaSahumerios() throws Exception {
        var opciones = objectMapper.readTree("""
                [
                  {"category_id":"MLA413742","category_name":"Porta Sahumerios","domain_name":"Porta Sahumerios"},
                  {"category_id":"MLA387586","category_name":"Sahumerios","domain_name":"Inciensos"}
                ]
                """);

        assertEquals("MLA387586",
                service.seleccionarCategoria("INCIENSO - SAHUMERIOS", opciones));
    }

    @Test
    void eligeRopaEnLugarDeAccesoriosDeMotoParaPilotosInfantiles()
            throws Exception {
        var opciones = objectMapper.readTree("""
                [
                  {"category_id":"MLA86375","category_name":"Trajes de Lluvia","domain_name":"Trajes de Lluvia para Motos"},
                  {"category_id":"MLA109096","category_name":"Pilotos","domain_name":"Camperas y Abrigos Infantiles"}
                ]
                """);

        assertEquals("MLA109096", service.seleccionarCategoria(
                "Piloto Infantil Lluvia Kuromi Wabro", opciones));
    }
}

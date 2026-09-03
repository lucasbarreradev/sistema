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
        assertTrue(service.prepararConsultaCategoria("Set x 6 Vasos")
                .startsWith("vasos para beber vajilla"));
        assertEquals("Posavasos de madera",
                service.prepararConsultaCategoria("Posavasos de madera"));
        assertEquals("Vasos termicos",
                service.prepararConsultaCategoria("Vasos termicos"));
        assertTrue(service.prepararConsultaCategoria(
                        "Set de baño acrílico premium")
                .startsWith("set de accesorios para bano"));
        assertEquals("Muneca", service.prepararConsultaCategoria("Muñeca"));
        assertTrue(service.prepararConsultaCategoria(
                        "Riñonera Helena Amelie Black")
                .startsWith("rinonera bolso de cintura accesorio de moda"));
        assertTrue(service.prepararConsultaCategoria("Manopla Kitchen")
                .startsWith("agarradera manopla de cocina"));
        assertTrue(service.prepararConsultaCategoria("Vela Cedro Verbena")
                .startsWith("vela decorativa para el hogar"));
        assertTrue(service.prepararConsultaCategoria("Bolsito Capibara")
                .startsWith("cartera bolso pequeño"));
        assertTrue(service.prepararConsultaCategoria(
                        "Set de viaje con envases")
                .startsWith("neceser organizador de viaje"));
        assertTrue(service.prepararConsultaCategoria(
                        "Botellón Suavizante/Jabón Liquido")
                .startsWith("dispensador botellon para jabon liquido y suavizante lavadero"));
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
    void eligeRinonerasGeneralesAunqueOtroResultadoAparezcaPrimero()
            throws Exception {
        var opciones = objectMapper.readTree("""
                [
                  {"category_id":"MLA410888","category_name":"Verduras","domain_name":"Verduras y hortalizas"},
                  {"category_id":"MLA414321","category_name":"Riñoneras","domain_name":"Riñoneras"},
                  {"category_id":"MLA417710","category_name":"Riñoneras","domain_name":"Riñoneras"}
                ]
                """);

        assertEquals("MLA417710", service.seleccionarCategoria(
                "Riñonera Helena Amelie Black Color Negro", opciones));
    }

    @Test
    void eligeSetsDeAccesoriosParaUnSetDeBanoAunqueNoSeaElPrimero()
            throws Exception {
        var opciones = objectMapper.readTree("""
                [
                  {"category_id":"MLA74001","category_name":"Juguetes para el Baño","domain_name":"Juguetes de baño"},
                  {"category_id":"MLA74506","category_name":"Sets Completos","domain_name":"Kits de grifería para baño"},
                  {"category_id":"MLA31032","category_name":"Sets de Accesorios","domain_name":"Kits de accesorios para baño"}
                ]
                """);

        assertEquals("MLA31032", service.seleccionarCategoria(
                "SET DE BAÑO ACRÍLICO PREMIUM", opciones));
    }

    @Test
    void eligeVasosParaBeberEnLugarDeFrutas() throws Exception {
        var opciones = objectMapper.readTree("""
                [
                  {"category_id":"MLA455431","category_name":"Frutas","domain_name":"Frutas"},
                  {"category_id":"MLA1594","category_name":"Vasos y Copas","domain_name":"Vasos y copas"},
                  {"category_id":"MLA457489","category_name":"Vasos","domain_name":"Vasos y copas"}
                ]
                """);

        assertEquals("MLA457489", service.seleccionarCategoria(
                "Set x 6 Vasos de vidrio", opciones));
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

    @Test
    void eligeDispenserDeJabonEnLugarDeMedialunasYFacturas()
            throws Exception {
        var opciones = objectMapper.readTree("""
                [
                  {"category_id":"MLA410902","category_name":"Medialunas y Facturas","domain_name":"Productos de Panadería"},
                  {"category_id":"MLA412620","category_name":"Dispensers de Jabón","domain_name":"Dispensadores manuales de jabón y detergente"},
                  {"category_id":"MLA412631","category_name":"Dispensadores de Bebidas","domain_name":"Dispensadores de bebidas"}
                ]
                """);

        assertEquals("MLA412620", service.seleccionarCategoria(
                "Botellón Suavizante/Jabón Liquido", opciones));
    }
}

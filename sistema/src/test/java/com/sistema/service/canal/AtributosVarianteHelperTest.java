package com.sistema.service.canal;

import com.sistema.model.CanalVenta;
import com.sistema.model.ProductoVariante;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AtributosVarianteHelperTest {

    @Test
    void mantieneAtributosIndependientesParaCadaCanal() {
        ProductoVariante variante = new ProductoVariante();
        variante.setMercadoLibreAtributosJson("{\"COLOR\":\"Negro ML\"}");
        variante.setWooCommerceAtributosJson("{\"COLOR\":\"Negro Woo\"}");
        variante.setTiendaNubeAtributosJson("{\"COLOR\":\"Negro TN\"}");

        assertEquals("Negro ML", AtributosVarianteHelper.obtener(
                variante, CanalVenta.MERCADO_LIBRE).get("COLOR"));
        assertEquals("Negro Woo", AtributosVarianteHelper.obtener(
                variante, CanalVenta.WOOCOMMERCE).get("COLOR"));
        assertEquals("Negro TN", AtributosVarianteHelper.obtener(
                variante, CanalVenta.TIENDANUBE).get("COLOR"));
    }

    @Test
    void completaWooYTiendaNubeConMercadoLibreSinPisarSusValoresPropios() {
        ProductoVariante variante = new ProductoVariante();
        variante.setMercadoLibreAtributosJson(
                "{\"COLOR\":\"Negro ML\",\"MATERIAL\":\"Cuero\"}");
        variante.setWooCommerceAtributosJson("{\"COLOR\":\"Negro Woo\"}");
        variante.setTiendaNubeAtributosJson("{\"SIZE\":\"Grande TN\"}");

        assertEquals(Map.of("COLOR", "Negro Woo", "MATERIAL", "Cuero"),
                AtributosVarianteHelper.obtener(variante, CanalVenta.WOOCOMMERCE));
        assertEquals(Map.of("COLOR", "Negro ML", "MATERIAL", "Cuero", "SIZE", "Grande TN"),
                AtributosVarianteHelper.obtener(variante, CanalVenta.TIENDANUBE));
    }
}

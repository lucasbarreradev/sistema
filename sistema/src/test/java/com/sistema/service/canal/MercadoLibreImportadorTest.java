package com.sistema.service.canal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sistema.dto.ProductoCanalImportado;
import com.sistema.service.MercadoLibreTokenService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class MercadoLibreImportadorTest {

    @Test
    void agrupaItemsUserProductsComoVariantesDeUnaFamilia() {
        MercadoLibreImportador importador = new MercadoLibreImportador(
                mock(MercadoLibreTokenService.class), new ObjectMapper());

        ProductoCanalImportado talleM = item("MLA1", "REM-001-M", "M", "Negro", 3);
        ProductoCanalImportado talleL = item("MLA2", "REM-001-L", "L", "Negro", 5);

        List<ProductoCanalImportado> resultado = importador.agruparUserProducts(List.of(talleM, talleL));

        assertEquals(1, resultado.size());
        ProductoCanalImportado familia = resultado.get(0);
        assertEquals("Remera Puma", familia.descripcion());
        assertNull(familia.sku());
        assertEquals(8, familia.cantidad());
        assertEquals(2, familia.variantes().size());
        assertTrue(familia.variantes().stream().allMatch(v -> v.itemMercadoLibre()));
        assertTrue(familia.variantes().stream().allMatch(v -> v.fotoUrl() != null));
        assertEquals(List.of("MLA1", "MLA2"), familia.variantes().stream()
                .map(v -> v.idExterno()).toList());
        assertEquals(List.of("M", "L"), familia.variantes().stream()
                .map(v -> v.talle()).toList());
    }

    private ProductoCanalImportado item(String id, String sku, String talle, String color, int stock) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("familyId", "MLAF123");
        datos.put("familyName", "Remera Puma");
        datos.put("atributosItem", new LinkedHashMap<>(Map.of(
                "BRAND", "Puma", "COLOR", color, "SIZE", talle, "SELLER_SKU", sku)));
        return new ProductoCanalImportado(id, sku, "Remera Puma " + color + " " + talle,
                stock, new BigDecimal("2000"), "https://img.test/" + id + ".jpg",
                "MLA448691", datos, List.of());
    }
}

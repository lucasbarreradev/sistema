package com.sistema.service.canal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sistema.dto.VarianteCanalImportada;
import com.sistema.service.TiendanubeCredencialesService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class TiendanubeImportadorTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final TiendanubeImportador importador =
            new TiendanubeImportador(mock(TiendanubeCredencialesService.class));

    @Test
    void conservaColorYTalleSegunElNombreDelAtributo() throws Exception {
        JsonNode producto = mapper.readTree("""
                {
                  "attributes": [{"es":"Color"},{"es":"Talle"}],
                  "variants": [{
                    "id": 101, "sku": "REM-AZ-M", "stock": 3, "price": "1250",
                    "values": [{"es":"Azul"},{"es":"M"}]
                  }]
                }
                """);

        List<VarianteCanalImportada> variantes = importador.mapearVariantes(producto);

        assertEquals(1, variantes.size());
        assertEquals("Azul", variantes.get(0).color());
        assertEquals("M", variantes.get(0).talle());
        assertEquals("Azul", variantes.get(0).atributos().get("COLOR"));
        assertEquals("M", variantes.get(0).atributos().get("SIZE"));
        assertEquals("Azul / M", variantes.get(0).nombre());
    }
}

package com.sistema.service.canal;

import com.fasterxml.jackson.databind.JsonNode;
import com.sistema.dto.ProductoCanalImportado;
import com.sistema.dto.VarianteCanalImportada;
import com.sistema.model.CanalVenta;
import com.sistema.service.TiendanubeCredencialesService;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class TiendanubeImportador implements ImportadorCanal {
    private final RestClient restClient = RestClient.create();
    private final TiendanubeCredencialesService credenciales;

    public TiendanubeImportador(TiendanubeCredencialesService credenciales) {
        this.credenciales = credenciales;
    }

    public CanalVenta canal() { return CanalVenta.TIENDANUBE; }
    public boolean configurado() { return credenciales.configurado(); }

    public List<ProductoCanalImportado> obtenerProductos() {
        if (!configurado()) throw new IllegalStateException("Tiendanube no está configurada");
        TiendanubeCredencialesService.Credenciales c = credenciales.obtener();
        List<ProductoCanalImportado> productos = new ArrayList<>();
        for (int pagina = 1; ; pagina++) {
            JsonNode body = restClient.get()
                    .uri("https://api.tiendanube.com/v1/" + c.storeId() + "/products?per_page=200&page=" + pagina)
                    .headers(h -> { h.setBearerAuth(c.token()); h.set("User-Agent", c.userAgent()); })
                    .retrieve().body(JsonNode.class);
            if (body == null || !body.isArray() || body.isEmpty()) break;
            for (JsonNode p : body) productos.add(mapear(p));
            if (body.size() < 200) break;
        }
        return productos;
    }

    private ProductoCanalImportado mapear(JsonNode p) {
        JsonNode variante = p.path("variants").isArray() && !p.path("variants").isEmpty() ? p.path("variants").get(0) : p;
        String nombre = localizado(p.path("name"));
        String imagen = p.path("images").isArray() && !p.path("images").isEmpty()
                ? p.path("images").get(0).path("src").asText(null) : null;
        return new ProductoCanalImportado(p.path("id").asText(), variante.path("sku").asText(), nombre,
                variante.path("stock").asInt(0), decimal(variante.path("price").asText("0")), imagen, null, Map.of(), mapearVariantes(p));
    }

    private List<VarianteCanalImportada> mapearVariantes(JsonNode producto) {
        List<VarianteCanalImportada> resultado = new ArrayList<>();
        if (!producto.path("variants").isArray() || producto.path("variants").size() <= 1) return resultado;
        for (JsonNode v : producto.path("variants")) {
            String talle = v.path("values").size() > 0 ? localizado(v.path("values").get(0)) : "";
            String color = v.path("values").size() > 1 ? localizado(v.path("values").get(1)) : "";
            resultado.add(new VarianteCanalImportada(v.path("id").asText(), v.path("sku").asText(),
                    String.join(" / ", talle, color).replaceAll("(^ / | / $)", ""), talle, color,
                    v.path("stock").asInt(0), decimal(v.path("price").asText("0")), v.path("barcode").asText(null), null,
                    v.path("barcode").asText(null), Map.of(), null, false));
        }
        return resultado;
    }

    private String localizado(JsonNode nodo) {
        if (nodo.isTextual()) return nodo.asText();
        if (nodo.hasNonNull("es")) return nodo.path("es").asText();
        if (nodo.fields().hasNext()) return nodo.fields().next().getValue().asText();
        return "Producto sin nombre";
    }
    private BigDecimal decimal(String value) { try { return new BigDecimal(value); } catch (Exception e) { return BigDecimal.ZERO; } }
}

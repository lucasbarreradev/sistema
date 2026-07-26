package com.sistema.service.canal;

import com.fasterxml.jackson.databind.JsonNode;
import com.sistema.dto.ProductoCanalImportado;
import com.sistema.dto.VarianteCanalImportada;
import com.sistema.model.CanalVenta;
import com.sistema.service.WooCommerceCredencialesService;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class WooCommerceImportador implements ImportadorCanal {
    private final RestClient restClient = RestClient.create();
    private final WooCommerceCredencialesService credenciales;

    public WooCommerceImportador(WooCommerceCredencialesService credenciales) {
        this.credenciales = credenciales;
    }

    public CanalVenta canal() { return CanalVenta.WOOCOMMERCE; }
    public boolean configurado() { return credenciales.configurado(); }

    public List<ProductoCanalImportado> obtenerProductos() {
        if (!configurado()) throw new IllegalStateException("WooCommerce no está configurado");
        WooCommerceCredencialesService.Credenciales c = credenciales.obtener();
        List<ProductoCanalImportado> productos = new ArrayList<>();
        for (int pagina = 1; ; pagina++) {
            JsonNode body = restClient.get()
                    .uri(c.url() + "/wp-json/wc/v3/products?per_page=100&page=" + pagina)
                    .headers(h -> h.setBasicAuth(c.key(), c.secret())).retrieve().body(JsonNode.class);
            if (body == null || !body.isArray() || body.isEmpty()) break;
            for (JsonNode p : body) productos.add(mapear(p, c));
            if (body.size() < 100) break;
        }
        return productos;
    }

    private ProductoCanalImportado mapear(JsonNode p, WooCommerceCredencialesService.Credenciales c) {
        String imagen = p.path("images").isArray() && !p.path("images").isEmpty()
                ? p.path("images").get(0).path("src").asText(null) : null;
        String precioTexto = p.path("regular_price").asText(p.path("price").asText("0"));
        return new ProductoCanalImportado(p.path("id").asText(), p.path("sku").asText(),
                p.path("name").asText(), p.path("stock_quantity").isNull() ? 0 : p.path("stock_quantity").asInt(0),
                decimal(precioTexto), imagen, null, Map.of(), mapearVariantes(p, c));
    }

    private List<VarianteCanalImportada> mapearVariantes(JsonNode producto,
                                                          WooCommerceCredencialesService.Credenciales c) {
        List<VarianteCanalImportada> resultado = new ArrayList<>();
        if (!"variable".equals(producto.path("type").asText()) || !producto.path("variations").isArray()) return resultado;
        for (JsonNode id : producto.path("variations")) {
            JsonNode v = restClient.get().uri(c.url() + "/wp-json/wc/v3/products/" + producto.path("id").asText() + "/variations/" + id.asText())
                    .headers(h -> h.setBasicAuth(c.key(), c.secret())).retrieve().body(JsonNode.class);
            if (v == null) continue;
            String talle = "", color = "";
            for (JsonNode atributo : v.path("attributes")) {
                if ("talle".equalsIgnoreCase(atributo.path("name").asText())) talle = atributo.path("option").asText();
                if ("color".equalsIgnoreCase(atributo.path("name").asText())) color = atributo.path("option").asText();
            }
            resultado.add(new VarianteCanalImportada(v.path("id").asText(), v.path("sku").asText(),
                    String.join(" / ", talle, color).replaceAll("(^ / | / $)", ""), talle, color,
                    v.path("stock_quantity").asInt(0), decimal(v.path("regular_price").asText("0")), null, null, null, Map.of(), null, false));
        }
        return resultado;
    }

    private BigDecimal decimal(String value) { try { return new BigDecimal(value); } catch (Exception e) { return BigDecimal.ZERO; } }
}

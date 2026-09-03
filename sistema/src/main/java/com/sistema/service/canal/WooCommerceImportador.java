package com.sistema.service.canal;

import com.fasterxml.jackson.databind.JsonNode;
import com.sistema.dto.ProductoCanalImportado;
import com.sistema.dto.VarianteCanalImportada;
import com.sistema.model.CanalVenta;
import com.sistema.service.WooCommerceCredencialesService;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;

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
        return obtenerProductos(false, () -> false);
    }

    @Override
    public List<ProductoCanalImportado> obtenerProductos(
            boolean incluirInactivas, BooleanSupplier cancelacionSolicitada) {
        if (!configurado()) throw new IllegalStateException("WooCommerce no está configurado");
        WooCommerceCredencialesService.Credenciales c = credenciales.obtener();
        List<ProductoCanalImportado> productos = new ArrayList<>();
        for (int pagina = 1; ; pagina++) {
            if (cancelacionSolicitada.getAsBoolean()) break;
            JsonNode body = restClient.get()
                    .uri(c.url() + "/wp-json/wc/v3/products?per_page=100&page=" + pagina)
                    .headers(h -> h.setBasicAuth(c.key(), c.secret())).retrieve().body(JsonNode.class);
            if (body == null || !body.isArray() || body.isEmpty()) break;
            for (JsonNode p : body) {
                if (cancelacionSolicitada.getAsBoolean()) return productos;
                productos.add(mapear(p, c, cancelacionSolicitada));
            }
            if (body.size() < 100) break;
        }
        return productos;
    }

    private ProductoCanalImportado mapear(JsonNode p, WooCommerceCredencialesService.Credenciales c,
                                           BooleanSupplier cancelacionSolicitada) {
        String imagen = p.path("images").isArray() && !p.path("images").isEmpty()
                ? p.path("images").get(0).path("src").asText(null) : null;
        String precioTexto = p.path("regular_price").asText(p.path("price").asText("0"));
        Map<String, Object> datos = new LinkedHashMap<>();
        List<Map<String, String>> categorias = mapearCategorias(p.path("categories"));
        if (!categorias.isEmpty()) datos.put("categorias", categorias);
        if (!p.path("status").asText("").isBlank()) datos.put("estado", p.path("status").asText());
        if (!p.path("description").asText("").isBlank()) {
            datos.put("descripcion", p.path("description").asText());
        }
        if (p.path("attributes").isArray() && !p.path("attributes").isEmpty()) {
            datos.put("atributosJson", p.path("attributes").toString());
        }
        return new ProductoCanalImportado(p.path("id").asText(), p.path("sku").asText(),
                p.path("name").asText(), p.path("stock_quantity").isNull() ? 0 : p.path("stock_quantity").asInt(0),
                decimal(precioTexto), imagen, null, datos,
                mapearVariantes(p, c, cancelacionSolicitada));
    }

    private List<Map<String, String>> mapearCategorias(JsonNode lista) {
        List<Map<String, String>> resultado = new ArrayList<>();
        if (!lista.isArray()) return resultado;
        for (JsonNode categoria : lista) {
            String id = categoria.path("id").asText("").trim();
            if (id.isBlank()) continue;
            String nombre = categoria.path("name").asText(id).trim();
            resultado.add(Map.of("id", id, "nombre", nombre.isBlank() ? id : nombre));
        }
        return resultado;
    }

    private List<VarianteCanalImportada> mapearVariantes(JsonNode producto,
                                                          WooCommerceCredencialesService.Credenciales c,
                                                          BooleanSupplier cancelacionSolicitada) {
        List<VarianteCanalImportada> resultado = new ArrayList<>();
        if (!"variable".equals(producto.path("type").asText()) || !producto.path("variations").isArray()) return resultado;
        for (JsonNode id : producto.path("variations")) {
            if (cancelacionSolicitada.getAsBoolean()) return resultado;
            JsonNode v = restClient.get().uri(c.url() + "/wp-json/wc/v3/products/" + producto.path("id").asText() + "/variations/" + id.asText())
                    .headers(h -> h.setBasicAuth(c.key(), c.secret())).retrieve().body(JsonNode.class);
            if (v == null) continue;
            String talle = "", color = "";
            Map<String, String> atributos = new LinkedHashMap<>();
            List<String> valoresNombre = new ArrayList<>();
            for (JsonNode atributo : v.path("attributes")) {
                String valor = atributo.path("option").asText("").trim();
                if (valor.isBlank()) continue;
                String idAtributo = idAtributo(atributo);
                atributos.put(idAtributo, valor);
                valoresNombre.add(valor);
                if ("SIZE".equals(idAtributo)) talle = valor;
                if ("COLOR".equals(idAtributo)) color = valor;
            }
            resultado.add(new VarianteCanalImportada(v.path("id").asText(), v.path("sku").asText(),
                    String.join(" / ", valoresNombre), talle, color,
                    v.path("stock_quantity").asInt(0), decimal(v.path("regular_price").asText("0")), null, null, null, atributos, null, false));
        }
        return resultado;
    }

    static String idAtributo(JsonNode atributo) {
        String nombre = atributo.path("name").asText("").trim();
        String slug = atributo.path("slug").asText("").trim();
        String base = slug.isBlank() ? nombre : slug.replaceFirst("(?i)^pa_", "");
        String normalizado = Normalizer.normalize(base, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^A-Za-z0-9]+", "_")
                .replaceAll("^_+|_+$", "")
                .toUpperCase(Locale.ROOT);
        if (normalizado.equals("TALLE") || normalizado.equals("TAMANO")
                || normalizado.equals("SIZE")) return "SIZE";
        if (normalizado.equals("COLOR") || normalizado.equals("COLOUR")) return "COLOR";
        return normalizado.isBlank() ? "ATRIBUTO" : normalizado;
    }

    private BigDecimal decimal(String value) { try { return new BigDecimal(value); } catch (Exception e) { return BigDecimal.ZERO; } }
}

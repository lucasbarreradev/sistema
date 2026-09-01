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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.text.Normalizer;
import java.util.Locale;

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
        return obtenerProductos(false, () -> false);
    }

    @Override
    public List<ProductoCanalImportado> obtenerProductos(
            boolean incluirInactivas, BooleanSupplier cancelacionSolicitada) {
        if (!configurado()) throw new IllegalStateException("Tiendanube no está configurada");
        TiendanubeCredencialesService.Credenciales c = credenciales.obtener();
        Map<String, String> nombresCategorias = obtenerCategorias(c, cancelacionSolicitada);
        List<ProductoCanalImportado> productos = new ArrayList<>();
        for (int pagina = 1; ; pagina++) {
            if (cancelacionSolicitada.getAsBoolean()) break;
            JsonNode body = restClient.get()
                    .uri("https://api.tiendanube.com/v1/" + c.storeId() + "/products?per_page=200&page=" + pagina)
                    .headers(h -> { h.setBearerAuth(c.token()); h.set("User-Agent", c.userAgent()); })
                    .retrieve().body(JsonNode.class);
            if (body == null || !body.isArray() || body.isEmpty()) break;
            for (JsonNode p : body) {
                if (cancelacionSolicitada.getAsBoolean()) return productos;
                productos.add(mapear(p, nombresCategorias));
            }
            if (body.size() < 200) break;
        }
        return productos;
    }

    private ProductoCanalImportado mapear(JsonNode p, Map<String, String> nombresCategorias) {
        JsonNode variante = p.path("variants").isArray() && !p.path("variants").isEmpty() ? p.path("variants").get(0) : p;
        String nombre = localizado(p.path("name"));
        String imagen = p.path("images").isArray() && !p.path("images").isEmpty()
                ? p.path("images").get(0).path("src").asText(null) : null;
        Map<String, Object> datos = new LinkedHashMap<>();
        List<Map<String, String>> categorias = mapearCategoriasProducto(
                p.path("categories"), nombresCategorias);
        if (!categorias.isEmpty()) datos.put("categorias", categorias);
        String marca = p.path("brand").asText("").trim();
        if (!marca.isBlank()) datos.put("marca", marca);
        if (p.has("published")) datos.put("estado", p.path("published").asBoolean() ? "active" : "inactive");
        return new ProductoCanalImportado(p.path("id").asText(), variante.path("sku").asText(), nombre,
                variante.path("stock").asInt(0), decimal(variante.path("price").asText("0")), imagen, null, datos,
                mapearVariantes(p, nombre));
    }

    private Map<String, String> obtenerCategorias(TiendanubeCredencialesService.Credenciales c,
                                                   BooleanSupplier cancelacionSolicitada) {
        Map<String, String> resultado = new LinkedHashMap<>();
        try {
            for (int pagina = 1; ; pagina++) {
                if (cancelacionSolicitada.getAsBoolean()) break;
                JsonNode body = restClient.get()
                        .uri("https://api.tiendanube.com/v1/" + c.storeId()
                                + "/categories?per_page=200&page=" + pagina)
                        .headers(h -> { h.setBearerAuth(c.token()); h.set("User-Agent", c.userAgent()); })
                        .retrieve().body(JsonNode.class);
                if (body == null || !body.isArray() || body.isEmpty()) break;
                for (JsonNode categoria : body) indexarCategoria(categoria, resultado);
                if (body.size() < 200) break;
            }
        } catch (RuntimeException ignored) {
            // Las categorías incluidas en cada producto siguen permitiendo armar el filtro.
        }
        return resultado;
    }

    private void indexarCategoria(JsonNode categoria, Map<String, String> resultado) {
        String id = categoria.path("id").asText("").trim();
        if (!id.isBlank()) resultado.put(id, localizado(categoria.path("name")));
        if (categoria.path("subcategories").isArray()) {
            for (JsonNode subcategoria : categoria.path("subcategories")) {
                indexarCategoria(subcategoria, resultado);
            }
        }
    }

    private List<Map<String, String>> mapearCategoriasProducto(
            JsonNode lista, Map<String, String> nombresCategorias) {
        List<Map<String, String>> resultado = new ArrayList<>();
        Set<String> agregadas = new LinkedHashSet<>();
        if (!lista.isArray()) return resultado;
        for (JsonNode categoria : lista) {
            String id = categoria.isObject()
                    ? categoria.path("id").asText("").trim() : categoria.asText("").trim();
            if (id.isBlank() || !agregadas.add(id)) continue;
            String nombre = categoria.isObject() ? localizado(categoria.path("name")) : "";
            if (nombre.isBlank() || "Producto sin nombre".equals(nombre)) {
                nombre = nombresCategorias.getOrDefault(id, id);
            }
            resultado.add(Map.of("id", id, "nombre", nombre));
        }
        return resultado;
    }

    List<VarianteCanalImportada> mapearVariantes(JsonNode producto) {
        return mapearVariantes(producto, localizado(producto.path("name")));
    }

    private List<VarianteCanalImportada> mapearVariantes(JsonNode producto, String nombreProducto) {
        List<VarianteCanalImportada> resultado = new ArrayList<>();
        if (!producto.path("variants").isArray() || producto.path("variants").isEmpty()) return resultado;
        List<String> nombresAtributos = new ArrayList<>();
        if (producto.path("attributes").isArray()) {
            for (JsonNode atributo : producto.path("attributes")) {
                nombresAtributos.add(localizado(atributo));
            }
        }
        for (JsonNode v : producto.path("variants")) {
            List<String> valores = new ArrayList<>();
            if (v.path("values").isArray()) {
                for (JsonNode valor : v.path("values")) valores.add(localizado(valor));
            }
            Map<String, String> atributos = new LinkedHashMap<>();
            String talle = "";
            String color = "";
            for (int i = 0; i < valores.size(); i++) {
                String valor = valores.get(i);
                String nombreAtributo = i < nombresAtributos.size() ? nombresAtributos.get(i) : "";
                String idMercadoLibre = idAtributoMercadoLibre(nombreAtributo);
                if ("SIZE".equals(idMercadoLibre)) talle = valor;
                if ("COLOR".equals(idMercadoLibre)) color = valor;
                if (idMercadoLibre != null && !valor.isBlank()) atributos.put(idMercadoLibre, valor);
            }
            String nombre = valores.stream().filter(valor -> !valor.isBlank())
                    .collect(java.util.stream.Collectors.joining(" / "));
            if (nombre.isBlank()) {
                nombre = nombreProducto == null || nombreProducto.isBlank()
                        || "Producto sin nombre".equals(nombreProducto)
                        ? "Presentación única"
                        : nombreProducto;
            }
            resultado.add(new VarianteCanalImportada(v.path("id").asText(), v.path("sku").asText(),
                    nombre, talle, color,
                    v.path("stock").asInt(0), decimal(v.path("price").asText("0")), v.path("barcode").asText(null), null,
                    v.path("barcode").asText(null), atributos, null, false));
        }
        return resultado;
    }

    private String idAtributoMercadoLibre(String nombre) {
        String normalizado = Normalizer.normalize(nombre == null ? "" : nombre, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .trim().toLowerCase(Locale.ROOT);
        if (Set.of("color", "cor").contains(normalizado)) return "COLOR";
        if (Set.of("talle", "talla", "tamano", "size").contains(normalizado)) return "SIZE";
        return null;
    }

    private String localizado(JsonNode nodo) {
        if (nodo.isTextual()) return nodo.asText();
        if (nodo.hasNonNull("es")) return nodo.path("es").asText();
        if (nodo.fields().hasNext()) return nodo.fields().next().getValue().asText();
        return "Producto sin nombre";
    }
    private BigDecimal decimal(String value) { try { return new BigDecimal(value); } catch (Exception e) { return BigDecimal.ZERO; } }
}

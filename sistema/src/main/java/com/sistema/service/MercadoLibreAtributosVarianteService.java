package com.sistema.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.sistema.dto.AtributoVarianteMl;
import com.sistema.model.Producto;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.net.http.HttpClient;
import java.time.Duration;

@Service
public class MercadoLibreAtributosVarianteService {
    private static final Set<String> CAMPOS_GENERALES = Set.of(
            "SELLER_SKU", "ITEM_CONDITION", "GENDER",
            "SIZE_GRID_ID", "SIZE_GRID_ROW_ID", "GTIN");
    private final RestClient restClient;
    private final MercadoLibreTokenService tokenService;
    private final Map<String, List<AtributoVarianteMl>> atributosPorCategoria =
            new java.util.concurrent.ConcurrentHashMap<>();

    public MercadoLibreAtributosVarianteService(MercadoLibreTokenService tokenService) {
        this.tokenService = tokenService;
        HttpClient cliente = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(cliente);
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    public Resultado obtener(Producto producto) {
        if (!tokenService.configurado()) return new Resultado(producto.getMercadoLibreCategoriaId(), List.of());
        String categoria = producto.getMercadoLibreCategoriaId();
        if (categoria == null || categoria.isBlank()) categoria = predecirCategoria(producto.getDescripcion());
        return obtenerPorCategoria(categoria);
    }

    public Resultado obtenerPorCategoria(String categoria) {
        if (!tokenService.configurado()) return new Resultado(categoria, List.of());
        if (categoria == null || categoria.isBlank()) return new Resultado(null, List.of());
        List<AtributoVarianteMl> guardados = atributosPorCategoria.get(categoria);
        if (guardados != null) return new Resultado(categoria, guardados);
        JsonNode respuesta = get("/categories/" + categoria + "/attributes");
        List<AtributoVarianteMl> atributos = new ArrayList<>();
        if (respuesta != null && respuesta.isArray()) {
            for (JsonNode atributo : respuesta) {
                JsonNode tags = atributo.path("tags");
                boolean permiteVariar = atributo.path("tags").path("allow_variations").asBoolean(false)
                        || atributo.path("tags").path("variation_attribute").asBoolean(false)
                        || "CHILD_PK".equals(atributo.path("hierarchy").asText());
                String id = atributo.path("id").asText();
                boolean obligatorio = tags.path("required").asBoolean(false)
                        || tags.path("new_required").asBoolean(false)
                        || tags.path("catalog_required").asBoolean(false)
                        || tags.path("catalog_listing_required").asBoolean(false);
                boolean condicional = tags.path("conditional_required").asBoolean(false);
                boolean marcaOModelo = "BRAND".equals(id) || "MODEL".equals(id);
                if ((!permiteVariar && !obligatorio && !condicional && !marcaOModelo)
                        || tags.path("read_only").asBoolean(false)
                        || CAMPOS_GENERALES.contains(id)) continue;
                List<String> valores = new ArrayList<>();
                for (JsonNode valor : atributo.path("values")) {
                    String nombre = valor.path("name").asText("");
                    if (!nombre.isBlank() && !valores.contains(nombre)) valores.add(nombre);
                }
                List<String> unidades = new ArrayList<>();
                for (JsonNode unidad : atributo.path("allowed_units")) {
                    String nombre = unidad.path("name").asText(unidad.path("id").asText(""));
                    if (!nombre.isBlank() && !unidades.contains(nombre)) unidades.add(nombre);
                }
                atributos.add(new AtributoVarianteMl(
                        id,
                        atributo.path("name").asText(atributo.path("id").asText()),
                        atributo.path("value_type").asText("string"),
                        valores,
                        unidades,
                        atributo.path("default_unit").asText(""),
                        obligatorio,
                        permiteVariar));
            }
        }
        List<AtributoVarianteMl> inmutables = List.copyOf(atributos);
        atributosPorCategoria.put(categoria, inmutables);
        return new Resultado(categoria, inmutables);
    }

    public String predecirCategoria(String descripcion) {
        if (!tokenService.configurado()) return "";
        JsonNode respuesta = get("/sites/MLA/domain_discovery/search?limit=3&q="
                + java.net.URLEncoder.encode(descripcion == null ? "" : descripcion,
                java.nio.charset.StandardCharsets.UTF_8));
        return respuesta != null && respuesta.isArray() && !respuesta.isEmpty()
                ? respuesta.get(0).path("category_id").asText("") : "";
    }

    private JsonNode get(String endpoint) {
        try {
            return get(endpoint, tokenService.obtenerAccessToken());
        } catch (RestClientResponseException e) {
            if (e.getStatusCode() != HttpStatus.UNAUTHORIZED) throw e;
            tokenService.invalidarAccessToken();
            return get(endpoint, tokenService.obtenerAccessToken());
        }
    }

    private JsonNode get(String endpoint, String token) {
        return restClient.get().uri("https://api.mercadolibre.com" + endpoint)
                .headers(h -> h.setBearerAuth(token)).retrieve().body(JsonNode.class);
    }

    public record Resultado(String categoriaId, List<AtributoVarianteMl> atributos) {}
}

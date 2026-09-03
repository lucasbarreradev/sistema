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
    private final Map<String, String> nombresPorCategoria =
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
        if (guardados != null) return new Resultado(
                categoria, obtenerNombreCategoria(categoria), guardados);
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
        return new Resultado(
                categoria, obtenerNombreCategoria(categoria), inmutables);
    }

    public String obtenerNombreCategoria(String categoriaId) {
        if (categoriaId == null || categoriaId.isBlank()) return "";
        if (!tokenService.configurado()) return categoriaId;
        return nombresPorCategoria.computeIfAbsent(categoriaId, id -> {
            JsonNode categoria = get("/categories/" + id);
            String nombre = categoria == null
                    ? "" : categoria.path("name").asText("").trim();
            return nombre.isBlank() ? id : nombre;
        });
    }

    public String predecirCategoria(String descripcion) {
        if (!tokenService.configurado()) return "";
        String consulta = prepararConsultaCategoria(descripcion);
        JsonNode respuesta = get("/sites/MLA/domain_discovery/search?limit=3&q="
                + java.net.URLEncoder.encode(consulta,
                java.nio.charset.StandardCharsets.UTF_8));
        return seleccionarCategoria(descripcion, respuesta);
    }

    String prepararConsultaCategoria(String descripcion) {
        String original = descripcion == null ? "" : descripcion.trim();
        String normalizada = normalizar(original);
        String consultaOriginal = original
                .replace("Ã±", "n").replace("Ã‘", "N")
                .replace('ñ', 'n').replace('Ñ', 'N');
        if (esVasoParaBeber(normalizada)) {
            return "vasos para beber vajilla " + consultaOriginal;
        }
        if (normalizada.contains("set") && normalizada.contains("bano")) {
            return "set de accesorios para bano " + consultaOriginal;
        }
        if (normalizada.contains("rinonera")) {
            return "rinonera bolso de cintura accesorio de moda " + consultaOriginal;
        }
        if (normalizada.contains("manopla")
                && (normalizada.contains("kitchen")
                        || normalizada.contains("cocina")
                        || normalizada.contains("horno"))) {
            return "agarradera manopla de cocina para horno " + consultaOriginal;
        }
        if (normalizada.contains("vela") && !normalizada.contains("velador")) {
            return "vela decorativa para el hogar " + consultaOriginal;
        }
        if (normalizada.contains("bolsito")) {
            return "cartera bolso pequeño " + consultaOriginal;
        }
        if (normalizada.contains("viaje")
                && (normalizada.contains("envase")
                        || normalizada.contains("frasco")
                        || normalizada.contains("porta liquido"))) {
            return "neceser organizador de viaje con envases rellenables "
                    + consultaOriginal;
        }
        if ((normalizada.contains("toallita") || normalizada.contains("toalitta"))
                && normalizada.contains("comprim")) {
            return "toallitas comprimidas de higiene personal " + consultaOriginal;
        }
        return consultaOriginal;
    }

    String seleccionarCategoria(String descripcion, JsonNode respuesta) {
        if (respuesta == null || !respuesta.isArray() || respuesta.isEmpty()) return "";
        String titulo = normalizar(descripcion);
        if (esVasoParaBeber(titulo)) {
            for (JsonNode opcion : respuesta) {
                if ("MLA457489".equals(opcion.path("category_id").asText(""))) {
                    return "MLA457489";
                }
            }
            for (JsonNode opcion : respuesta) {
                String nombre = normalizar(opcion.path("category_name").asText(""));
                String dominio = normalizar(opcion.path("domain_name").asText(""));
                if (nombre.equals("vasos") && dominio.equals("vasos y copas")) {
                    return opcion.path("category_id").asText("");
                }
            }
        }
        if (titulo.contains("set") && titulo.contains("bano")) {
            for (JsonNode opcion : respuesta) {
                if ("MLA31032".equals(opcion.path("category_id").asText(""))) {
                    return "MLA31032";
                }
            }
            for (JsonNode opcion : respuesta) {
                String dominio = normalizar(opcion.path("domain_name").asText(""));
                if (dominio.contains("accesorios") && dominio.contains("bano")) {
                    return opcion.path("category_id").asText("");
                }
            }
        }
        if (titulo.contains("rinonera")) {
            for (JsonNode opcion : respuesta) {
                if ("MLA417710".equals(opcion.path("category_id").asText(""))) {
                    return "MLA417710";
                }
            }
            for (JsonNode opcion : respuesta) {
                String nombre = normalizar(opcion.path("category_name").asText(""));
                String dominio = normalizar(opcion.path("domain_name").asText(""));
                if (nombre.equals("rinoneras") || dominio.equals("rinoneras")) {
                    return opcion.path("category_id").asText("");
                }
            }
        }
        if ((titulo.contains("incienso") || titulo.contains("sahumer"))
                && !titulo.contains("porta")) {
            for (JsonNode opcion : respuesta) {
                String nombre = normalizar(opcion.path("category_name").asText(""));
                String dominio = normalizar(opcion.path("domain_name").asText(""));
                if ((nombre.equals("sahumerios") || dominio.equals("inciensos"))
                        && !nombre.contains("porta")) {
                    return opcion.path("category_id").asText("");
                }
            }
        }
        if (titulo.contains("piloto")
                && (titulo.contains("infantil") || titulo.contains("nena")
                        || titulo.contains("nino") || titulo.contains("disney")
                        || titulo.contains("kuromi") || titulo.contains("peppa")
                        || titulo.contains("paw patrol"))) {
            for (JsonNode opcion : respuesta) {
                String dominio = normalizar(opcion.path("domain_name").asText(""));
                if ((dominio.contains("campera") || dominio.contains("abrigo"))
                        && !dominio.contains("moto")) {
                    return opcion.path("category_id").asText("");
                }
            }
        }
        return respuesta.get(0).path("category_id").asText("");
    }

    private boolean esVasoParaBeber(String textoNormalizado) {
        return textoNormalizado.matches(".*\\bvasos\\b.*")
                && !textoNormalizado.contains("descartable")
                && !textoNormalizado.contains("medidor")
                && !textoNormalizado.contains("termico")
                && !textoNormalizado.matches(".*\\bbebe(s)?\\b.*");
    }

    private String normalizar(String valor) {
        if (valor == null) return "";
        return java.text.Normalizer.normalize(valor, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^A-Za-z0-9]+", " ")
                .trim().toLowerCase(java.util.Locale.ROOT);
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

    public record Resultado(
            String categoriaId,
            String categoriaNombre,
            List<AtributoVarianteMl> atributos) {
        public Resultado(String categoriaId, List<AtributoVarianteMl> atributos) {
            this(categoriaId, categoriaId, atributos);
        }
    }
}

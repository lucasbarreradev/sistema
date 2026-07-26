package com.sistema.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.sistema.dto.AtributoProductoMl;
import com.sistema.model.Producto;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class MercadoLibreAtributosProductoService {
    private static final Set<String> CAMPOS_PROPIOS = Set.of("SELLER_SKU", "ITEM_CONDITION", "BRAND", "MODEL",
            "GTIN", "EMPTY_GTIN_REASON", "GENDER", "GARMENT_TYPE", "SIZE_GRID_ID", "SIZE_GRID_ROW_ID");
    private final RestClient restClient = RestClient.create();
    private final MercadoLibreTokenService tokenService;

    public MercadoLibreAtributosProductoService(MercadoLibreTokenService tokenService) {
        this.tokenService = tokenService;
    }

    public List<AtributoProductoMl> obtenerObligatorios(Producto producto) {
        String categoria = producto.getMercadoLibreCategoriaId();
        if (!tokenService.configurado() || categoria == null || categoria.isBlank()) return List.of();
        JsonNode respuesta = get("/categories/" + categoria + "/attributes");
        List<AtributoProductoMl> resultado = new ArrayList<>();
        if (!respuesta.isArray()) return resultado;
        for (JsonNode atributo : respuesta) {
            String id = atributo.path("id").asText("");
            JsonNode tags = atributo.path("tags");
            boolean motivoGtin = "EMPTY_GTIN_REASON".equals(id) && tags.path("conditional_required").asBoolean(false);
            boolean atributoDeVariante = tags.path("allow_variations").asBoolean(false)
                    || tags.path("variation_attribute").asBoolean(false)
                    || "CHILD_PK".equals(atributo.path("hierarchy").asText());
            boolean productoConVariantes = Boolean.TRUE.equals(producto.getUsaVariantes());
            boolean obligatorio = tags.path("required").asBoolean(false)
                    || tags.path("catalog_required").asBoolean(false)
                    || ("LOAD_INDEX".equals(id) && !productoConVariantes);
            if ((!obligatorio && !motivoGtin)
                    || tags.path("read_only").asBoolean(false)
                    || (atributoDeVariante && productoConVariantes)
                    || CAMPOS_PROPIOS.contains(id)) continue;
            List<AtributoProductoMl.Valor> valores = new ArrayList<>();
            for (JsonNode valor : atributo.path("values")) {
                String nombre = valor.path("name").asText("");
                if (!nombre.isBlank()) valores.add(new AtributoProductoMl.Valor(valor.path("id").asText(""), nombre));
            }
            List<String> unidades = new ArrayList<>();
            for (JsonNode unidad : atributo.path("allowed_units")) {
                String nombre = unidad.path("name").asText(unidad.path("id").asText(""));
                if (!nombre.isBlank()) unidades.add(nombre);
            }
            resultado.add(new AtributoProductoMl(id, atributo.path("name").asText(id),
                    atributo.path("value_type").asText("string"), valores, unidades,
                    atributo.path("default_unit").asText(""), obligatorio && !motivoGtin));
        }
        return resultado;
    }

    private JsonNode get(String endpoint) {
        try { return get(endpoint, tokenService.obtenerAccessToken()); }
        catch (RestClientResponseException e) {
            if (e.getStatusCode() != HttpStatus.UNAUTHORIZED) throw e;
            tokenService.invalidarAccessToken();
            return get(endpoint, tokenService.obtenerAccessToken());
        }
    }

    private JsonNode get(String endpoint, String token) {
        JsonNode respuesta = restClient.get().uri("https://api.mercadolibre.com" + endpoint)
                .headers(h -> h.setBearerAuth(token)).retrieve().body(JsonNode.class);
        return respuesta == null ? com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode() : respuesta;
    }
}

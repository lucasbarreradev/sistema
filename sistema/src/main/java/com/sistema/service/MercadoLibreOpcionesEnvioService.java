package com.sistema.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sistema.model.Producto;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MercadoLibreOpcionesEnvioService {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final MercadoLibreTokenService tokenService;
    private final RestClient restClient;
    private final Map<String, String> dominiosPorCategoria =
            new java.util.concurrent.ConcurrentHashMap<>();

    @Autowired
    public MercadoLibreOpcionesEnvioService(
            MercadoLibreTokenService tokenService) {
        this(tokenService, crearRestClient());
    }

    MercadoLibreOpcionesEnvioService(
            MercadoLibreTokenService tokenService, RestClient restClient) {
        this.tokenService = tokenService;
        this.restClient = restClient;
    }

    public OpcionesEnvio obtener(Producto producto) {
        if (!tokenService.configurado() || producto == null
                || sinTexto(producto.getMercadoLibreCategoriaId())) return null;
        try {
            return consultar(producto, tokenService.obtenerAccessToken());
        } catch (RestClientResponseException e) {
            if (e.getStatusCode() != HttpStatus.UNAUTHORIZED) {
                return obtenerRespaldo(producto);
            }
            tokenService.invalidarAccessToken();
            try {
                return consultar(producto, tokenService.obtenerAccessToken());
            } catch (RuntimeException ignorado) {
                return obtenerRespaldo(producto);
            }
        } catch (RuntimeException e) {
            return obtenerRespaldo(producto);
        }
    }

    private OpcionesEnvio consultar(Producto producto, String token) {
        Long usuarioId = tokenService.obtenerUsuarioExternoId();
        if (usuarioId == null) return obtenerRespaldo(producto);
        JsonNode respuesta = restClient.post()
                .uri("https://api.mercadolibre.com/users/" + usuarioId
                        + "/shipping_modes")
                .headers(h -> {
                    h.setBearerAuth(token);
                    h.set("x-multichannel", "true");
                    h.set("X-Format-New", "true");
                })
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload(producto, usuarioId, token))
                .retrieve().body(JsonNode.class);
        OpcionesEnvio opciones = analizarRespuesta(respuesta);
        return opciones == null ? obtenerRespaldo(producto) : opciones;
    }

    private Map<String, Object> payload(
            Producto producto, Long usuarioId, String token) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("site_id", "MLA");
        body.put("seller_id", usuarioId);
        body.put("title", titulo(producto));
        BigDecimal precio = producto.getPrecioContado();
        body.put("item_price", precio == null || precio.signum() <= 0
                ? BigDecimal.ONE : precio);
        body.put("item_currency", "ARS");
        body.put("category_id", producto.getMercadoLibreCategoriaId());
        body.put("listing_type_id", textoO(
                producto.getMercadoLibreListingTypeId(), "gold_special"));
        body.put("buying_mode", "buy_it_now");
        body.put("condition", textoO(producto.getMercadoLibreCondicion(), "new"));
        body.put("channels", List.of(Map.of("id", "marketplace")));
        body.put("new_format", true);
        body.put("verbose", false);
        if (!sinTexto(producto.getMercadoLibreId())) {
            body.put("item_id", producto.getMercadoLibreId().trim());
        }
        List<Map<String, Object>> atributos = atributos(producto);
        if (!atributos.isEmpty()) body.put("attributes", atributos);
        String dominio = dominioCategoria(
                producto.getMercadoLibreCategoriaId(), token);
        Map<String, Object> catalogo = new LinkedHashMap<>();
        if (!dominio.isBlank()) catalogo.put("domain_id", dominio);
        catalogo.put("attributes", atributos);
        body.put("catalog", catalogo);
        return body;
    }

    private String dominioCategoria(String categoriaId, String token) {
        String guardado = dominiosPorCategoria.get(categoriaId);
        if (guardado != null) return guardado;
        JsonNode categoria = get("/categories/" + categoriaId, token);
        String dominio = categoria.path("settings").path("catalog_domain")
                .asText(categoria.path("catalog_domain").asText(""))
                .trim();
        dominiosPorCategoria.put(categoriaId, dominio);
        return dominio;
    }

    private List<Map<String, Object>> atributos(Producto producto) {
        List<Map<String, Object>> atributos = new ArrayList<>();
        agregarAtributo(atributos, "BRAND", "Marca", producto.getMercadoLibreMarca());
        agregarAtributo(atributos, "MODEL", "Modelo", producto.getMercadoLibreModelo());
        agregarAtributo(atributos, "GTIN", "GTIN", producto.getMercadoLibreGtin());
        String json = producto.getMercadoLibreAtributosJson();
        if (sinTexto(json)) return atributos;
        try {
            JsonNode lista = JSON.readTree(json);
            if (!lista.isArray()) return atributos;
            for (JsonNode atributo : lista) {
                String id = atributo.path("id").asText("").trim();
                String valor = atributo.path("value_name").asText("").trim();
                if (id.isBlank() || valor.isBlank()) continue;
                agregarAtributo(atributos, id,
                        atributo.path("name").asText(id), valor);
            }
        } catch (Exception ignorado) {
            // La validación de atributos mostrará por separado un JSON inválido.
        }
        return atributos;
    }

    private void agregarAtributo(List<Map<String, Object>> atributos,
                                 String id, String nombre, String valor) {
        if (sinTexto(valor)) return;
        atributos.removeIf(a -> id.equals(a.get("id")));
        atributos.add(Map.of("id", id, "name", nombre, "value_name", valor.trim()));
    }

    private OpcionesEnvio obtenerRespaldo(Producto producto) {
        try {
            String token = tokenService.obtenerAccessToken();
            Long usuarioId = tokenService.obtenerUsuarioExternoId();
            if (usuarioId == null) return null;
            JsonNode usuario = get("/users/" + usuarioId + "/shipping_preferences", token);
            JsonNode categoria = get("/categories/"
                    + producto.getMercadoLibreCategoriaId() + "/shipping_preferences", token);
            boolean me2Usuario = contieneTexto(usuario.path("modes"), "me2");
            boolean me2Categoria = false;
            boolean noEspecificado = false;
            for (JsonNode logistica : categoria.path("logistics")) {
                String modo = logistica.path("mode").asText("");
                if ("me2".equals(modo)) me2Categoria = true;
                if ("not_specified".equals(modo)) noEspecificado = true;
            }
            boolean me2 = me2Usuario && me2Categoria;
            boolean retiro = usuario.path("local_pick_up").asBoolean(false);
            return new OpcionesEnvio(me2 ? "me2" : noEspecificado
                    ? "not_specified" : null, false, retiro, false);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private JsonNode get(String endpoint, String token) {
        return restClient.get().uri("https://api.mercadolibre.com" + endpoint)
                .headers(h -> h.setBearerAuth(token)).retrieve().body(JsonNode.class);
    }

    static OpcionesEnvio analizarRespuesta(JsonNode respuesta) {
        if (respuesta == null || respuesta.isNull()) return null;
        List<JsonNode> modos = new ArrayList<>();
        buscarModos(respuesta, modos);
        List<JsonNode> elegidos = modos.stream()
                .filter(n -> "me2".equals(n.path("mode").asText("")))
                .toList();
        String modo = "me2";
        if (elegidos.isEmpty()) {
            modo = "not_specified";
            elegidos = modos.stream()
                        .filter(n -> "not_specified".equals(
                                n.path("mode").asText("")))
                        .toList();
        }
        if (elegidos.isEmpty()) return null;
        return new OpcionesEnvio(modo,
                elegidos.stream().anyMatch(n -> permitido(
                        n, "free_shipping", false)),
                elegidos.stream().anyMatch(n -> permitido(
                        n, "local_pick_up", false)), true);
    }

    private static void buscarModos(JsonNode nodo, List<JsonNode> resultado) {
        if (nodo == null) return;
        if (nodo.isObject() && nodo.has("mode") && nodo.path("mode").isTextual()) {
            resultado.add(nodo);
        }
        nodo.elements().forEachRemaining(hijo -> buscarModos(hijo, resultado));
    }

    private static boolean permitido(JsonNode nodo, String atributo,
                                      boolean valorSiNoInforma) {
        List<String> estados = new ArrayList<>();
        buscarEstado(nodo, atributo, estados);
        if (estados.isEmpty()) return valorSiNoInforma;
        return estados.stream().anyMatch(estado -> !"not_allowed".equals(estado));
    }

    private static void buscarEstado(JsonNode nodo, String atributo,
                                     List<String> estados) {
        if (nodo == null) return;
        if (nodo.isObject()) {
            for (String contenedor : List.of("shipping_attributes", "attributes")) {
                JsonNode valor = nodo.path(contenedor).path(atributo);
                if (valor.isTextual()) estados.add(valor.asText(""));
            }
        }
        nodo.elements().forEachRemaining(hijo -> buscarEstado(hijo, atributo, estados));
    }

    private boolean contieneTexto(JsonNode lista, String buscado) {
        if (!lista.isArray()) return false;
        for (JsonNode valor : lista) {
            if (buscado.equals(valor.asText(""))) return true;
        }
        return false;
    }

    private String titulo(Producto producto) {
        return textoO(producto.getMercadoLibreTitulo(), producto.getDescripcion());
    }

    private String textoO(String valor, String respaldo) {
        return sinTexto(valor) ? respaldo : valor.trim();
    }

    private static boolean sinTexto(String valor) {
        return valor == null || valor.isBlank();
    }

    private static RestClient crearRestClient() {
        HttpClient cliente = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(cliente);
        factory.setReadTimeout(Duration.ofSeconds(6));
        return RestClient.builder().requestFactory(factory).build();
    }

    public record OpcionesEnvio(
            String modo, boolean envioGratis, boolean retiroPersonal,
            boolean verificada) {}
}

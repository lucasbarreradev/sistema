package com.sistema.service.canal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sistema.dto.ProductoCanalImportado;
import com.sistema.dto.VarianteCanalImportada;
import com.sistema.model.CanalVenta;
import com.sistema.service.MercadoLibreTokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.*;

@Component
public class MercadoLibreImportador implements ImportadorCanal {
    private static final Set<String> ATRIBUTOS_CON_CAMPO = Set.of(
            "SELLER_SKU", "ITEM_CONDITION", "BRAND", "MODEL", "GTIN");
    private static final long INTERVALO_STOCK_MS = 650L;

    private final RestClient restClient;
    private final MercadoLibreTokenService tokenService;
    private final ObjectMapper objectMapper;
    private final Object bloqueoConsultasStock = new Object();
    private long proximaConsultaStockNanos;

    @Autowired
    public MercadoLibreImportador(MercadoLibreTokenService tokenService, ObjectMapper objectMapper) {
        this(tokenService, objectMapper, RestClient.create());
    }

    MercadoLibreImportador(MercadoLibreTokenService tokenService, ObjectMapper objectMapper,
                            RestClient restClient) {
        this.tokenService = tokenService;
        this.objectMapper = objectMapper;
        this.restClient = restClient;
    }

    public CanalVenta canal() { return CanalVenta.MERCADO_LIBRE; }
    public boolean configurado() { return tokenService.configurado(); }

    public List<ProductoCanalImportado> obtenerProductos() {
        JsonNode usuario = get("/users/me");
        String userId = usuario.path("id").asText();
        if (userId.isBlank()) throw new IllegalStateException("No se pudo identificar la cuenta de Mercado Libre");
        List<ProductoCanalImportado> productos = new ArrayList<>();
        Map<String, String> familiasPorUserProduct = new HashMap<>();
        for (int offset = 0; ; offset += 50) {
            JsonNode pagina = get("/users/" + userId + "/items/search?status=active&limit=50&offset=" + offset);
            JsonNode ids = pagina.path("results");
            if (!ids.isArray() || ids.isEmpty()) break;
            for (JsonNode id : ids) {
                JsonNode item = get("/items/" + id.asText() + "?include_attributes=all");
                ProductoCanalImportado importado = mapear(item);
                String familyName = item.path("family_name").asText("");
                String userProductId = item.path("user_product_id").asText("");
                if (!userProductId.isBlank()) {
                    String familyId = familiasPorUserProduct.computeIfAbsent(userProductId,
                            clave -> resolverFamilyId(clave));
                    if (!familyId.isBlank()) {
                        importado.datosCanal().put("familyId", familyId);
                        if (!familyName.isBlank()) importado.datosCanal().put("familyName", familyName);
                    }
                }
                productos.add(importado);
            }
            if (offset + ids.size() >= pagina.path("paging").path("total").asInt()) break;
        }
        return agruparUserProducts(productos);
    }

    public ProductoCanalImportado obtenerProducto(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            throw new IllegalArgumentException("Falta el identificador de la publicación de Mercado Libre");
        }
        return mapear(get("/items/" + itemId.trim() + "?include_attributes=all"));
    }

    private JsonNode get(String endpoint) {
        try { return getConToken(endpoint, tokenService.obtenerAccessToken()); }
        catch (RestClientResponseException e) {
            if (e.getStatusCode() != HttpStatus.UNAUTHORIZED) throw e;
            tokenService.invalidarAccessToken();
            return getConToken(endpoint, tokenService.obtenerAccessToken());
        }
    }

    private JsonNode getOpcional(String endpoint) {
        try { return get(endpoint); }
        catch (RestClientResponseException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) return null;
            throw e;
        }
    }

    private JsonNode getConToken(String endpoint, String token) {
        return restClient.get().uri("https://api.mercadolibre.com" + endpoint)
                .headers(h -> h.setBearerAuth(token)).retrieve().body(JsonNode.class);
    }

    private ProductoCanalImportado mapear(JsonNode producto) {
        Map<String, JsonNode> atributos = indexarAtributos(producto.path("attributes"));
        String sku = valorAtributo(atributos, "SELLER_SKU");
        if (sku.isBlank()) sku = producto.path("seller_sku").asText();

        List<String> fotos = new ArrayList<>();
        if (producto.path("pictures").isArray()) {
            for (JsonNode foto : producto.path("pictures")) {
                String url = foto.path("secure_url").asText(foto.path("url").asText());
                if (!url.isBlank()) fotos.add(url);
            }
        }

        Map<String, Object> datos = new LinkedHashMap<>();
        long tiendaOficial = producto.path("official_store_id").asLong(0);
        if (tiendaOficial > 0) datos.put("officialStoreId", tiendaOficial);
        ponerSiTieneTexto(datos, "marca", valorAtributo(atributos, "BRAND"));
        ponerSiTieneTexto(datos, "modelo", valorAtributo(atributos, "MODEL"));
        ponerSiTieneTexto(datos, "gtin", valorAtributo(atributos, "GTIN"));
        ponerSiTieneTexto(datos, "guiaTallesId", valorAtributo(atributos, "SIZE_GRID_ID"));
        ponerSiTieneTexto(datos, "guiaTallesFilaId", valorAtributo(atributos, "SIZE_GRID_ROW_ID"));
        ponerSiTieneTexto(datos, "genero", valorAtributo(atributos, "GENDER"));
        ponerSiTieneTexto(datos, "videoId", producto.path("video_id").asText());
        ponerSiTieneTexto(datos, "condicion", producto.path("condition").asText());
        ponerSiTieneTexto(datos, "estado", producto.path("status").asText());
        ponerSiTieneTexto(datos, "listingTypeId", producto.path("listing_type_id").asText());
        ponerSiTieneTexto(datos, "modoEnvio", producto.path("shipping").path("mode").asText());
        if (producto.path("shipping").has("free_shipping")) {
            datos.put("envioGratis", producto.path("shipping").path("free_shipping").asBoolean());
        }
        if (producto.path("shipping").has("local_pick_up")) {
            datos.put("retiroPersonal", producto.path("shipping").path("local_pick_up").asBoolean());
        }
        ponerSiTieneTexto(datos, "garantiaTipo", valorTermino(producto.path("sale_terms"), "WARRANTY_TYPE"));
        ponerSiTieneTexto(datos, "garantiaTiempo", valorTermino(producto.path("sale_terms"), "WARRANTY_TIME"));
        ponerSiTieneTexto(datos, "tiempoDisponibilidad", valorTermino(producto.path("sale_terms"), "MANUFACTURING_TIME"));
        if (fotos.size() > 1) datos.put("fotosUrlsExternas", String.join(System.lineSeparator(), fotos.subList(1, fotos.size())));

        JsonNode descripcion = getOpcional("/items/" + producto.path("id").asText() + "/description");
        if (descripcion != null) ponerSiTieneTexto(datos, "descripcion", descripcion.path("plain_text").asText());
        String atributosJson = serializarAtributosAdicionales(producto.path("attributes"));
        ponerSiTieneTexto(datos, "atributosJson", atributosJson);
        datos.put("atributosItem", valoresAtributos(producto.path("attributes")));

        return new ProductoCanalImportado(producto.path("id").asText(), sku, producto.path("title").asText(),
                stockDisponible(producto), producto.path("price").decimalValue(),
                fotos.isEmpty() ? null : fotos.get(0), producto.path("category_id").asText(null), datos,
                mapearVariantes(producto.path("variations"), producto.path("pictures"),
                        producto.path("id").asText()));
    }

    private String resolverFamilyId(String userProductId) {
        JsonNode userProduct = getOpcional("/user-products/" + userProductId);
        return userProduct == null ? "" : userProduct.path("family_id").asText("");
    }

    List<ProductoCanalImportado> agruparUserProducts(List<ProductoCanalImportado> items) {
        Map<String, List<ProductoCanalImportado>> familias = new LinkedHashMap<>();
        List<ProductoCanalImportado> resultado = new ArrayList<>();
        for (ProductoCanalImportado item : items) {
            String familyId = textoDato(item, "familyId");
            if (familyId.isBlank()) resultado.add(item);
            else familias.computeIfAbsent(familyId, ignorada -> new ArrayList<>()).add(item);
        }
        for (Map.Entry<String, List<ProductoCanalImportado>> entrada : familias.entrySet()) {
            List<ProductoCanalImportado> familia = entrada.getValue();
            resultado.add(familia.size() == 1 ? familia.get(0) : fusionarFamilia(entrada.getKey(), familia));
        }
        return resultado;
    }

    private ProductoCanalImportado fusionarFamilia(String familyId, List<ProductoCanalImportado> items) {
        ProductoCanalImportado base = items.get(0);
        Set<String> atributosVariables = atributosVariables(items);
        List<VarianteCanalImportada> variantes = items.stream()
                .map(item -> convertirItemEnVariante(item, atributosVariables)).toList();
        Map<String, Object> datos = new LinkedHashMap<>(base.datosCanal());
        datos.put("familyId", familyId);
        datos.remove("atributosItem");
        quitarAtributosVariables(datos, atributosVariables);
        String nombreFamilia = textoDato(base, "familyName");
        int stock = items.stream().map(ProductoCanalImportado::cantidad).filter(Objects::nonNull)
                .mapToInt(Integer::intValue).sum();
        return new ProductoCanalImportado(base.idExterno(), null,
                nombreFamilia.isBlank() ? base.descripcion() : nombreFamilia,
                stock, base.precio(), base.fotoUrl(), base.mercadoLibreCategoriaId(), datos, variantes);
    }

    private VarianteCanalImportada convertirItemEnVariante(ProductoCanalImportado item,
                                                             Set<String> atributosVariables) {
        Map<String, String> todos = atributosItem(item);
        Map<String, String> atributos = new LinkedHashMap<>();
        atributosVariables.forEach(id -> {
            String valor = todos.get(id);
            if (valor != null && !valor.isBlank()) atributos.put(id, valor);
        });
        String talle = atributos.getOrDefault("SIZE", "");
        String color = atributos.getOrDefault("COLOR", "");
        String nombre = atributos.values().stream().filter(v -> v != null && !v.isBlank())
                .reduce((a, b) -> a + " / " + b).orElse(item.descripcion());
        return new VarianteCanalImportada(item.idExterno(), item.sku(), nombre, talle, color,
                item.cantidad(), item.precio(), null, todos.get("PRODUCT_NUMBER"), todos.get("GTIN"),
                atributos, item.fotoUrl(), true);
    }

    private Set<String> atributosVariables(List<ProductoCanalImportado> items) {
        Map<String, Set<String>> valores = new LinkedHashMap<>();
        for (ProductoCanalImportado item : items) {
            atributosItem(item).forEach((id, valor) ->
                    valores.computeIfAbsent(id, ignorada -> new LinkedHashSet<>()).add(valor));
        }
        Set<String> ignorados = Set.of("SELLER_SKU", "GTIN", "EAN", "UPC", "PRODUCT_NUMBER",
                "SIZE_GRID_ID", "SIZE_GRID_ROW_ID", "ITEM_CONDITION");
        return valores.entrySet().stream().filter(e -> e.getValue().size() > 1)
                .map(Map.Entry::getKey).filter(id -> !ignorados.contains(id))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> atributosItem(ProductoCanalImportado item) {
        Object valor = item.datosCanal().get("atributosItem");
        return valor instanceof Map<?, ?> mapa ? (Map<String, String>) mapa : Map.of();
    }

    private Map<String, String> valoresAtributos(JsonNode lista) {
        Map<String, String> resultado = new LinkedHashMap<>();
        if (lista.isArray()) for (JsonNode atributo : lista) {
            String id = atributo.path("id").asText();
            String valor = atributo.path("value_name").asText(atributo.path("value_id").asText());
            if (!id.isBlank() && !valor.isBlank()) resultado.put(id, valor);
        }
        return resultado;
    }

    private String textoDato(ProductoCanalImportado item, String clave) {
        Object valor = item.datosCanal().get(clave);
        return valor == null ? "" : valor.toString();
    }

    private void quitarAtributosVariables(Map<String, Object> datos, Set<String> variables) {
        Object json = datos.get("atributosJson");
        if (!(json instanceof String texto) || texto.isBlank() || variables.isEmpty()) return;
        try {
            List<JsonNode> atributos = objectMapper.readValue(texto, new com.fasterxml.jackson.core.type.TypeReference<>() {});
            atributos.removeIf(a -> variables.contains(a.path("id").asText()));
            if (atributos.isEmpty()) datos.remove("atributosJson");
            else datos.put("atributosJson", objectMapper.writeValueAsString(atributos));
        } catch (Exception ignored) {
            // El JSON original ya fue validado al mapear; si no puede filtrarse se conserva.
        }
    }

    List<VarianteCanalImportada> mapearVariantes(JsonNode lista, JsonNode fotosProducto) {
        return mapearVariantes(lista, fotosProducto, "");
    }

    private List<VarianteCanalImportada> mapearVariantes(JsonNode lista, JsonNode fotosProducto,
                                                          String itemId) {
        List<VarianteCanalImportada> resultado = new ArrayList<>();
        if (!lista.isArray()) return resultado;
        Map<String, String> fotosPorId = indexarFotos(fotosProducto);
        for (JsonNode v : lista) {
            String talle = "", color = "";
            Map<String, String> atributosVariante = new LinkedHashMap<>();
            for (JsonNode atributo : v.path("attribute_combinations")) {
                String id = atributo.path("id").asText();
                String valor = atributo.path("value_name").asText(atributo.path("value_id").asText());
                if (!id.isBlank() && !valor.isBlank()) atributosVariante.put(id, valor);
                if ("SIZE".equals(id)) talle = valor;
                if ("COLOR".equals(id)) color = valor;
            }
            Map<String, JsonNode> atributosPropios = indexarAtributos(v.path("attributes"));
            String sku = valorAtributo(atributosPropios, "SELLER_SKU");
            if (sku.isBlank()) sku = v.path("seller_sku").asText();
            if (sku.isBlank()) sku = v.path("seller_custom_field").asText();
            String gtin = valorAtributo(atributosPropios, "GTIN");
            if (gtin.isBlank()) gtin = valorAtributo(atributosPropios, "EAN");
            if (gtin.isBlank()) gtin = valorAtributo(atributosPropios, "UPC");
            String nombre = String.join(" / ", atributosVariante.values());
            String fotoUrl = primeraFoto(v.path("picture_ids"), fotosPorId);
            JsonNode detalleStock = completarDetalleStockVariacion(itemId, v);
            resultado.add(new VarianteCanalImportada(v.path("id").asText(),
                    sku, nombre, talle, color,
                    stockDisponible(detalleStock), v.path("price").decimalValue(), null,
                    v.path("product_id").asText(null), gtin, atributosVariante, fotoUrl, false));
        }
        return resultado;
    }

    private JsonNode completarDetalleStockVariacion(String itemId, JsonNode variacion) {
        if (variacion.path("available_quantity").asInt(0) > 0
                || !variacion.path("user_product_id").asText("").isBlank()
                || !variacion.path("inventory_id").asText("").isBlank()
                || itemId == null || itemId.isBlank()) {
            return variacion;
        }
        String variacionId = variacion.path("id").asText("");
        if (variacionId.isBlank()) return variacion;
        JsonNode detalle = getOpcional("/items/" + itemId + "/variations/" + variacionId
                + "?include_attributes=all");
        return detalle == null ? variacion : detalle;
    }

    private int stockDisponible(JsonNode itemOVariacion) {
        int stockItem = itemOVariacion.path("available_quantity").asInt(0);
        String userProductId = itemOVariacion.path("user_product_id").asText("");
        String inventoryId = itemOVariacion.path("inventory_id").asText("");
        if (stockItem > 0) return stockItem;
        if (!userProductId.isBlank()) {
            try {
                JsonNode stock = obtenerStockConReintentos(
                        "/user-products/" + userProductId + "/stock");
                Integer distribuido = sumarStockUbicaciones(stock);
                if (distribuido != null) return distribuido;
            } catch (RestClientResponseException e) {
                if (!recursoStockNoDisponible(e)) throw e;
            }
        }
        if (!inventoryId.isBlank()) {
            try {
                JsonNode stock = obtenerStockConReintentos(
                        "/inventories/" + inventoryId + "/stock/fulfillment");
                Integer fulfillment = stockFulfillment(stock);
                if (fulfillment != null) return fulfillment;
            } catch (RestClientResponseException e) {
                if (!recursoStockNoDisponible(e)) throw e;
            }
        }
        // No todas las cuentas tienen stock distribuido o Full inicializado.
        return stockItem;
    }

    private boolean recursoStockNoDisponible(RestClientResponseException error) {
        return error.getStatusCode() == HttpStatus.BAD_REQUEST
                || error.getStatusCode() == HttpStatus.NOT_FOUND;
    }

    private JsonNode obtenerStockConReintentos(String endpoint) {
        RestClientResponseException ultimoError = null;
        for (int intento = 1; intento <= 4; intento++) {
            esperarTurnoConsultaStock();
            try {
                return get(endpoint);
            } catch (RestClientResponseException e) {
                ultimoError = e;
                if (e.getStatusCode() != HttpStatus.TOO_MANY_REQUESTS || intento == 4) throw e;
                esperar(retryAfterMs(e));
            }
        }
        throw ultimoError;
    }

    private void esperarTurnoConsultaStock() {
        synchronized (bloqueoConsultasStock) {
            long ahora = System.nanoTime();
            long esperaNanos = Math.max(0L, proximaConsultaStockNanos - ahora);
            if (esperaNanos > 0) esperar((esperaNanos + 999_999L) / 1_000_000L);
            proximaConsultaStockNanos = System.nanoTime() + INTERVALO_STOCK_MS * 1_000_000L;
        }
    }

    private long retryAfterMs(RestClientResponseException error) {
        String valor = error.getResponseHeaders() == null
                ? null : error.getResponseHeaders().getFirst("Retry-After");
        if (valor == null || valor.isBlank()) return 1500L;
        try {
            return Math.max(1000L, Math.min(10_000L, Long.parseLong(valor.trim()) * 1000L));
        } catch (NumberFormatException ignored) {
            return 1500L;
        }
    }

    private void esperar(long milisegundos) {
        try {
            Thread.sleep(milisegundos);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Se interrumpió la consulta de stock de Mercado Libre", e);
        }
    }

    Integer sumarStockUbicaciones(JsonNode stock) {
        if (stock == null || !stock.path("locations").isArray()) return null;
        int total = 0;
        for (JsonNode ubicacion : stock.path("locations")) {
            total += Math.max(0, ubicacion.path("quantity").asInt(0));
        }
        return total;
    }

    Integer stockFulfillment(JsonNode stock) {
        if (stock == null || !stock.has("available_quantity")
                || stock.path("available_quantity").isNull()) {
            return null;
        }
        return Math.max(0, stock.path("available_quantity").asInt(0));
    }

    private Map<String, String> indexarFotos(JsonNode fotos) {
        Map<String, String> resultado = new LinkedHashMap<>();
        if (!fotos.isArray()) return resultado;
        for (JsonNode foto : fotos) {
            String id = foto.path("id").asText();
            String url = foto.path("secure_url").asText(foto.path("url").asText());
            if (!id.isBlank() && !url.isBlank()) resultado.put(id, url);
        }
        return resultado;
    }

    private String primeraFoto(JsonNode ids, Map<String, String> fotosPorId) {
        if (!ids.isArray()) return null;
        for (JsonNode id : ids) {
            String url = fotosPorId.get(id.asText());
            if (url != null && !url.isBlank()) return url;
        }
        return null;
    }

    private Map<String, JsonNode> indexarAtributos(JsonNode lista) {
        Map<String, JsonNode> resultado = new HashMap<>();
        if (lista.isArray()) for (JsonNode atributo : lista) resultado.put(atributo.path("id").asText(), atributo);
        return resultado;
    }

    private String valorAtributo(Map<String, JsonNode> atributos, String id) {
        JsonNode atributo = atributos.get(id);
        if (atributo == null) return "";
        return atributo.path("value_name").asText(atributo.path("value_id").asText());
    }

    private String valorTermino(JsonNode terminos, String id) {
        if (terminos.isArray()) {
            for (JsonNode termino : terminos) {
                if (id.equals(termino.path("id").asText())) {
                    return termino.path("value_name").asText(termino.path("value_id").asText());
                }
            }
        }
        return "";
    }

    private String serializarAtributosAdicionales(JsonNode lista) {
        if (!lista.isArray()) return "";
        List<JsonNode> adicionales = new ArrayList<>();
        for (JsonNode atributo : lista) {
            if (!ATRIBUTOS_CON_CAMPO.contains(atributo.path("id").asText())) adicionales.add(atributo);
        }
        if (adicionales.isEmpty()) return "";
        try { return objectMapper.writeValueAsString(adicionales); }
        catch (JsonProcessingException e) { throw new IllegalStateException("No se pudieron guardar los atributos de Mercado Libre", e); }
    }

    private void ponerSiTieneTexto(Map<String, Object> datos, String clave, String valor) {
        if (valor != null && !valor.isBlank()) datos.put(clave, valor);
    }
}

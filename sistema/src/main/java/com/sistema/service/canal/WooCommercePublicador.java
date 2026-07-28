package com.sistema.service.canal;

import com.fasterxml.jackson.databind.JsonNode;
import com.sistema.model.CanalVenta;
import com.sistema.model.Producto;
import com.sistema.model.ProductoVariante;
import com.sistema.repository.ProductoVarianteRepository;
import com.sistema.service.MercadoLibreAtributosVarianteService;
import com.sistema.service.WooCommerceCredencialesService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.http.client.JdkClientHttpRequestFactory;

import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class WooCommercePublicador implements PublicadorCanal {
    private final RestClient restClient;
    private final ProductoVarianteRepository varianteRepository;
    private final WooCommerceCredencialesService credenciales;
    private final MercadoLibreAtributosVarianteService atributosMercadoLibre;
    private final Map<String, Map<String, String>> nombresAtributosPorCategoria = new ConcurrentHashMap<>();
    @Value("${integraciones.public-base-url:}") private String publicBaseUrl;

    @Autowired
    public WooCommercePublicador(ProductoVarianteRepository varianteRepository,
                                 WooCommerceCredencialesService credenciales,
                                 MercadoLibreAtributosVarianteService atributosMercadoLibre) {
        this(varianteRepository, credenciales, atributosMercadoLibre, crearRestClient());
    }

    WooCommercePublicador(ProductoVarianteRepository varianteRepository,
                          WooCommerceCredencialesService credenciales) {
        this(varianteRepository, credenciales, null, crearRestClient());
    }

    WooCommercePublicador(ProductoVarianteRepository varianteRepository,
                          WooCommerceCredencialesService credenciales,
                          RestClient restClient) {
        this(varianteRepository, credenciales, null, restClient);
    }

    WooCommercePublicador(ProductoVarianteRepository varianteRepository,
                          WooCommerceCredencialesService credenciales,
                          MercadoLibreAtributosVarianteService atributosMercadoLibre,
                          RestClient restClient) {
        this.varianteRepository = varianteRepository;
        this.credenciales = credenciales;
        this.atributosMercadoLibre = atributosMercadoLibre;
        this.restClient = restClient;
    }

    public CanalVenta canal() { return CanalVenta.WOOCOMMERCE; }
    public boolean configurado() { return credenciales.configurado(); }

    public ResultadoPublicacion publicar(Producto p, String idActual) {
        validar();
        WooCommerceCredencialesService.Credenciales c = credenciales.obtener();
        Map<String, Object> body = new LinkedHashMap<>();
        List<ProductoVariante> variantes = varianteRepository.findByProductoIdOrderByNombreAsc(p.getId());
        boolean variable = variantes.size() > 1;
        ProductoVariante presentacionSimple = variantes.size() == 1 ? variantes.get(0) : null;
        validarSkuNoUsadoPorOtraVariante(p, presentacionSimple);
        Map<String, String> nombresAtributos = nombresAtributos(p);
        body.put("name", p.getDescripcion());
        body.put("type", variable ? "variable" : "simple");
        body.put("sku", presentacionSimple == null ? p.getSku() : presentacionSimple.getSku());
        body.put("status", "publish");
        List<Map<String, Object>> atributosProducto = atributosWoo(variantes, nombresAtributos);
        if (!atributosProducto.isEmpty()) body.put("attributes", atributosProducto);
        if (variable) {
            int stockTotal = variantes.stream().mapToInt(this::stock).sum();
            body.put("manage_stock", false);
            body.put("stock_status", estadoStock(stockTotal));
        } else {
            int stock = presentacionSimple == null
                    ? Optional.ofNullable(p.getCantidad()).orElse(0)
                    : stock(presentacionSimple);
            body.put("regular_price", presentacionSimple == null ? precio(p) : precio(presentacionSimple));
            body.put("manage_stock", true);
            body.put("stock_quantity", stock);
            body.put("stock_status", estadoStock(stock));
            body.put("backorders", "no");
        }
        agregarImagen(body, p, presentacionSimple);
        String idProducto = idActual;
        if (idProducto == null || idProducto.isBlank()) {
            idProducto = buscarProductoPorSku(Objects.toString(body.get("sku"), ""), c);
        }
        JsonNode response;
        try {
            response = enviarProducto(body, idProducto, c);
        } catch (RestClientResponseException e) {
            if (!esVinculoProductoInvalido(e) || idProducto == null || idProducto.isBlank()) throw e;
            String encontrado = buscarProductoPorSku(Objects.toString(body.get("sku"), ""), c);
            idProducto = encontrado;
            response = enviarProducto(body, idProducto, c);
        }
        idProducto = response == null ? idProducto : response.path("id").asText(idProducto);
        if (variable) publicarVariantes(idProducto, variantes, idsAtributosVariacion(variantes),
                nombresAtributos, c);
        return new ResultadoPublicacion(idProducto);
    }

    private boolean esVinculoProductoInvalido(RestClientResponseException error) {
        if (error.getStatusCode() == HttpStatus.NOT_FOUND) return true;
        if (error.getStatusCode() != HttpStatus.BAD_REQUEST) return false;
        String respuesta = error.getResponseBodyAsString();
        return respuesta != null && respuesta.contains("woocommerce_rest_product_invalid_id");
    }

    public void sincronizarStock(Producto producto, String productoWooId) {
        validar();
        WooCommerceCredencialesService.Credenciales c = credenciales.obtener();
        if (productoWooId == null || productoWooId.isBlank()) {
            throw new IllegalArgumentException("El producto no tiene una publicación vinculada de WooCommerce");
        }
        List<ProductoVariante> variantes = varianteRepository.findByProductoIdOrderByNombreAsc(producto.getId());
        if (variantes.size() <= 1) {
            int stock = variantes.isEmpty() ? Optional.ofNullable(producto.getCantidad()).orElse(0)
                    : stock(variantes.get(0));
            putStock(c.url() + "/wp-json/wc/v3/products/" + productoWooId, stock, c);
            return;
        }
        int stockTotal = variantes.stream().mapToInt(this::stock).sum();
        putEstadoProductoVariable(c.url() + "/wp-json/wc/v3/products/" + productoWooId, stockTotal, c);
        for (ProductoVariante variante : variantes) {
            String variacionWooId = variante.getWooCommerceVariationId();
            if (variacionWooId == null || variacionWooId.isBlank()) {
                throw new IllegalStateException("La variante " + variante.getSku()
                        + " no está vinculada con una variante de WooCommerce; publíquela nuevamente una vez");
            }
            putStock(c.url() + "/wp-json/wc/v3/products/" + productoWooId
                    + "/variations/" + variacionWooId, stock(variante), c);
        }
    }

    private void putStock(String endpoint, int stock, WooCommerceCredencialesService.Credenciales c) {
        restClient.put().uri(endpoint).headers(h -> h.setBasicAuth(c.key(), c.secret()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "manage_stock", true,
                        "stock_quantity", stock,
                        "stock_status", estadoStock(stock),
                        "backorders", "no"))
                .retrieve().toBodilessEntity();
    }

    private void putEstadoProductoVariable(String endpoint, int stockTotal,
                                           WooCommerceCredencialesService.Credenciales c) {
        restClient.put().uri(endpoint).headers(h -> h.setBasicAuth(c.key(), c.secret()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "manage_stock", false,
                        "stock_status", estadoStock(stockTotal)))
                .retrieve().toBodilessEntity();
    }

    List<Map<String, Object>> atributosWoo(List<ProductoVariante> variantes) {
        return atributosWoo(variantes, Map.of());
    }

    private List<Map<String, Object>> atributosWoo(List<ProductoVariante> variantes,
                                                   Map<String, String> nombresAtributos) {
        List<Map<String, Object>> atributos = new ArrayList<>();
        Map<String, LinkedHashSet<String>> opciones = opcionesAtributos(variantes);
        opciones.forEach((id, valores) -> atributos.add(Map.of("name", nombreAtributo(id, nombresAtributos),
                "visible", true, "variation", valores.size() > 1, "options", new ArrayList<>(valores))));
        return atributos;
    }

    private Set<String> idsAtributosVariacion(List<ProductoVariante> variantes) {
        Set<String> ids = new LinkedHashSet<>();
        opcionesAtributos(variantes).forEach((id, valores) -> {
            if (valores.size() > 1) ids.add(id);
        });
        return ids;
    }

    private Map<String, LinkedHashSet<String>> opcionesAtributos(List<ProductoVariante> variantes) {
        Map<String, LinkedHashSet<String>> opciones = new LinkedHashMap<>();
        variantes.forEach(v -> AtributosVarianteHelper.obtener(v).forEach((id, valor) -> {
            if (valor != null && !valor.isBlank()) {
                opciones.computeIfAbsent(id, k -> new LinkedHashSet<>()).add(valor);
            }
        }));
        return opciones;
    }

    private void publicarVariantes(String productoId, List<ProductoVariante> variantes,
                                    Set<String> idsAtributosVariacion,
                                    Map<String, String> nombresAtributos,
                                    WooCommerceCredencialesService.Credenciales c) {
        for (ProductoVariante variante : variantes) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("regular_price", Optional.ofNullable(variante.getPrecioContado()).orElse(variante.getProducto().getPrecioContado()).toPlainString());
            int stock = stock(variante);
            body.put("sku", variante.getSku());
            body.put("status", "publish");
            body.put("manage_stock", true);
            body.put("stock_quantity", stock);
            body.put("stock_status", estadoStock(stock));
            body.put("backorders", "no");
            List<Map<String, String>> atributos = new ArrayList<>();
            AtributosVarianteHelper.obtener(variante).forEach((id, valor) -> {
                if (idsAtributosVariacion.contains(id)) {
                    atributos.add(Map.of("name", nombreAtributo(id, nombresAtributos), "option", valor));
                }
            });
            body.put("attributes", atributos);
            String imagen = FotoCanalHelper.resolverUrl(variante, publicBaseUrl);
            if (imagen != null && !imagen.isBlank()) body.put("image", Map.of("src", imagen));
            String actual = variante.getWooCommerceVariationId();
            if (actual == null || actual.isBlank()) actual = buscarVariacionPorSku(productoId, variante.getSku(), c);
            JsonNode response = enviarVariacion(productoId, body, actual, c);
            if (response != null) { variante.setWooCommerceVariationId(response.path("id").asText(actual)); varianteRepository.save(variante); }
        }
    }

    private JsonNode enviarProducto(Map<String, Object> body, String id,
                                    WooCommerceCredencialesService.Credenciales c) {
        boolean nuevo = id == null || id.isBlank();
        String endpoint = c.url() + "/wp-json/wc/v3/products" + (nuevo ? "" : "/" + id);
        RestClient.RequestBodySpec request = (nuevo ? restClient.post() : restClient.put()).uri(endpoint)
                .headers(h -> h.setBasicAuth(c.key(), c.secret())).contentType(MediaType.APPLICATION_JSON);
        return request.body(body).retrieve().body(JsonNode.class);
    }

    private JsonNode enviarVariacion(String productoId, Map<String, Object> body, String id,
                                     WooCommerceCredencialesService.Credenciales c) {
        boolean nueva = id == null || id.isBlank();
        String endpoint = c.url() + "/wp-json/wc/v3/products/" + productoId
                + "/variations" + (nueva ? "" : "/" + id);
        RestClient.RequestBodySpec request = (nueva ? restClient.post() : restClient.put()).uri(endpoint)
                .headers(h -> h.setBasicAuth(c.key(), c.secret())).contentType(MediaType.APPLICATION_JSON);
        return request.body(body).retrieve().body(JsonNode.class);
    }

    private String buscarProductoPorSku(String sku, WooCommerceCredencialesService.Credenciales c) {
        if (sku == null || sku.isBlank()) return null;
        for (String estado : List.of("any", "trash")) {
            String endpoint = c.url() + "/wp-json/wc/v3/products?sku="
                    + URLEncoder.encode(sku, StandardCharsets.UTF_8)
                    + "&status=" + estado + "&per_page=100";
            JsonNode respuesta = restClient.get().uri(endpoint)
                    .headers(h -> h.setBasicAuth(c.key(), c.secret())).retrieve().body(JsonNode.class);
            if (respuesta != null && respuesta.isArray()) {
                for (JsonNode producto : respuesta) {
                    if (sku.equalsIgnoreCase(producto.path("sku").asText())) {
                        if ("variation".equalsIgnoreCase(producto.path("type").asText())) {
                            throw new IllegalStateException("El SKU " + sku
                                    + " ya existe como variante de otro producto en WooCommerce"
                                    + " (producto padre " + producto.path("parent_id").asText("?") + "). "
                                    + "WooCommerce no permite usar el mismo SKU también en un producto independiente.");
                        }
                        return producto.path("id").asText();
                    }
                }
            }
        }
        return null;
    }

    private Map<String, String> nombresAtributos(Producto producto) {
        String categoria = producto.getMercadoLibreCategoriaId();
        if (atributosMercadoLibre == null || categoria == null || categoria.isBlank()) return Map.of();
        Map<String, String> guardados = nombresAtributosPorCategoria.get(categoria);
        if (guardados != null) return guardados;
        try {
            Map<String, String> nombres = atributosMercadoLibre.obtener(producto).atributos().stream()
                    .filter(atributo -> atributo.id() != null && atributo.nombre() != null
                            && !atributo.id().isBlank() && !atributo.nombre().isBlank())
                    .collect(Collectors.toMap(
                            atributo -> atributo.id().toUpperCase(Locale.ROOT),
                            atributo -> atributo.nombre().trim(),
                            (primero, ignorado) -> primero,
                            LinkedHashMap::new));
            if (!nombres.isEmpty()) nombresAtributosPorCategoria.put(categoria, Map.copyOf(nombres));
            return nombres;
        } catch (RuntimeException ignored) {
            // WooCommerce puede seguir publicando con las traducciones locales de respaldo.
            return Map.of();
        }
    }

    private String nombreAtributo(String id, Map<String, String> nombresAtributos) {
        if (id == null) return "";
        return nombresAtributos.getOrDefault(
                id.toUpperCase(Locale.ROOT), AtributosVarianteHelper.nombre(id));
    }

    private void validarSkuNoUsadoPorOtraVariante(Producto producto, ProductoVariante presentacionSimple) {
        String sku = presentacionSimple == null ? producto.getSku() : presentacionSimple.getSku();
        if (sku == null || sku.isBlank()) return;
        varianteRepository.findBySkuIgnoreCase(sku)
                .filter(variante -> variante.getProducto() != null
                        && !Objects.equals(variante.getProducto().getId(), producto.getId()))
                .ifPresent(variante -> {
                    Producto propietario = variante.getProducto();
                    throw new IllegalStateException("El SKU " + sku + " ya pertenece a una variante de "
                            + propietario.getSku() + " / " + propietario.getDescripcion()
                            + ". WooCommerce exige SKU únicos: cambie el SKU del producto independiente "
                            + "o elimínelo si es un duplicado.");
                });
    }

    private String buscarVariacionPorSku(String productoId, String sku,
                                         WooCommerceCredencialesService.Credenciales c) {
        if (sku == null || sku.isBlank()) return null;
        String endpoint = c.url() + "/wp-json/wc/v3/products/" + productoId
                + "/variations?sku=" + URLEncoder.encode(sku, StandardCharsets.UTF_8) + "&per_page=100";
        JsonNode respuesta = restClient.get().uri(endpoint)
                .headers(h -> h.setBasicAuth(c.key(), c.secret())).retrieve().body(JsonNode.class);
        if (respuesta != null && respuesta.isArray()) {
            for (JsonNode variacion : respuesta) {
                if (sku.equalsIgnoreCase(variacion.path("sku").asText())) return variacion.path("id").asText();
            }
        }
        return null;
    }

    private static RestClient crearRestClient() {
        return RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                        .version(HttpClient.Version.HTTP_1_1)
                        .build()))
                .build();
    }

    private void agregarImagen(Map<String, Object> body, Producto p, ProductoVariante presentacionSimple) {
        String src = presentacionSimple == null ? FotoCanalHelper.resolverUrl(p, publicBaseUrl)
                : FotoCanalHelper.resolverUrl(presentacionSimple, publicBaseUrl);
        if (src != null && !src.isBlank()) body.put("images", List.of(Map.of("src", src)));
    }
    private String precio(Producto p) { return Optional.ofNullable(p.getPrecioContado()).orElseThrow(() -> new IllegalArgumentException("El producto no tiene precio de contado")).toPlainString(); }
    private String precio(ProductoVariante v) { return Optional.ofNullable(v.getPrecioContado()).orElse(v.getProducto().getPrecioContado()).toPlainString(); }
    private int stock(ProductoVariante variante) { return Optional.ofNullable(variante.getStock()).orElse(0); }
    private String estadoStock(int stock) { return stock > 0 ? "instock" : "outofstock"; }
    private void validar() { if (!configurado()) throw new IllegalStateException("WooCommerce no está configurado"); }
}

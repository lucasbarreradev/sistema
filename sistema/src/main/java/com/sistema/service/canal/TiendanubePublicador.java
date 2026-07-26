package com.sistema.service.canal;

import com.fasterxml.jackson.databind.JsonNode;
import com.sistema.model.CanalVenta;
import com.sistema.model.Producto;
import com.sistema.model.ProductoVariante;
import com.sistema.repository.ProductoVarianteRepository;
import com.sistema.service.TiendanubeCredencialesService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.*;

@Component
public class TiendanubePublicador implements PublicadorCanal {
    private final RestClient restClient;
    private final ProductoVarianteRepository varianteRepository;
    private final TiendanubeCredencialesService credenciales;
    @Value("${integraciones.public-base-url:}") private String publicBaseUrl;

    @Autowired
    public TiendanubePublicador(ProductoVarianteRepository varianteRepository,
                                TiendanubeCredencialesService credenciales) {
        this(varianteRepository, credenciales, RestClient.create());
    }

    TiendanubePublicador(ProductoVarianteRepository varianteRepository,
                         TiendanubeCredencialesService credenciales,
                         RestClient restClient) {
        this.varianteRepository = varianteRepository;
        this.credenciales = credenciales;
        this.restClient = restClient;
    }

    public CanalVenta canal() { return CanalVenta.TIENDANUBE; }
    public boolean configurado() { return credenciales.configurado(); }

    public ResultadoPublicacion publicar(Producto p, String idActual) {
        validar();
        TiendanubeCredencialesService.Credenciales c = credenciales.obtener();
        List<ProductoVariante> variantesSistema = varianteRepository.findByProductoIdOrderByNombreAsc(p.getId());
        boolean variable = variantesSistema.size() > 1;
        List<Map<String, Object>> variantes = variable
                ? variantesSistema.stream().map(this::varianteTiendaNube).toList()
                : List.of(variantesSistema.isEmpty() ? varianteSimple(p) : presentacionSimple(variantesSistema.get(0)));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", Map.of("es", p.getDescripcion()));
        if (variable) {
            List<Map<String, String>> atributos = new ArrayList<>();
            LinkedHashSet<String> ids = new LinkedHashSet<>();
            variantesSistema.forEach(v -> ids.addAll(AtributosVarianteHelper.obtener(v).keySet()));
            ids.forEach(id -> atributos.add(Map.of("es", AtributosVarianteHelper.nombre(id))));
            body.put("attributes", atributos);
        }
        body.put("variants", variantes);
        if (p.tieneFoto()) {
            String src = FotoCanalHelper.resolverUrl(p, publicBaseUrl);
            if (src != null && !src.isBlank()) body.put("images", List.of(Map.of("src", src)));
        }
        String endpoint = "https://api.tiendanube.com/v1/" + c.storeId() + "/products" + (idActual == null ? "" : "/" + idActual);
        RestClient.RequestBodySpec request = (idActual == null ? restClient.post() : restClient.put()).uri(endpoint)
                .headers(h -> { h.setBearerAuth(c.token()); h.set("User-Agent", c.userAgent()); }).contentType(MediaType.APPLICATION_JSON);
        JsonNode response;
        try {
            response = request.body(body).retrieve().body(JsonNode.class);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode() != HttpStatus.NOT_FOUND || idActual == null || idActual.isBlank()) throw e;
            // El vínculo puede pertenecer a una tienda anterior o a un producto eliminado.
            // Los IDs de variantes tampoco son reutilizables al crear el producto nuevamente.
            variantes.forEach(variante -> variante.remove("id"));
            variantesSistema.forEach(variante -> {
                variante.setTiendaNubeVariationId(null);
                varianteRepository.save(variante);
            });
            response = restClient.post()
                    .uri("https://api.tiendanube.com/v1/" + c.storeId() + "/products")
                    .headers(h -> { h.setBearerAuth(c.token()); h.set("User-Agent", c.userAgent()); })
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body).retrieve().body(JsonNode.class);
        }
        if (response != null && response.path("variants").isArray()) {
            for (JsonNode externa : response.path("variants")) {
                String sku = externa.path("sku").asText();
                variantesSistema.stream().filter(v -> v.getSku().equalsIgnoreCase(sku)).findFirst().ifPresent(v -> {
                    v.setTiendaNubeVariationId(externa.path("id").asText()); varianteRepository.save(v);
                });
            }
        }
        return new ResultadoPublicacion(response == null ? idActual : response.path("id").asText(idActual));
    }

    public void sincronizarStock(Producto producto, String productoTiendaNubeId) {
        validar();
        TiendanubeCredencialesService.Credenciales c = credenciales.obtener();
        if (productoTiendaNubeId == null || productoTiendaNubeId.isBlank()) {
            throw new IllegalArgumentException("El producto no tiene una publicación vinculada de Tiendanube");
        }
        List<ProductoVariante> variantes = varianteRepository.findByProductoIdOrderByNombreAsc(producto.getId());
        Map<String, String> idsPorSku = obtenerIdsVariantes(productoTiendaNubeId, c);
        if (variantes.isEmpty()) {
            String idVariante = idsPorSku.get(producto.getSku());
            if (idVariante == null && idsPorSku.size() == 1) idVariante = idsPorSku.values().iterator().next();
            reemplazarStock(productoTiendaNubeId, idVariante,
                    Optional.ofNullable(producto.getCantidad()).orElse(0), c);
            return;
        }
        for (ProductoVariante variante : variantes) {
            String idVariante = variante.getTiendaNubeVariationId();
            if (idVariante == null || idVariante.isBlank()) idVariante = idsPorSku.get(variante.getSku());
            if (idVariante == null || idVariante.isBlank()) {
                throw new IllegalStateException("La presentación " + variante.getSku()
                        + " no está vinculada con Tiendanube; publíquela nuevamente una vez");
            }
            reemplazarStock(productoTiendaNubeId, idVariante, Optional.ofNullable(variante.getStock()).orElse(0), c);
        }
    }

    private Map<String, String> obtenerIdsVariantes(String productoId,
                                                    TiendanubeCredencialesService.Credenciales c) {
        JsonNode respuesta = restClient.get()
                .uri("https://api.tiendanube.com/v1/" + c.storeId() + "/products/" + productoId + "/variants")
                .headers(h -> { h.setBearerAuth(c.token()); h.set("User-Agent", c.userAgent()); })
                .retrieve().body(JsonNode.class);
        Map<String, String> ids = new LinkedHashMap<>();
        if (respuesta != null && respuesta.isArray()) {
            for (JsonNode variante : respuesta) {
                ids.put(variante.path("sku").asText(""), variante.path("id").asText());
            }
        }
        return ids;
    }

    private void reemplazarStock(String productoId, String varianteId, int stock,
                                 TiendanubeCredencialesService.Credenciales c) {
        if (varianteId == null || varianteId.isBlank()) {
            throw new IllegalStateException("Tiendanube no devolvió la presentación necesaria para actualizar el stock");
        }
        restClient.post()
                .uri("https://api.tiendanube.com/v1/" + c.storeId() + "/products/" + productoId + "/variants/stock")
                .headers(h -> { h.setBearerAuth(c.token()); h.set("User-Agent", c.userAgent()); })
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("action", "replace", "value", stock, "id", Long.parseLong(varianteId)))
                .retrieve().toBodilessEntity();
    }
    private Map<String, Object> varianteSimple(Producto p) {
        Map<String, Object> variante = new LinkedHashMap<>();
        variante.put("price", Optional.ofNullable(p.getPrecioContado()).orElseThrow(() -> new IllegalArgumentException("El producto no tiene precio de contado")).toPlainString());
        variante.put("stock_management", true); variante.put("stock", Optional.ofNullable(p.getCantidad()).orElse(0)); variante.put("sku", p.getSku());
        return variante;
    }
    private Map<String, Object> presentacionSimple(ProductoVariante v) {
        Map<String, Object> variante = new LinkedHashMap<>();
        if (v.getTiendaNubeVariationId() != null) variante.put("id", v.getTiendaNubeVariationId());
        variante.put("price", Optional.ofNullable(v.getPrecioContado()).orElse(v.getProducto().getPrecioContado()).toPlainString());
        variante.put("stock_management", true);
        variante.put("stock", Optional.ofNullable(v.getStock()).orElse(0));
        variante.put("sku", v.getSku());
        return variante;
    }
    private Map<String, Object> varianteTiendaNube(ProductoVariante v) {
        Map<String, Object> variante = new LinkedHashMap<>();
        if (v.getTiendaNubeVariationId() != null) variante.put("id", v.getTiendaNubeVariationId());
        variante.put("price", Optional.ofNullable(v.getPrecioContado()).orElse(v.getProducto().getPrecioContado()).toPlainString());
        variante.put("stock_management", true); variante.put("stock", v.getStock()); variante.put("sku", v.getSku());
        List<Map<String, String>> valores = new ArrayList<>();
        AtributosVarianteHelper.obtener(v).values().forEach(valor -> valores.add(Map.of("es", valor)));
        variante.put("values", valores);
        return variante;
    }
    private void validar() { if (!configurado()) throw new IllegalStateException("Tiendanube no está configurada"); }
    private String limpiarUrl(String value) { return value.replaceAll("/+$", ""); }
}

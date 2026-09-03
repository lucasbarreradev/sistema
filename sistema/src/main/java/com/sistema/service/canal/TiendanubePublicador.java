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
    private static final List<String> ATRIBUTOS_PRIORITARIOS = List.of(
            "COLOR", "SIZE",
            "SECTION_WIDTH", "TIRE_WIDTH", "ASPECT_RATIO", "RIM_DIAMETER", "DIAMETER",
            "STORAGE_CAPACITY", "INTERNAL_MEMORY", "RAM",
            "VOLTAGE", "FABRIC_DESIGN", "MATERIAL", "PATTERN_NAME", "MODEL", "MAIN_COLOR"
    );

    private final RestClient restClient;
    private final ProductoVarianteRepository varianteRepository;
    private final TiendanubeCredencialesService credenciales;
    @Value("${integraciones.public-base-url:}")
    private String publicBaseUrl;

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

    public CanalVenta canal() {
        return CanalVenta.TIENDANUBE;
    }

    public boolean configurado() {
        return credenciales.configurado();
    }

    public ResultadoPublicacion publicar(Producto producto, String idActual) {
        validar();
        TiendanubeCredencialesService.Credenciales c = credenciales.obtener();
        List<ProductoVariante> variantesSistema =
                varianteRepository.findByProductoIdOrderByNombreAsc(producto.getId());
        boolean variable = variantesSistema.size() > 1;
        List<String> idsAtributos = variable ? atributosDeVariacion(variantesSistema) : List.of();
        List<Map<String, Object>> variantes = variable
                ? variantesSistema.stream().map(v -> varianteTiendaNube(v, idsAtributos)).toList()
                : List.of(variantesSistema.isEmpty()
                        ? varianteSimple(producto)
                        : presentacionSimple(variantesSistema.get(0)));

        Map<String, Object> datosProducto = new LinkedHashMap<>();
        datosProducto.put("name", Map.of("es", tituloTiendanube(producto)));
        if (variable) {
            List<Map<String, String>> atributos = idsAtributos.stream()
                    .map(id -> Map.of("es", AtributosVarianteHelper.nombre(id)))
                    .toList();
            datosProducto.put("attributes", atributos);
        }

        if (idActual == null || idActual.isBlank()) {
            return crearProducto(producto, datosProducto, variantes, variantesSistema, c);
        }

        try {
            actualizarProducto(idActual, datosProducto, c);
            JsonNode variantesActualizadas = actualizarVariantes(
                    producto, idActual, variable, variantes, variantesSistema, c);
            guardarIdsVariantes(variantesActualizadas, variantesSistema);
            sincronizarImagenSiFalta(producto, idActual, c);
            return new ResultadoPublicacion(idActual);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode() != HttpStatus.NOT_FOUND) throw e;
            // El vínculo puede pertenecer a una tienda anterior o a un producto eliminado.
            limpiarIdsVariantes(variantesSistema);
            variantes.forEach(variante -> variante.remove("id"));
            return crearProducto(producto, datosProducto, variantes, variantesSistema, c);
        }
    }

    private ResultadoPublicacion crearProducto(
            Producto producto,
            Map<String, Object> datosProducto,
            List<Map<String, Object>> variantes,
            List<ProductoVariante> variantesSistema,
            TiendanubeCredencialesService.Credenciales c) {
        Map<String, Object> body = new LinkedHashMap<>(datosProducto);
        body.put("variants", variantes);
        String src = producto.tieneFoto() ? FotoCanalHelper.resolverUrl(producto, publicBaseUrl) : null;
        if (src != null && !src.isBlank()) body.put("images", List.of(Map.of("src", src)));

        JsonNode response = restClient.post()
                .uri(api(c) + "/products")
                .headers(h -> autenticar(h, c))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        guardarIdsVariantes(response, variantesSistema);
        return new ResultadoPublicacion(response == null ? null : response.path("id").asText(null));
    }

    private void actualizarProducto(String productoId, Map<String, Object> datosProducto,
                                    TiendanubeCredencialesService.Credenciales c) {
        restClient.put()
                .uri(api(c) + "/products/" + productoId)
                .headers(h -> autenticar(h, c))
                .contentType(MediaType.APPLICATION_JSON)
                .body(datosProducto)
                .retrieve()
                .toBodilessEntity();
    }

    private JsonNode actualizarVariantes(
            Producto producto,
            String productoId,
            boolean variable,
            List<Map<String, Object>> variantes,
            List<ProductoVariante> variantesSistema,
            TiendanubeCredencialesService.Credenciales c) {
        if (variable) {
            variantes.forEach(variante -> variante.remove("id"));
            return restClient.put()
                    .uri(api(c) + "/products/" + productoId + "/variants")
                    .headers(h -> autenticar(h, c))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(variantes)
                    .retrieve()
                    .body(JsonNode.class);
        }

        Map<String, Object> variante = new LinkedHashMap<>(variantes.get(0));
        String varianteId = Objects.toString(variante.remove("id"), null);
        if (varianteId == null || varianteId.isBlank()) {
            Map<String, String> idsPorSku = obtenerIdsVariantes(productoId, c);
            String sku = variantesSistema.isEmpty()
                    ? producto.getSku()
                    : variantesSistema.get(0).getSku();
            varianteId = idsPorSku.get(sku);
            if ((varianteId == null || varianteId.isBlank()) && idsPorSku.size() == 1) {
                varianteId = idsPorSku.values().iterator().next();
            }
        }
        if (varianteId == null || varianteId.isBlank()) {
            throw new IllegalStateException(
                    "Tiendanube no devolvió la presentación del producto " + producto.getSku());
        }
        variante.remove("stock_management");
        return restClient.put()
                .uri(api(c) + "/products/" + productoId + "/variants/" + varianteId)
                .headers(h -> autenticar(h, c))
                .contentType(MediaType.APPLICATION_JSON)
                .body(variante)
                .retrieve()
                .body(JsonNode.class);
    }

    private void sincronizarImagenSiFalta(
            Producto producto,
            String productoId,
            TiendanubeCredencialesService.Credenciales c) {
        if (!producto.tieneFoto()) return;
        String src = FotoCanalHelper.resolverUrl(producto, publicBaseUrl);
        if (src == null || src.isBlank()) return;

        JsonNode imagenes = restClient.get()
                .uri(api(c) + "/products/" + productoId + "/images")
                .headers(h -> autenticar(h, c))
                .retrieve()
                .body(JsonNode.class);
        if (imagenes != null && imagenes.isArray() && !imagenes.isEmpty()) return;

        restClient.post()
                .uri(api(c) + "/products/" + productoId + "/images")
                .headers(h -> autenticar(h, c))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("src", src))
                .retrieve()
                .toBodilessEntity();
    }

    private void guardarIdsVariantes(JsonNode response, List<ProductoVariante> variantesSistema) {
        if (response == null || variantesSistema.isEmpty()) return;
        if (response.isArray()) {
            response.forEach(externa -> guardarIdVariante(externa, variantesSistema));
        } else if (response.path("variants").isArray()) {
            response.path("variants").forEach(externa -> guardarIdVariante(externa, variantesSistema));
        } else {
            guardarIdVariante(response, variantesSistema);
        }
    }

    private void guardarIdVariante(JsonNode externa, List<ProductoVariante> variantesSistema) {
        String id = externa.path("id").asText();
        if (id.isBlank()) return;
        String sku = externa.path("sku").asText();
        Optional<ProductoVariante> encontrada = variantesSistema.stream()
                .filter(v -> !sku.isBlank() && v.getSku().equalsIgnoreCase(sku))
                .findFirst();
        if (encontrada.isEmpty() && variantesSistema.size() == 1) {
            encontrada = Optional.of(variantesSistema.get(0));
        }
        encontrada.ifPresent(v -> {
            v.setTiendaNubeVariationId(id);
            varianteRepository.save(v);
        });
    }

    private void limpiarIdsVariantes(List<ProductoVariante> variantesSistema) {
        variantesSistema.forEach(variante -> {
            variante.setTiendaNubeVariationId(null);
            varianteRepository.save(variante);
        });
    }

    public void sincronizarStock(Producto producto, String productoTiendaNubeId) {
        validar();
        TiendanubeCredencialesService.Credenciales c = credenciales.obtener();
        if (productoTiendaNubeId == null || productoTiendaNubeId.isBlank()) {
            throw new IllegalArgumentException("El producto no tiene una publicación vinculada de Tiendanube");
        }
        List<ProductoVariante> variantes =
                varianteRepository.findByProductoIdOrderByNombreAsc(producto.getId());
        Map<String, String> idsPorSku = obtenerIdsVariantes(productoTiendaNubeId, c);
        if (variantes.isEmpty()) {
            String idVariante = idsPorSku.get(producto.getSku());
            if (idVariante == null && idsPorSku.size() == 1) {
                idVariante = idsPorSku.values().iterator().next();
            }
            reemplazarStock(productoTiendaNubeId, idVariante,
                    Optional.ofNullable(producto.getCantidad()).orElse(0), c);
            return;
        }
        for (ProductoVariante variante : variantes) {
            String idVariante = variante.getTiendaNubeVariationId();
            if (idVariante == null || idVariante.isBlank()) {
                idVariante = idsPorSku.get(variante.getSku());
            }
            if (idVariante == null || idVariante.isBlank()) {
                throw new IllegalStateException("La presentación " + variante.getSku()
                        + " no está vinculada con Tiendanube; publíquela nuevamente una vez");
            }
            reemplazarStock(productoTiendaNubeId, idVariante,
                    Optional.ofNullable(variante.getStock()).orElse(0), c);
        }
    }

    private Map<String, String> obtenerIdsVariantes(
            String productoId,
            TiendanubeCredencialesService.Credenciales c) {
        JsonNode respuesta = restClient.get()
                .uri(api(c) + "/products/" + productoId + "/variants")
                .headers(h -> autenticar(h, c))
                .retrieve()
                .body(JsonNode.class);
        Map<String, String> ids = new LinkedHashMap<>();
        if (respuesta != null && respuesta.isArray()) {
            for (JsonNode variante : respuesta) {
                ids.put(variante.path("sku").asText(""), variante.path("id").asText());
            }
        }
        return ids;
    }

    private void reemplazarStock(
            String productoId,
            String varianteId,
            int stock,
            TiendanubeCredencialesService.Credenciales c) {
        if (varianteId == null || varianteId.isBlank()) {
            throw new IllegalStateException(
                    "Tiendanube no devolvió la presentación necesaria para actualizar el stock");
        }
        restClient.post()
                .uri(api(c) + "/products/" + productoId + "/variants/stock")
                .headers(h -> autenticar(h, c))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("action", "replace", "value", stock, "id", Long.parseLong(varianteId)))
                .retrieve()
                .toBodilessEntity();
    }

    private Map<String, Object> varianteSimple(Producto producto) {
        Map<String, Object> variante = new LinkedHashMap<>();
        variante.put("price", Optional.ofNullable(producto.getPrecioContado())
                .orElseThrow(() -> new IllegalArgumentException("El producto no tiene precio de contado"))
                .toPlainString());
        variante.put("stock", Optional.ofNullable(producto.getCantidad()).orElse(0));
        variante.put("sku", producto.getSku());
        return variante;
    }

    private Map<String, Object> presentacionSimple(ProductoVariante varianteSistema) {
        Map<String, Object> variante = datosComunesVariante(varianteSistema);
        if (varianteSistema.getTiendaNubeVariationId() != null
                && !varianteSistema.getTiendaNubeVariationId().isBlank()) {
            variante.put("id", varianteSistema.getTiendaNubeVariationId());
        }
        return variante;
    }

    private Map<String, Object> varianteTiendaNube(
            ProductoVariante varianteSistema,
            List<String> idsAtributos) {
        Map<String, Object> variante = datosComunesVariante(varianteSistema);
        if (varianteSistema.getTiendaNubeVariationId() != null
                && !varianteSistema.getTiendaNubeVariationId().isBlank()) {
            variante.put("id", varianteSistema.getTiendaNubeVariationId());
        }
        Map<String, String> atributos = atributosNormalizados(varianteSistema);
        List<Map<String, String>> valores = new ArrayList<>();
        for (String id : idsAtributos) {
            String valor = atributos.get(id);
            if (valor == null || valor.isBlank()) {
                throw new IllegalArgumentException("La variante " + varianteSistema.getSku()
                        + " no tiene un valor para " + AtributosVarianteHelper.nombre(id));
            }
            valores.add(Map.of("es", valor));
        }
        variante.put("values", valores);
        return variante;
    }

    private Map<String, Object> datosComunesVariante(ProductoVariante varianteSistema) {
        Map<String, Object> variante = new LinkedHashMap<>();
        variante.put("price", Optional.ofNullable(varianteSistema.getPrecioContado())
                .orElse(Optional.ofNullable(varianteSistema.getProducto().getPrecioContado())
                        .orElseThrow(() -> new IllegalArgumentException(
                                "El producto " + varianteSistema.getProducto().getSku()
                                        + " no tiene precio de contado")))
                .toPlainString());
        variante.put("stock", Optional.ofNullable(varianteSistema.getStock()).orElse(0));
        variante.put("sku", varianteSistema.getSku());
        return variante;
    }

    private List<String> atributosDeVariacion(List<ProductoVariante> variantes) {
        List<Map<String, String>> atributosPorVariante = variantes.stream()
                .map(this::atributosNormalizados)
                .toList();
        LinkedHashSet<String> comunes = new LinkedHashSet<>(atributosPorVariante.get(0).keySet());
        atributosPorVariante.forEach(atributos -> comunes.retainAll(atributos.keySet()));

        List<String> candidatos = comunes.stream()
                .filter(id -> !atributoTecnico(id))
                .filter(id -> cantidadValoresDistintos(atributosPorVariante, id) > 1)
                .sorted(Comparator.comparingInt(this::prioridadAtributo))
                .toList();
        if (candidatos.isEmpty()) {
            throw new IllegalArgumentException(
                    "Las variantes no tienen Color, Talle u otro atributo diferente para publicarlas en Tiendanube");
        }

        List<String> elegidos = new ArrayList<>();
        List<String> disponibles = new ArrayList<>(candidatos);
        while (!disponibles.isEmpty() && elegidos.size() < 3) {
            String mejor = null;
            int mayorCantidadCombinaciones = -1;
            for (String candidato : disponibles) {
                List<String> prueba = new ArrayList<>(elegidos);
                prueba.add(candidato);
                int cantidad = cantidadCombinaciones(atributosPorVariante, prueba);
                if (cantidad > mayorCantidadCombinaciones) {
                    mayorCantidadCombinaciones = cantidad;
                    mejor = candidato;
                }
            }
            elegidos.add(mejor);
            disponibles.remove(mejor);
            if (cantidadCombinaciones(atributosPorVariante, elegidos) == variantes.size()) break;
        }
        if (cantidadCombinaciones(atributosPorVariante, elegidos) != variantes.size()) {
            throw new IllegalArgumentException(
                    "Las variantes repiten la misma combinación de valores; revise sus atributos antes de publicarlas en Tiendanube");
        }
        return List.copyOf(elegidos);
    }

    private Map<String, String> atributosNormalizados(ProductoVariante variante) {
        Map<String, String> atributos = new LinkedHashMap<>();
        AtributosVarianteHelper.obtener(
                variante, CanalVenta.TIENDANUBE).forEach((id, valor) -> {
            if (id != null && valor != null && !valor.isBlank()) {
                atributos.put(id.trim().toUpperCase(Locale.ROOT), valor.trim());
            }
        });
        if (!atributos.containsKey("SIZE") && variante.getTalle() != null
                && !variante.getTalle().isBlank()) {
            atributos.put("SIZE", variante.getTalle().trim());
        }
        if (!atributos.containsKey("COLOR") && variante.getColor() != null
                && !variante.getColor().isBlank()) {
            atributos.put("COLOR", variante.getColor().trim());
        }
        if (!atributos.containsKey("SIZE") && atributos.containsKey("FILTRABLE_SIZE")) {
            atributos.put("SIZE", atributos.get("FILTRABLE_SIZE"));
        }
        if (!atributos.containsKey("COLOR") && atributos.containsKey("MAIN_COLOR")) {
            atributos.put("COLOR", atributos.get("MAIN_COLOR"));
        }
        // Son equivalencias o duplicados visuales, no opciones independientes para el comprador.
        atributos.remove("FILTRABLE_SIZE");
        atributos.remove("MAIN_COLOR");
        return atributos;
    }

    private boolean atributoTecnico(String id) {
        return id.startsWith("SELLER_PACKAGE_")
                || id.equals("WITH_VIRTUAL_TRY_ON")
                || id.equals("FILTRABLE_GENDER")
                || id.equals("GTIN")
                || id.equals("SELLER_SKU")
                || id.equals("EMPTY_GTIN_REASON")
                || id.equals("HAZMAT_TRANSPORTABILITY");
    }

    private int prioridadAtributo(String id) {
        int indice = ATRIBUTOS_PRIORITARIOS.indexOf(id);
        return indice < 0 ? ATRIBUTOS_PRIORITARIOS.size() : indice;
    }

    private int cantidadValoresDistintos(List<Map<String, String>> atributos, String id) {
        return (int) atributos.stream().map(mapa -> mapa.get(id)).distinct().count();
    }

    private int cantidadCombinaciones(
            List<Map<String, String>> atributos,
            List<String> ids) {
        Set<String> combinaciones = new HashSet<>();
        for (Map<String, String> variante : atributos) {
            String combinacion = ids.stream()
                    .map(variante::get)
                    .reduce((a, b) -> a + "\u001f" + b)
                    .orElse("");
            combinaciones.add(combinacion);
        }
        return combinaciones.size();
    }

    private String api(TiendanubeCredencialesService.Credenciales c) {
        return "https://api.tiendanube.com/v1/" + c.storeId();
    }

    private void autenticar(
            org.springframework.http.HttpHeaders headers,
            TiendanubeCredencialesService.Credenciales c) {
        headers.setBearerAuth(c.token());
        headers.set("User-Agent", c.userAgent());
    }

    private void validar() {
        if (!configurado()) throw new IllegalStateException("Tiendanube no está configurada");
    }

    private String tituloTiendanube(Producto producto) {
        String titulo = producto.getTiendaNubeTitulo();
        return titulo == null || titulo.isBlank()
                ? producto.getDescripcion() : titulo.trim();
    }
}

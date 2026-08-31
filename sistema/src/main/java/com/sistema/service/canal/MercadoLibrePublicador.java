package com.sistema.service.canal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sistema.model.CanalVenta;
import com.sistema.model.Producto;
import com.sistema.model.ProductoVariante;
import com.sistema.repository.ProductoVarianteRepository;
import com.sistema.service.MercadoLibreTokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.*;

@Component
public class MercadoLibrePublicador implements PublicadorCanal {
    private static final String ITEM_CONDITION_NEW = "2230284";

    private final RestClient restClient;
    private final MercadoLibreTokenService tokenService;
    private final ObjectMapper objectMapper;
    private final ProductoVarianteRepository varianteRepository;
    private final Map<String, Set<String>> atributosSoloLecturaPorCategoria = new java.util.concurrent.ConcurrentHashMap<>();
    @Value("${integraciones.mercadolibre.category-id:}") private String categoryId;
    @Value("${integraciones.mercadolibre.listing-type-id:gold_special}") private String listingTypeId;
    @Value("${integraciones.mercadolibre.user-products:true}") private boolean userProducts;
    @Value("${integraciones.public-base-url:}") private String publicBaseUrl;

    @Autowired
    public MercadoLibrePublicador(MercadoLibreTokenService tokenService, ObjectMapper objectMapper,
                                  ProductoVarianteRepository varianteRepository) {
        this(tokenService, objectMapper, varianteRepository, RestClient.create());
    }

    MercadoLibrePublicador(MercadoLibreTokenService tokenService, ObjectMapper objectMapper,
                           ProductoVarianteRepository varianteRepository, RestClient restClient) {
        this.tokenService = tokenService;
        this.objectMapper = objectMapper;
        this.varianteRepository = varianteRepository;
        this.restClient = restClient;
    }

    public CanalVenta canal() { return CanalVenta.MERCADO_LIBRE; }
    public boolean configurado() { return tokenService.configurado(); }

    public ResultadoPublicacion publicar(Producto producto, String idActual) {
        validar();
        prepararCategoriaPublicable(producto);
        List<ProductoVariante> variantes = varianteRepository.findByProductoIdOrderByNombreAsc(producto.getId());
        validarFormatoGtins(producto, variantes);
        validarAtributosObligatorios(producto, variantes);
        long sellerId = obtenerSellerIdActual();
        prepararGuiaTallesParaCuentaConectada(producto, variantes);
        if (debePublicarVariantesComoUserProducts(variantes, idActual, producto)) {
            validarFotosDeVariantesVisuales(producto, variantes);
            return publicarVariantesComoUserProducts(producto, variantes, sellerId);
        }
        String id = resolverId(producto, idActual);
        if (id != null && requiereNuevaPublicacion(id, sellerId)) id = null;
        JsonNode response = publicarItemConFallbackGtin(construirPayload(producto, id == null), id);
        String idPublicado = response == null ? id : response.path("id").asText(id);
        if (idPublicado == null || idPublicado.isBlank()) {
            throw new IllegalStateException("Mercado Libre no devolvió el identificador de la publicación");
        }
        publicarDescripcionConRenovacion(idPublicado, producto.getMercadoLibreDescripcion(), id == null);
        sincronizarEstadoConRenovacion(idPublicado, producto.getMercadoLibreEstado());
        return new ResultadoPublicacion(idPublicado);
    }

    private void detectarGuiaTallesSiCorresponde(Producto producto, List<ProductoVariante> variantes) {
        if (variantes.isEmpty() || tieneTexto(producto.getMercadoLibreGuiaTallesId())
                || variantes.stream().noneMatch(v -> tieneTexto(v.getTalle()))) return;
        JsonNode atributosCategoria = getMercadoLibreConRenovacion(
                "/categories/" + producto.getMercadoLibreCategoriaId() + "/attributes");
        boolean usaGuiaTalles = atributosCategoria.isArray()
                && java.util.stream.StreamSupport.stream(atributosCategoria.spliterator(), false)
                .anyMatch(a -> "SIZE_GRID_ID".equals(a.path("id").asText()));
        if (!usaGuiaTalles) return;
        if (!tieneTexto(producto.getMercadoLibreGenero())) {
            throw new IllegalArgumentException("Seleccione el género del producto para buscar la guía de talles de Mercado Libre");
        }
        if (!tieneTexto(producto.getMercadoLibreMarca())) {
            throw new IllegalArgumentException("Ingrese la marca del producto para buscar la guía de talles de Mercado Libre");
        }
        JsonNode categoria = consultarCategoriaConRenovacion(producto.getMercadoLibreCategoriaId());
        String domainId = categoria.path("settings").path("catalog_domain")
                .asText(categoria.path("catalog_domain").asText(""));
        if (!tieneTexto(domainId)) return;
        String dominioGuia = domainId.replaceFirst("^MLA-", "");
        JsonNode usuario = getMercadoLibreConRenovacion("/users/me");
        long sellerId = usuario.path("id").asLong(0);
        if (sellerId <= 0) throw new IllegalStateException("No se pudo identificar la cuenta de Mercado Libre");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("domain_id", dominioGuia);
        body.put("site_id", "MLA");
        body.put("seller_id", sellerId);
        body.put("attributes", List.of(
                Map.of("id", "GENDER", "values", List.of(Map.of("name", producto.getMercadoLibreGenero()))),
                Map.of("id", "BRAND", "values", List.of(Map.of("name", producto.getMercadoLibreMarca())))));
        JsonNode respuesta = buscarGuiasConRenovacion(body);
        for (JsonNode resumen : respuesta.path("charts")) {
            String id = resumen.path("id").asText("");
            if (!tieneTexto(id)) continue;
            JsonNode guia = resumen.path("rows").isArray() && !resumen.path("rows").isEmpty()
                    ? resumen : consultarGuiaConRenovacion(id);
            boolean contieneTodos = variantes.stream().filter(v -> tieneTexto(v.getTalle()))
                    .allMatch(v -> buscarFilaGuia(guia, v.getTalle()) != null);
            if (contieneTodos) {
                producto.setMercadoLibreGuiaTallesId(id);
                return;
            }
        }
        throw new IllegalArgumentException("No se encontró en la cuenta una guía de " + producto.getMercadoLibreMarca()
                + " para " + producto.getMercadoLibreGenero() + " que contenga todos los talles del producto");
    }

    private void prepararGuiaTallesParaCuentaConectada(
            Producto producto,
            List<ProductoVariante> variantes) {
        if (tieneTexto(producto.getMercadoLibreGuiaTallesId())) {
            try {
                consultarGuiaConRenovacion(producto.getMercadoLibreGuiaTallesId().trim());
            } catch (RestClientResponseException e) {
                if (e.getStatusCode() != HttpStatus.FORBIDDEN) throw e;
                // Las guías específicas pertenecen al vendedor que las creó. Si el producto
                // fue importado desde otra cuenta, no se puede reutilizar ese identificador.
                producto.setMercadoLibreGuiaTallesId(null);
                producto.setMercadoLibreGuiaTallesFilaId(null);
            }
        }
        detectarGuiaTallesSiCorresponde(producto, variantes);
    }

    private long obtenerSellerIdActual() {
        Long sellerId = tokenService.obtenerUsuarioExternoId();
        if (sellerId == null || sellerId <= 0) {
            throw new IllegalStateException("No se pudo identificar la cuenta conectada de Mercado Libre");
        }
        return sellerId;
    }

    private JsonNode buscarGuiasConRenovacion(Map<String, Object> body) {
        try {
            return buscarGuias(body, tokenService.obtenerAccessToken());
        } catch (RestClientResponseException e) {
            if (e.getStatusCode() != HttpStatus.UNAUTHORIZED) throw e;
            tokenService.invalidarAccessToken();
            return buscarGuias(body, tokenService.obtenerAccessToken());
        }
    }

    private JsonNode buscarGuias(Map<String, Object> body, String token) {
        JsonNode respuesta = restClient.post().uri("https://api.mercadolibre.com/catalog/charts/search?limit=100")
                .headers(h -> h.setBearerAuth(token)).contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().body(JsonNode.class);
        return respuesta == null ? objectMapper.createObjectNode() : respuesta;
    }

    private JsonNode consultarCategoriaConRenovacion(String categoria) {
        try {
            return getMercadoLibre("/categories/" + categoria, tokenService.obtenerAccessToken());
        } catch (RestClientResponseException e) {
            if (e.getStatusCode() != HttpStatus.UNAUTHORIZED) throw e;
            tokenService.invalidarAccessToken();
            return getMercadoLibre("/categories/" + categoria, tokenService.obtenerAccessToken());
        }
    }

    private JsonNode getMercadoLibreConRenovacion(String endpoint) {
        try {
            return getMercadoLibre(endpoint, tokenService.obtenerAccessToken());
        } catch (RestClientResponseException e) {
            if (e.getStatusCode() != HttpStatus.UNAUTHORIZED) throw e;
            tokenService.invalidarAccessToken();
            return getMercadoLibre(endpoint, tokenService.obtenerAccessToken());
        }
    }

    private JsonNode getMercadoLibre(String endpoint, String token) {
        JsonNode respuesta = restClient.get().uri("https://api.mercadolibre.com" + endpoint)
                .headers(h -> h.setBearerAuth(token)).retrieve().body(JsonNode.class);
        return respuesta == null ? objectMapper.createObjectNode() : respuesta;
    }

    private void prepararCategoriaPublicable(Producto producto) {
        String categoria = texto(producto.getMercadoLibreCategoriaId(), categoryId);
        if (!categoria.isBlank() && categoriaPermitePublicarConRenovacion(categoria)) return;
        String consultaCategoria = texto(producto.getDescripcion(), "") + " "
                + texto(producto.getCategoriaOrigen(), "");
        String predicha = predecirCategoriaConRenovacion(consultaCategoria.trim());
        if (!tieneTexto(predicha)) {
            throw new IllegalArgumentException("No se pudo determinar una categoría final de Mercado Libre para \""
                    + producto.getDescripcion() + "\". Edite el producto y seleccione manualmente la categoría "
                    + "de Mercado Libre que corresponda.");
        }
        producto.setMercadoLibreCategoriaId(predicha);
    }

    private void validarAtributosObligatorios(Producto producto, List<ProductoVariante> variantes) {
        JsonNode definiciones = getMercadoLibreConRenovacion(
                "/categories/" + producto.getMercadoLibreCategoriaId() + "/attributes");
        if (!definiciones.isArray()) return;
        autocompletarAtributosObligatorios(producto, variantes, definiciones);
        Set<String> soloLectura = new LinkedHashSet<>();
        for (JsonNode definicion : definiciones) {
            if (definicion.path("tags").path("read_only").asBoolean(false)) {
                soloLectura.add(definicion.path("id").asText(""));
            }
        }
        atributosSoloLecturaPorCategoria.put(producto.getMercadoLibreCategoriaId(), Set.copyOf(soloLectura));
        Set<String> presentesProducto = construirAtributos(producto).stream()
                .map(a -> Objects.toString(a.get("id"), ""))
                .filter(id -> !id.isBlank()).collect(java.util.stream.Collectors.toSet());
        List<String> faltantes = new ArrayList<>();
        for (JsonNode definicion : definiciones) {
            if (!definicion.path("tags").path("required").asBoolean(false)) continue;
            String id = definicion.path("id").asText("");
            boolean presenteEnVariantes = !variantes.isEmpty() && variantes.stream()
                    .allMatch(v -> AtributosVarianteHelper.obtener(v).containsKey(id));
            if (!presentesProducto.contains(id) && !presenteEnVariantes) {
                faltantes.add(definicion.path("name").asText(id));
            }
        }
        if (!faltantes.isEmpty()) {
            JsonNode categoria = consultarCategoriaConRenovacion(producto.getMercadoLibreCategoriaId());
            String nombreCategoria = categoria.path("name").asText(producto.getMercadoLibreCategoriaId());
            throw new IllegalArgumentException("Para publicar \"" + producto.getDescripcion()
                    + "\" en la categoría " + nombreCategoria + " ("
                    + producto.getMercadoLibreCategoriaId() + "), complete en Productos > Editar los campos "
                    + "obligatorios de Mercado Libre: " + String.join(", ", faltantes)
                    + ". Si esos campos no corresponden al producto, cambie la categoría seleccionada.");
        }
    }

    void autocompletarAtributosObligatorios(Producto producto,
                                             List<ProductoVariante> variantes,
                                             JsonNode definiciones) {
        Map<String, Map<String, Object>> adicionales = leerAtributosAdicionales(producto);
        Set<String> presentesProducto = construirAtributos(producto).stream()
                .map(a -> Objects.toString(a.get("id"), ""))
                .filter(id -> !id.isBlank())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        boolean cambio = false;
        for (JsonNode definicion : definiciones) {
            JsonNode tags = definicion.path("tags");
            if (!esObligatorio(definicion) || tags.path("read_only").asBoolean(false)) continue;
            String id = definicion.path("id").asText("");
            if (id.isBlank() || presentesProducto.contains(id)) continue;
            boolean presenteEnVariantes = !variantes.isEmpty() && variantes.stream()
                    .allMatch(v -> AtributosVarianteHelper.obtener(v).containsKey(id));
            if (presenteEnVariantes) continue;

            Map<String, Object> inferido = inferirAtributo(producto, definicion);
            boolean atributoVariacion = tags.path("allow_variations").asBoolean(false)
                    || tags.path("variation_attribute").asBoolean(false)
                    || "CHILD_PK".equals(definicion.path("hierarchy").asText(""));
            if (atributoVariacion && !variantes.isEmpty()) {
                if (inferido != null && !"-1".equals(inferido.get("value_id"))) {
                    String valor = Objects.toString(inferido.get("value_name"), "");
                    if (valor.isBlank()) valor = nombreValor(definicion, Objects.toString(inferido.get("value_id"), ""));
                    if (!valor.isBlank()) {
                        completarAtributoEnVariantes(variantes, id, valor);
                    }
                }
                continue;
            }
            if (inferido == null && "EMPTY_GTIN_REASON".equals(id)) {
                inferido = primerValorPermitido(id, definicion);
            }
            if (inferido == null && !Set.of("GTIN", "SIZE_GRID_ID", "SIZE_GRID_ROW_ID")
                    .contains(id)) {
                inferido = atributoNoAplica(id);
            }
            if (inferido == null) continue;
            adicionales.put(id, inferido);
            presentesProducto.add(id);
            cambio = true;
        }
        if (cambio) guardarAtributosAdicionales(producto, adicionales);
    }

    private boolean esObligatorio(JsonNode definicion) {
        JsonNode tags = definicion.path("tags");
        return tags.path("required").asBoolean(false)
                || tags.path("new_required").asBoolean(false);
    }

    private Map<String, Object> inferirAtributo(Producto producto, JsonNode definicion) {
        String id = definicion.path("id").asText("");
        String textoProducto = texto(producto.getDescripcion(), "") + " "
                + texto(producto.getCategoriaOrigen(), "");
        String normalizado = " " + normalizarTextoBusqueda(textoProducto) + " ";
        JsonNode mejor = null;
        int longitudMejor = 0;
        for (JsonNode valor : definicion.path("values")) {
            String nombre = valor.path("name").asText("");
            String candidato = normalizarTextoBusqueda(nombre);
            if (candidato.length() < 2 || Set.of("si", "no", "yes", "true", "false").contains(candidato)) continue;
            if (normalizado.contains(" " + candidato + " ") && candidato.length() > longitudMejor) {
                mejor = valor;
                longitudMejor = candidato.length();
            }
        }
        if (mejor != null) return atributoConValor(id, mejor);

        String concepto = normalizarTextoBusqueda(definicion.path("name").asText(id))
                .replaceFirst("^(con|tiene|incluye) ", "");
        if ("boolean".equals(definicion.path("value_type").asText())
                && !concepto.isBlank() && normalizado.contains(" " + concepto + " ")) {
            for (JsonNode valor : definicion.path("values")) {
                String nombre = normalizarTextoBusqueda(valor.path("name").asText(""));
                if (Set.of("si", "yes", "true").contains(nombre)) return atributoConValor(id, valor);
            }
        }
        if (id.contains("YEAR")) {
            java.util.regex.Matcher anio = java.util.regex.Pattern.compile("(?<!\\d)(19|20)\\d{2}(?!\\d)")
                    .matcher(textoProducto);
            if (anio.find()) return atributoConNombre(id, anio.group());
        }
        return null;
    }

    private Map<String, Object> atributoConValor(String id, JsonNode valor) {
        Map<String, Object> atributo = new LinkedHashMap<>();
        atributo.put("id", id);
        String valueId = valor.path("id").asText("");
        String valueName = valor.path("name").asText("");
        if (!valueId.isBlank()) atributo.put("value_id", valueId);
        if (!valueName.isBlank()) atributo.put("value_name", valueName);
        return atributo;
    }

    private Map<String, Object> atributoConNombre(String id, String nombre) {
        Map<String, Object> atributo = new LinkedHashMap<>();
        atributo.put("id", id);
        atributo.put("value_name", nombre);
        return atributo;
    }

    private Map<String, Object> primerValorPermitido(String id, JsonNode definicion) {
        JsonNode primero = null;
        for (JsonNode valor : definicion.path("values")) {
            if (primero == null) primero = valor;
            String nombre = normalizarTextoBusqueda(valor.path("name").asText(""));
            if (nombre.contains("no tiene") || nombre.contains("no registrado")
                    || nombre.contains("no posee")) {
                return atributoConValor(id, valor);
            }
        }
        return primero == null ? null : atributoConValor(id, primero);
    }

    private Map<String, Object> atributoNoAplica(String id) {
        Map<String, Object> atributo = new LinkedHashMap<>();
        atributo.put("id", id);
        atributo.put("value_id", "-1");
        atributo.put("value_name", null);
        return atributo;
    }

    private String nombreValor(JsonNode definicion, String valueId) {
        for (JsonNode valor : definicion.path("values")) {
            if (valueId.equals(valor.path("id").asText(""))) return valor.path("name").asText("");
        }
        return "";
    }

    private void completarAtributoEnVariantes(List<ProductoVariante> variantes, String id, String valor) {
        for (ProductoVariante variante : variantes) {
            Map<String, String> atributos = new LinkedHashMap<>(AtributosVarianteHelper.obtener(variante));
            if (atributos.containsKey(id)) continue;
            atributos.put(id, valor);
            try {
                variante.setMercadoLibreAtributosJson(objectMapper.writeValueAsString(atributos));
                if ("COLOR".equals(id) && !tieneTexto(variante.getColor())) variante.setColor(valor);
                if ("SIZE".equals(id) && !tieneTexto(variante.getTalle())) variante.setTalle(valor);
                varianteRepository.save(variante);
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("No se pudo completar el atributo " + id
                        + " en la variante " + variante.getNombreMostrar(), e);
            }
        }
    }

    private Map<String, Map<String, Object>> leerAtributosAdicionales(Producto producto) {
        Map<String, Map<String, Object>> resultado = new LinkedHashMap<>();
        String json = producto.getMercadoLibreAtributosJson();
        if (!tieneTexto(json)) return resultado;
        try {
            List<Map<String, Object>> atributos = objectMapper.readValue(json, new TypeReference<>() {});
            for (Map<String, Object> atributo : atributos) {
                String id = Objects.toString(atributo.get("id"), "");
                if (!id.isBlank()) resultado.put(id, new LinkedHashMap<>(atributo));
            }
            return resultado;
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("El JSON de atributos de Mercado Libre no es válido", e);
        }
    }

    private void guardarAtributosAdicionales(Producto producto,
                                               Map<String, Map<String, Object>> atributos) {
        try {
            producto.setMercadoLibreAtributosJson(objectMapper.writeValueAsString(atributos.values()));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("No se pudieron preparar los atributos de Mercado Libre", e);
        }
    }

    private String normalizarTextoBusqueda(String valor) {
        if (valor == null) return "";
        return java.text.Normalizer.normalize(valor, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^A-Za-z0-9]+", " ")
                .trim().toLowerCase(Locale.ROOT);
    }

    private void validarFormatoGtins(Producto producto, List<ProductoVariante> variantes) {
        if (tieneTexto(producto.getMercadoLibreGtin()) && !gtinValido(producto.getMercadoLibreGtin())) {
            throw new IllegalArgumentException("El GTIN " + producto.getMercadoLibreGtin()
                    + " no es válido. Ingrese el código real o borre el campo y seleccione un Motivo de GTIN vacío.");
        }
        for (ProductoVariante variante : variantes) {
            if (tieneTexto(variante.getMercadoLibreGtin()) && !gtinValido(variante.getMercadoLibreGtin())) {
                throw new IllegalArgumentException("El GTIN de la variante " + variante.getNombreMostrar()
                        + " no es válido. Ingrese el código real o use un Motivo de GTIN vacío.");
            }
        }
    }

    boolean gtinValido(String valor) {
        if (valor == null) return false;
        String gtin = valor.trim();
        if (!gtin.matches("\\d+") || !Set.of(8, 12, 13, 14).contains(gtin.length())) return false;
        int suma = 0;
        int peso = 3;
        for (int i = gtin.length() - 2; i >= 0; i--) {
            suma += Character.digit(gtin.charAt(i), 10) * peso;
            peso = peso == 3 ? 1 : 3;
        }
        int verificador = (10 - (suma % 10)) % 10;
        return verificador == Character.digit(gtin.charAt(gtin.length() - 1), 10);
    }

    private boolean categoriaPermitePublicarConRenovacion(String categoria) {
        try {
            return categoriaPermitePublicar(categoria, tokenService.obtenerAccessToken());
        } catch (RestClientResponseException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) return false;
            if (e.getStatusCode() != HttpStatus.UNAUTHORIZED) throw e;
            tokenService.invalidarAccessToken();
            return categoriaPermitePublicar(categoria, tokenService.obtenerAccessToken());
        }
    }

    private boolean categoriaPermitePublicar(String categoria, String token) {
        JsonNode detalle = restClient.get()
                .uri("https://api.mercadolibre.com/categories/" + categoria)
                .headers(h -> h.setBearerAuth(token))
                .retrieve().body(JsonNode.class);
        return detalle != null && detalle.path("settings").path("listing_allowed").asBoolean(false);
    }

    private String predecirCategoriaConRenovacion(String descripcion) {
        try {
            return predecirCategoria(descripcion, tokenService.obtenerAccessToken());
        } catch (RestClientResponseException e) {
            if (e.getStatusCode() != HttpStatus.UNAUTHORIZED) throw e;
            tokenService.invalidarAccessToken();
            return predecirCategoria(descripcion, tokenService.obtenerAccessToken());
        }
    }

    private String predecirCategoria(String descripcion, String token) {
        JsonNode respuesta = restClient.get()
                .uri(builder -> builder.scheme("https").host("api.mercadolibre.com")
                        .path("/sites/MLA/domain_discovery/search")
                        .queryParam("limit", 1)
                        .queryParam("q", descripcion)
                        .build())
                .headers(h -> h.setBearerAuth(token))
                .retrieve().body(JsonNode.class);
        return respuesta != null && respuesta.isArray() && !respuesta.isEmpty()
                ? respuesta.get(0).path("category_id").asText("") : "";
    }

    private boolean debePublicarVariantesComoUserProducts(List<ProductoVariante> variantes,
                                                           String idActual, Producto producto) {
        if (!userProducts || variantes.isEmpty()) return false;
        boolean yaTieneItemsPorVariante = variantes.stream().anyMatch(v -> tieneTexto(v.getMercadoLibreItemId()));
        boolean esPublicacionLegacy = variantes.stream().anyMatch(v -> tieneTexto(v.getMercadoLibreVariationId()));
        boolean noTienePublicacion = resolverId(producto, idActual) == null;
        return yaTieneItemsPorVariante || (noTienePublicacion && !esPublicacionLegacy);
    }

    void validarFotosDeVariantesVisuales(Producto producto, List<ProductoVariante> variantes) {
        Map<String, List<ProductoVariante>> porFoto = new LinkedHashMap<>();
        for (ProductoVariante variante : variantes) {
            String firmaVisual = firmaVisual(variante);
            if (firmaVisual.isBlank()) continue;
            String firmaFoto = firmaFoto(variante, producto);
            porFoto.computeIfAbsent(firmaFoto, ignorada -> new ArrayList<>()).add(variante);
        }
        for (List<ProductoVariante> grupo : porFoto.values()) {
            Set<String> aspectos = new LinkedHashSet<>();
            grupo.forEach(v -> aspectos.add(firmaVisual(v)));
            if (aspectos.size() > 1) {
                String nombres = grupo.stream().map(ProductoVariante::getNombreMostrar)
                        .filter(Objects::nonNull).limit(4).reduce((a, b) -> a + ", " + b).orElse("variantes");
                throw new IllegalArgumentException("Las variantes " + nombres
                        + " cambian de color, diseño o piedra, pero usan la misma foto. "
                        + "Cargue una imagen que corresponda a cada variante antes de publicar en Mercado Libre.");
            }
        }
    }

    private String firmaVisual(ProductoVariante variante) {
        return AtributosVarianteHelper.obtener(variante).entrySet().stream()
                .filter(e -> esAtributoVisual(e.getKey()))
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey().toUpperCase(Locale.ROOT) + "=" + normalizarTexto(e.getValue()))
                .reduce((a, b) -> a + "|" + b).orElse("");
    }

    private boolean esAtributoVisual(String id) {
        String valor = id == null ? "" : id.toUpperCase(Locale.ROOT);
        return valor.contains("COLOR") || valor.contains("DESIGN") || valor.contains("PATTERN")
                || valor.contains("GEMSTONE") || valor.contains("STONE_TYPE");
    }

    private String firmaFoto(ProductoVariante variante, Producto producto) {
        if (variante.tieneFotoLocal()) return "LOCAL:" + Arrays.hashCode(variante.getFotoContenido());
        if (tieneTexto(variante.getFotoUrlExterna())) return "URL:" + variante.getFotoUrlExterna().trim();
        if (producto.tieneFotoLocal()) return "GENERAL_LOCAL:" + Arrays.hashCode(producto.getFotoContenido());
        String externas = texto(producto.getFotoUrlExterna(), "") + "\n"
                + texto(producto.getFotosUrlsExternas(), "");
        return "GENERAL_URL:" + externas.trim();
    }

    private String normalizarTexto(String valor) {
        return valor == null ? "" : valor.trim().toUpperCase(Locale.ROOT);
    }

    private ResultadoPublicacion publicarVariantesComoUserProducts(Producto producto,
                                                                    List<ProductoVariante> variantes,
                                                                    long sellerId) {
        String primerId = null;
        Map<Long, FilaGuiaAsignada> filasGuia = resolverFilasGuia(producto, variantes);
        for (ProductoVariante variante : variantes) {
            String id = tieneTexto(variante.getMercadoLibreItemId())
                    ? variante.getMercadoLibreItemId().trim() : null;
            if (id != null && requiereNuevaPublicacion(id, sellerId)) id = null;
            boolean nueva = id == null;
            Map<String, Object> payload = construirPayloadVarianteUserProduct(producto, variante, nueva,
                    filasGuia.get(variante.getId()));
            JsonNode response = publicarItemConFallbackGtin(payload, id);
            String idPublicado = response == null ? id : response.path("id").asText(id);
            if (!tieneTexto(idPublicado)) {
                throw new IllegalStateException("Mercado Libre no devolvió el identificador de la variante "
                        + variante.getNombreMostrar());
            }
            variante.setMercadoLibreItemId(idPublicado);
            variante.setMercadoLibreVariationId(null);
            varianteRepository.save(variante);
            publicarDescripcionConRenovacion(idPublicado, producto.getMercadoLibreDescripcion(), nueva);
            sincronizarEstadoConRenovacion(idPublicado, producto.getMercadoLibreEstado());
            if (primerId == null) primerId = idPublicado;
        }
        return new ResultadoPublicacion(primerId);
    }

    boolean requiereNuevaPublicacion(String itemId, long sellerId) {
        try {
            JsonNode item = getMercadoLibreConRenovacion("/items/" + itemId);
            long propietario = item.path("seller_id").asLong(0);
            if (propietario <= 0 || propietario != sellerId) return true;
            validarItemNoEsteEnRevision(itemId, item);
            return "closed".equalsIgnoreCase(item.path("status").asText());
        } catch (RestClientResponseException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND
                    || e.getStatusCode() == HttpStatus.FORBIDDEN) return true;
            throw e;
        }
    }

    public void sincronizarStock(Producto producto, String idActual) {
        validar();
        String id = resolverId(producto, idActual);
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("El producto no tiene una publicación vinculada de Mercado Libre");
        }
        List<ProductoVariante> variantes = varianteRepository.findByProductoIdOrderByNombreAsc(producto.getId());
        if (variantes.isEmpty()) {
            actualizarStockConRenovacion(id, Map.of("available_quantity",
                    Optional.ofNullable(producto.getCantidad()).orElse(0)));
            return;
        }

        List<Map<String, Object>> variaciones = variantes.stream()
                .filter(v -> v.getMercadoLibreVariationId() != null && !v.getMercadoLibreVariationId().isBlank())
                .map(v -> Map.<String, Object>of(
                        "id", v.getMercadoLibreVariationId(),
                        "available_quantity", Optional.ofNullable(v.getStock()).orElse(0)))
                .toList();
        if (!variaciones.isEmpty()) actualizarStockConRenovacion(id, Map.of("variations", variaciones));

        variantes.stream()
                .filter(v -> v.getMercadoLibreItemId() != null && !v.getMercadoLibreItemId().isBlank())
                .forEach(v -> actualizarStockConRenovacion(v.getMercadoLibreItemId(), Map.of(
                        "available_quantity", Optional.ofNullable(v.getStock()).orElse(0))));

        if (variaciones.isEmpty() && variantes.stream().noneMatch(v ->
                v.getMercadoLibreItemId() != null && !v.getMercadoLibreItemId().isBlank())) {
            throw new IllegalStateException("Las variantes no tienen identificadores de Mercado Libre para actualizar el stock");
        }
    }

    private void actualizarStockConRenovacion(String id, Map<String, Object> body) {
        validarItemNoEsteEnRevision(id, getMercadoLibreConRenovacion("/items/" + id));
        try {
            actualizarStock(id, body, tokenService.obtenerAccessToken());
        } catch (RestClientResponseException e) {
            if (e.getStatusCode() != HttpStatus.UNAUTHORIZED) throw traducirItemNoModificable(e, id);
            tokenService.invalidarAccessToken();
            try {
                actualizarStock(id, body, tokenService.obtenerAccessToken());
            } catch (RestClientResponseException reintento) {
                throw traducirItemNoModificable(reintento, id);
            }
        }
    }

    private void actualizarStock(String id, Map<String, Object> body, String token) {
        restClient.put().uri("https://api.mercadolibre.com/items/" + id)
                .headers(h -> h.setBearerAuth(token)).contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().toBodilessEntity();
    }

    private JsonNode publicarItemConRenovacion(Map<String, Object> payload, String id) {
        try {
            return publicarItem(payload, id, tokenService.obtenerAccessToken());
        } catch (RestClientResponseException e) {
            if (e.getStatusCode() != HttpStatus.UNAUTHORIZED) throw e;
            tokenService.invalidarAccessToken();
            return publicarItem(payload, id, tokenService.obtenerAccessToken());
        }
    }

    private JsonNode publicarItemConFallbackGtin(Map<String, Object> payload, String id) {
        try {
            return publicarItemConRenovacion(payload, id);
        } catch (RestClientResponseException e) {
            RuntimeException restriccion = traducirItemNoModificable(e, id);
            if (restriccion != e) throw restriccion;
            if (tieneTexto(id) && payload.containsKey("pictures") && esErrorFotosNoModificables(e)) {
                return publicarItemConFallbackGtin(prepararReintentoSinFotos(payload), id);
            }
            if (!esErrorGtinRequerido(e)) throw e;
            if (!tieneTexto(id)) {
                boolean envioMotivo = objectMapper.valueToTree(payload).toString()
                        .contains("\"id\":\"EMPTY_GTIN_REASON\"");
                if (envioMotivo) {
                    throw new IllegalArgumentException("Mercado Libre no acepta el Motivo de GTIN vacío para esta publicación. "
                            + "Ingrese el GTIN real del producto.");
                }
                throw new IllegalArgumentException("Mercado Libre exige identificar este producto. Edítelo y complete el GTIN real o, "
                        + "si no lo tiene, seleccione el Motivo de GTIN vacío ubicado junto al campo GTIN.");
            }
            return publicarItemConRenovacion(prepararReintentoSinAtributos(payload), id);
        }
    }

    Map<String, Object> prepararReintentoSinFotos(Map<String, Object> payload) {
        Map<String, Object> reintento = new LinkedHashMap<>(payload);
        reintento.remove("pictures");
        return reintento;
    }

    private boolean esErrorFotosNoModificables(RestClientResponseException e) {
        String respuesta = e.getResponseBodyAsString();
        return respuesta != null
                && respuesta.contains("pictures")
                && (respuesta.contains("field_not_updatable")
                || respuesta.contains("not_modifiable"));
    }

    Map<String, Object> prepararReintentoSinAtributos(Map<String, Object> payload) {
        Map<String, Object> reintento = new LinkedHashMap<>(payload);
        reintento.remove("attributes");
        reintento.remove("variations");
        return reintento;
    }

    private boolean esErrorGtinRequerido(RestClientResponseException e) {
        String respuesta = e.getResponseBodyAsString();
        return respuesta != null && respuesta.contains("item.attribute.missing_conditional_required")
                && respuesta.contains("GTIN");
    }

    private void validarItemNoEsteEnRevision(String itemId, JsonNode item) {
        if (!"under_review".equalsIgnoreCase(item.path("status").asText(""))) return;
        String subestado = item.path("sub_status").isArray()
                ? java.util.stream.StreamSupport.stream(item.path("sub_status").spliterator(), false)
                .map(JsonNode::asText).filter(this::tieneTexto)
                .collect(java.util.stream.Collectors.joining(", "))
                : item.path("sub_status").asText("");
        throw errorItemEnRevision(itemId, subestado);
    }

    private RuntimeException traducirItemNoModificable(RestClientResponseException error, String itemId) {
        String respuesta = error.getResponseBodyAsString();
        if (respuesta == null) return error;
        boolean enRevision = respuesta.contains("under_review");
        boolean camposBloqueados = respuesta.contains("item.price.not_modifiable")
                || respuesta.contains("available_quantity is not modifiable")
                || respuesta.contains("item.attributes.not_modifiable");
        return enRevision || camposBloqueados ? errorItemEnRevision(itemId, "") : error;
    }

    private IllegalStateException errorItemEnRevision(String itemId, String subestado) {
        String detalle = tieneTexto(subestado) ? " (" + subestado + ")" : "";
        return new IllegalStateException("La publicación " + texto(itemId, "de Mercado Libre")
                + " está en revisión de Mercado Libre" + detalle
                + ". Mientras figure como under_review no se pueden sincronizar precio, stock ni atributos. "
                + "Revise la moderación en Mercado Libre y vuelva a sincronizar cuando la publicación quede activa "
                + "o pausada. El sistema no creó una publicación duplicada.");
    }

    private JsonNode publicarItem(Map<String, Object> body, String id, String token) {
        RestClient.RequestBodySpec request = (id == null
                ? restClient.post().uri("https://api.mercadolibre.com/items")
                : restClient.put().uri("https://api.mercadolibre.com/items/" + id))
                .headers(h -> h.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON);
        return request.body(body).retrieve().body(JsonNode.class);
    }

    Map<String, Object> construirPayloadVarianteUserProduct(Producto producto,
                                                             ProductoVariante variante,
                                                             boolean nuevaPublicacion) {
        return construirPayloadVarianteUserProduct(producto, variante, nuevaPublicacion, null);
    }

    private Map<String, Object> construirPayloadVarianteUserProduct(Producto producto,
                                                                     ProductoVariante variante,
                                                                     boolean nuevaPublicacion,
                                                                     FilaGuiaAsignada filaGuia) {
        Map<String, Object> body = construirPayload(producto, nuevaPublicacion);
        body.remove("title");
        body.remove("variations");
        if (nuevaPublicacion) body.put("family_name", producto.getDescripcion());
        else body.remove("family_name");
        body.put("price", Optional.ofNullable(variante.getPrecioContado())
                .orElse(producto.getPrecioContado()));
        body.put("available_quantity", Optional.ofNullable(variante.getStock()).orElse(0));
        body.put("attributes", construirAtributosUserProduct(producto, variante, filaGuia));
        List<Map<String, String>> fotos = construirFotos(variante, producto);
        if (nuevaPublicacion && fotos.isEmpty()) {
            if (variante.tieneFoto() && publicBaseUrl.isBlank()) {
                throw new IllegalArgumentException("La variante " + variante.getNombreMostrar()
                        + " tiene foto, pero falta configurar PUBLIC_BASE_URL para que Mercado Libre pueda verla");
            }
            throw new IllegalArgumentException("Agregue una foto a la variante " + variante.getNombreMostrar());
        }
        if (!fotos.isEmpty()) body.put("pictures", fotos);
        return body;
    }

    private List<Map<String, Object>> construirAtributosUserProduct(Producto producto,
                                                                     ProductoVariante variante,
                                                                     FilaGuiaAsignada filaGuia) {
        Map<String, Map<String, Object>> atributos = new LinkedHashMap<>();
        for (Map<String, Object> atributo : construirAtributos(producto)) {
            Object id = atributo.get("id");
            if (id != null) atributos.put(id.toString(), new LinkedHashMap<>(atributo));
        }
        agregarAtributo(atributos, "SELLER_SKU", null, variante.getSku());
        if (tieneTexto(variante.getMercadoLibreGtin())) {
            agregarAtributo(atributos, "GTIN", null, variante.getMercadoLibreGtin());
        }
        AtributosVarianteHelper.obtener(variante).forEach((id, valor) ->
        {
            if (!esSoloLectura(producto, id) && !esAtributoGuiaImportado(id)) {
                agregarAtributo(atributos, id, null, valor);
            }
        });
        if (tieneTexto(producto.getMercadoLibreGuiaTallesId())) {
            if (filaGuia != null && tieneTexto(filaGuia.tallePrincipal())) {
                agregarAtributo(atributos, "SIZE", null, filaGuia.tallePrincipal());
            }
            agregarAtributo(atributos, "SIZE_GRID_ID", null, producto.getMercadoLibreGuiaTallesId());
            agregarAtributo(atributos, "SIZE_GRID_ROW_ID", null, filaGuia == null ? null : filaGuia.id());
        }
        return new ArrayList<>(atributos.values());
    }

    private Map<Long, FilaGuiaAsignada> resolverFilasGuia(Producto producto, List<ProductoVariante> variantes) {
        if (!tieneTexto(producto.getMercadoLibreGuiaTallesId())) return Map.of();
        String guiaId = producto.getMercadoLibreGuiaTallesId().trim();
        JsonNode guia = consultarGuiaConRenovacion(guiaId);
        Map<Long, FilaGuiaAsignada> resultado = new HashMap<>();
        for (ProductoVariante variante : variantes) {
            if (!tieneTexto(variante.getTalle())) {
                throw new IllegalArgumentException("La variante " + variante.getNombreMostrar()
                        + " necesita un talle para asociarla a la guía de Mercado Libre");
            }
            FilaGuiaAsignada fila = buscarFilaGuia(guia, variante.getTalle());
            if (fila == null || !tieneTexto(fila.id())) {
                throw new IllegalArgumentException("El talle " + variante.getTalle()
                        + " no existe en la guía de Mercado Libre " + guiaId);
            }
            String rowId = fila.id().contains(":") ? fila.id() : guiaId + ":" + fila.id();
            resultado.put(variante.getId(), new FilaGuiaAsignada(rowId, fila.tallePrincipal()));
        }
        return resultado;
    }

    private JsonNode consultarGuiaConRenovacion(String guiaId) {
        try {
            return consultarGuia(guiaId, tokenService.obtenerAccessToken());
        } catch (RestClientResponseException e) {
            if (e.getStatusCode() != HttpStatus.UNAUTHORIZED) throw e;
            tokenService.invalidarAccessToken();
            return consultarGuia(guiaId, tokenService.obtenerAccessToken());
        }
    }

    private JsonNode consultarGuia(String guiaId, String token) {
        JsonNode guia = restClient.get().uri("https://api.mercadolibre.com/catalog/charts/" + guiaId)
                .headers(h -> h.setBearerAuth(token)).retrieve().body(JsonNode.class);
        if (guia == null || !guia.path("rows").isArray()) {
            throw new IllegalArgumentException("Mercado Libre no devolvió las filas de la guía " + guiaId);
        }
        return guia;
    }

    private FilaGuiaAsignada buscarFilaGuia(JsonNode guia, String talle) {
        String buscado = normalizarTalle(talle);
        String atributoPrincipal = guia.path("main_attribute_id").asText("");
        for (JsonNode fila : guia.path("rows")) {
            String tallePrincipal = "";
            boolean coincide = false;
            for (JsonNode atributo : fila.path("attributes")) {
                String id = atributo.path("id").asText("");
                if (id.equals(atributoPrincipal) || (tallePrincipal.isBlank() && id.equals("SIZE"))) {
                    tallePrincipal = atributo.path("values").path(0).path("name").asText("");
                }
                if (!esAtributoTalleGuia(id, atributoPrincipal)) continue;
                for (JsonNode valor : atributo.path("values")) {
                    if (normalizarTalle(valor.path("name").asText()).equals(buscado)) {
                        coincide = true;
                    }
                }
            }
            if (coincide) return new FilaGuiaAsignada(fila.path("id").asText(""), tallePrincipal);
        }
        return null;
    }

    private boolean esAtributoTalleGuia(String id, String principal) {
        String normalizado = id == null ? "" : id.toUpperCase(Locale.ROOT);
        return normalizado.equals(principal == null ? "" : principal.toUpperCase(Locale.ROOT))
                || normalizado.equals("SIZE") || normalizado.equals("MANUFACTURER_SIZE")
                || (normalizado.contains("FILTRABLE") && normalizado.contains("SIZE"));
    }

    private String normalizarTalle(String value) {
        if (value == null) return "";
        return java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replaceAll("[^A-Za-z0-9]", "")
                .toUpperCase(Locale.ROOT);
    }

    Map<String, Object> construirPayload(Producto producto, boolean nuevaPublicacion) {
        Map<String, Object> body = new LinkedHashMap<>();
        String categoria = texto(producto.getMercadoLibreCategoriaId(), categoryId);
        if (categoria.isBlank()) {
            throw new IllegalArgumentException("El producto no tiene categoría de Mercado Libre");
        }

        List<ProductoVariante> variantes = varianteRepository.findByProductoIdOrderByNombreAsc(producto.getId());
        // En el modelo User Products, family_name se envía solamente al crear. Mercado Libre
        // rechaza ese campo dentro del PUT /items; su modificación usa un recurso separado.
        if (nuevaPublicacion) {
            body.put(userProducts && variantes.isEmpty() ? "family_name" : "title", producto.getDescripcion());
        } else if (!userProducts) {
            body.put("title", producto.getDescripcion());
        }
        body.put("price", precioPublicacion(producto, variantes));
        ProductoVariante presentacionSimple = variantes.size() == 1 ? variantes.get(0) : null;
        body.put("available_quantity", presentacionSimple == null
                ? Optional.ofNullable(producto.getCantidad()).orElse(0)
                : Optional.ofNullable(presentacionSimple.getStock()).orElse(0));
        if (variantes.size() > 1) {
            body.put("variations", construirVariaciones(variantes, resolverFilasGuia(producto, variantes)));
        }

        if (nuevaPublicacion) {
            body.put("category_id", categoria);
            body.put("currency_id", "ARS");
            body.put("buying_mode", "buy_it_now");
            body.put("condition", condicion(producto));
            body.put("listing_type_id", texto(producto.getMercadoLibreListingTypeId(), listingTypeId));
            body.put("channels", List.of("marketplace"));
        }

        if (producto.getMercadoLibreOfficialStoreId() != null) {
            body.put("official_store_id", producto.getMercadoLibreOfficialStoreId());
        }
        agregarSiTieneTexto(body, "video_id", producto.getMercadoLibreVideoId());
        if (producto.getMercadoLibreEnvioGratis() != null || producto.getMercadoLibreRetiroPersonal() != null
                || (producto.getMercadoLibreModoEnvio() != null && !producto.getMercadoLibreModoEnvio().isBlank())) {
            Map<String, Object> shipping = new LinkedHashMap<>();
            agregarSiTieneTexto(shipping, "mode", producto.getMercadoLibreModoEnvio());
            if (producto.getMercadoLibreEnvioGratis() != null) shipping.put("free_shipping", producto.getMercadoLibreEnvioGratis());
            if (producto.getMercadoLibreRetiroPersonal() != null) shipping.put("local_pick_up", producto.getMercadoLibreRetiroPersonal());
            body.put("shipping", shipping);
        }

        List<Map<String, Object>> atributos;
        if (presentacionSimple == null) {
            atributos = construirAtributos(producto);
        } else {
            Map<Long, FilaGuiaAsignada> filas = resolverFilasGuia(producto, variantes);
            atributos = construirAtributosUserProduct(producto, presentacionSimple,
                    presentacionSimple.getId() == null ? null : filas.get(presentacionSimple.getId()));
        }
        if (!atributos.isEmpty()) body.put("attributes", atributos);

        List<Map<String, String>> terminos = new ArrayList<>();
        agregarTermino(terminos, "WARRANTY_TYPE", producto.getMercadoLibreGarantiaTipo());
        agregarTermino(terminos, "WARRANTY_TIME", producto.getMercadoLibreGarantiaTiempo());
        if (producto.getMercadoLibreTiempoDisponibilidad() != null && producto.getMercadoLibreTiempoDisponibilidad() > 0) {
            agregarTermino(terminos, "MANUFACTURING_TIME", producto.getMercadoLibreTiempoDisponibilidad() + " días");
        }
        if (!terminos.isEmpty()) body.put("sale_terms", terminos);

        List<Map<String, String>> fotos = presentacionSimple == null
                ? construirFotos(producto) : construirFotos(presentacionSimple, producto);
        boolean hayFotoDeVariante = variantes.stream().anyMatch(ProductoVariante::tieneFoto);
        if (nuevaPublicacion && fotos.isEmpty() && !hayFotoDeVariante) {
            if (producto.tieneFotoLocal() && publicBaseUrl.isBlank()) {
                throw new IllegalArgumentException("El producto tiene una foto local, pero falta configurar PUBLIC_BASE_URL para que Mercado Libre pueda verla");
            }
            throw new IllegalArgumentException("Agregue al menos una foto al producto antes de publicarlo en Mercado Libre");
        }
        if (!fotos.isEmpty()) body.put("pictures", fotos);
        return body;
    }

    private java.math.BigDecimal precioPublicacion(Producto producto, List<ProductoVariante> variantes) {
        if (producto.getPrecioContado() != null) return producto.getPrecioContado();
        return variantes.stream().map(ProductoVariante::getPrecioContado).filter(Objects::nonNull).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("El producto y sus variantes no tienen precio de contado"));
    }

    private List<Map<String, Object>> construirVariaciones(List<ProductoVariante> variantes,
                                                            Map<Long, FilaGuiaAsignada> filasGuia) {
        Set<String> idsEsperados = new LinkedHashSet<>(AtributosVarianteHelper.obtener(variantes.get(0)).keySet());
        return variantes.stream().map(variante -> {
            List<Map<String, Object>> combinaciones = new ArrayList<>();
            Map<String, String> atributosImportados = new LinkedHashMap<>(AtributosVarianteHelper.obtener(variante));
            if (!atributosImportados.keySet().equals(idsEsperados)) {
                throw new IllegalArgumentException("Todas las variantes deben completar las mismas características. Revise "
                        + variante.getNombreMostrar());
            }
            FilaGuiaAsignada filaGuia = variante.getId() == null ? null : filasGuia.get(variante.getId());
            if (filaGuia != null && tieneTexto(filaGuia.tallePrincipal())) {
                atributosImportados.put("SIZE", filaGuia.tallePrincipal());
            }
            atributosImportados.forEach((id, valor) -> {
                if (!esSoloLectura(variante.getProducto(), id) && !esAtributoGuiaImportado(id)) {
                    combinaciones.add(Map.of("id", id, "value_name", valor));
                }
            });
            if (combinaciones.isEmpty()) throw new IllegalArgumentException("La variante " + variante.getSku() + " no tiene atributos de variación");
            Map<String, Object> json = new LinkedHashMap<>();
            if (variante.getMercadoLibreVariationId() != null && !variante.getMercadoLibreVariationId().isBlank())
                json.put("id", variante.getMercadoLibreVariationId());
            json.put("attribute_combinations", combinaciones);
            json.put("price", Optional.ofNullable(variante.getPrecioContado()).orElse(variante.getProducto().getPrecioContado()));
            json.put("available_quantity", Optional.ofNullable(variante.getStock()).orElse(0));
            json.put("seller_custom_field", variante.getSku());
            List<Map<String, Object>> atributosPropios = new ArrayList<>();
            atributosPropios.add(Map.of("id", "SELLER_SKU", "value_name", variante.getSku()));
            if (variante.getMercadoLibreGtin() != null && variante.getMercadoLibreGtin().matches("\\d{8,14}")) {
                atributosPropios.add(Map.of("id", "GTIN", "value_name", variante.getMercadoLibreGtin()));
            }
            if (filaGuia != null && tieneTexto(filaGuia.id())) {
                atributosPropios.add(Map.of("id", "SIZE_GRID_ROW_ID", "value_name", filaGuia.id()));
            }
            json.put("attributes", atributosPropios);
            return json;
        }).toList();
    }

    private record FilaGuiaAsignada(String id, String tallePrincipal) {}

    private List<Map<String, Object>> construirAtributos(Producto producto) {
        Map<String, Map<String, Object>> atributos = new LinkedHashMap<>();
        agregarAtributo(atributos, "SELLER_SKU", null, producto.getSku());
        agregarAtributo(atributos, "ITEM_CONDITION", ITEM_CONDITION_NEW, null);
        agregarAtributo(atributos, "BRAND", null, producto.getMercadoLibreMarca());
        agregarAtributo(atributos, "MODEL", null, producto.getMercadoLibreModelo());
        agregarAtributo(atributos, "GARMENT_TYPE", null, producto.getMercadoLibreTipoPrenda());
        agregarAtributo(atributos, "GTIN", null, producto.getMercadoLibreGtin());
        agregarAtributo(atributos, "GENDER", null, producto.getMercadoLibreGenero());
        agregarAtributo(atributos, "SIZE_GRID_ID", null, producto.getMercadoLibreGuiaTallesId());
        if (!Boolean.TRUE.equals(producto.getUsaVariantes())) {
            agregarAtributo(atributos, "SIZE_GRID_ROW_ID", null, producto.getMercadoLibreGuiaTallesFilaId());
        }

        String json = producto.getMercadoLibreAtributosJson();
        if (json != null && !json.isBlank()) {
            try {
                List<Map<String, Object>> personalizados = objectMapper.readValue(json,
                        new TypeReference<List<Map<String, Object>>>() {});
                for (Map<String, Object> atributo : personalizados) {
                    Object id = atributo.get("id");
                    if (id == null || id.toString().isBlank()) {
                        throw new IllegalArgumentException("Cada atributo adicional debe tener un campo id");
                    }
                    if (!atributo.containsKey("value_id") && !atributo.containsKey("value_name")) {
                        throw new IllegalArgumentException("El atributo " + id + " debe tener value_id o value_name");
                    }
                    if (!esSoloLectura(producto, id.toString())
                            && !esAtributoGuiaImportado(id.toString())
                            && !("EMPTY_GTIN_REASON".equals(id.toString()) && tieneTexto(producto.getMercadoLibreGtin()))) {
                        atributos.put(id.toString(), new LinkedHashMap<>(atributo));
                    }
                }
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("El JSON de atributos de Mercado Libre no es válido", e);
            }
        }
        return new ArrayList<>(atributos.values());
    }

    private boolean esSoloLectura(Producto producto, String id) {
        return producto != null && producto.getMercadoLibreCategoriaId() != null
                && atributosSoloLecturaPorCategoria
                .getOrDefault(producto.getMercadoLibreCategoriaId(), Set.of()).contains(id);
    }

    private boolean esAtributoGuiaImportado(String id) {
        return "SIZE_GRID_ID".equals(id) || "SIZE_GRID_ROW_ID".equals(id);
    }

    private void agregarAtributo(Map<String, Map<String, Object>> atributos, String id,
                                 String valueId, String valueName) {
        if ((valueId == null || valueId.isBlank()) && (valueName == null || valueName.isBlank())) return;
        Map<String, Object> atributo = new LinkedHashMap<>();
        atributo.put("id", id);
        if (valueId != null && !valueId.isBlank()) atributo.put("value_id", valueId);
        else atributo.put("value_name", valueName.trim());
        atributos.put(id, atributo);
    }

    private List<Map<String, String>> construirFotos(Producto producto) {
        Set<String> urls = new LinkedHashSet<>();
        if (producto.tieneFotoLocal() && !publicBaseUrl.isBlank() && producto.getId() != null) {
            urls.add(FotoCanalHelper.resolverUrl(producto, publicBaseUrl));
        }
        agregarUrl(urls, producto.getFotoUrlExterna());
        if (producto.getFotosUrlsExternas() != null) {
            producto.getFotosUrlsExternas().lines().forEach(url -> agregarUrl(urls, url));
        }
        return urls.stream().limit(12).map(url -> Map.of("source", url)).toList();
    }

    private List<Map<String, String>> construirFotos(ProductoVariante variante, Producto producto) {
        String propia = FotoCanalHelper.resolverUrl(variante, publicBaseUrl);
        if (tieneTexto(propia)) return List.of(Map.of("source", propia));
        return construirFotos(producto);
    }

    private void agregarUrl(Set<String> urls, String valor) {
        if (valor == null || valor.isBlank()) return;
        String url = valor.trim();
        if (!url.startsWith("https://") && !url.startsWith("http://")) {
            throw new IllegalArgumentException("Las URLs de fotos deben comenzar con http:// o https://");
        }
        urls.add(url);
    }

    private void publicarDescripcionConRenovacion(String id, String descripcion, boolean nueva) {
        if (descripcion == null || descripcion.isBlank()) return;
        try {
            publicarDescripcion(id, descripcion, nueva, tokenService.obtenerAccessToken());
        } catch (RestClientResponseException e) {
            if (e.getStatusCode() != HttpStatus.UNAUTHORIZED) throw e;
            tokenService.invalidarAccessToken();
            publicarDescripcion(id, descripcion, nueva, tokenService.obtenerAccessToken());
        }
    }

    private void publicarDescripcion(String id, String descripcion, boolean nueva, String token) {
        RestClient.RequestBodySpec request = (nueva
                ? restClient.post().uri("https://api.mercadolibre.com/items/" + id + "/description")
                : restClient.put().uri("https://api.mercadolibre.com/items/" + id + "/description"))
                .headers(h -> h.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON);
        request.body(Map.of("plain_text", descripcion.trim())).retrieve().toBodilessEntity();
    }

    private void sincronizarEstadoConRenovacion(String id, String estado) {
        if (estado == null || !(estado.equals("active") || estado.equals("paused"))) return;
        try {
            sincronizarEstado(id, estado, tokenService.obtenerAccessToken());
        } catch (RestClientResponseException e) {
            if (e.getStatusCode() != HttpStatus.UNAUTHORIZED) throw e;
            tokenService.invalidarAccessToken();
            sincronizarEstado(id, estado, tokenService.obtenerAccessToken());
        }
    }

    private void sincronizarEstado(String id, String estado, String token) {
        restClient.put().uri("https://api.mercadolibre.com/items/" + id)
                .headers(h -> h.setBearerAuth(token)).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("status", estado)).retrieve().toBodilessEntity();
    }

    private String condicion(Producto producto) {
        String condicion = producto.getMercadoLibreCondicion();
        return condicion != null && Set.of("new", "used", "refurbished").contains(condicion) ? condicion : "new";
    }

    private void agregarTermino(List<Map<String, String>> terminos, String id, String valor) {
        if (valor != null && !valor.isBlank()) terminos.add(Map.of("id", id, "value_name", valor.trim()));
    }

    private void agregarSiTieneTexto(Map<String, Object> body, String clave, String valor) {
        if (valor != null && !valor.isBlank()) body.put(clave, valor.trim());
    }

    private String resolverId(Producto producto, String idActual) {
        return texto(idActual, producto.getMercadoLibreId()).isBlank() ? null : texto(idActual, producto.getMercadoLibreId());
    }

    private String texto(String principal, String alternativa) {
        return principal != null && !principal.isBlank() ? principal.trim()
                : alternativa == null ? "" : alternativa.trim();
    }

    private void validar() {
        if (!configurado()) throw new IllegalStateException("Mercado Libre no está configurado para renovación OAuth");
    }

    private String limpiarUrl(String value) { return value.replaceAll("/+$", ""); }

    private boolean tieneTexto(String value) { return value != null && !value.isBlank(); }
}

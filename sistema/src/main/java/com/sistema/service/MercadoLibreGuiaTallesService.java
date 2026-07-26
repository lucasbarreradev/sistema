package com.sistema.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sistema.model.Producto;
import com.sistema.model.ProductoVariante;
import com.sistema.repository.ProductoVarianteRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MercadoLibreGuiaTallesService {
    private static final String SITE_ID = "MLA";
    private static final Pattern NUMERO_UNIDAD = Pattern.compile("^\\s*(-?\\d+(?:[.,]\\d+)?)\\s*([^\\d\\s].*)?$");
    private final RestClient restClient = RestClient.create();
    private final MercadoLibreTokenService tokenService;
    private final ProductoService productoService;
    private final ProductoVarianteRepository varianteRepository;
    private final ObjectMapper objectMapper;

    public MercadoLibreGuiaTallesService(MercadoLibreTokenService tokenService, ProductoService productoService,
                                         ProductoVarianteRepository varianteRepository, ObjectMapper objectMapper) {
        this.tokenService = tokenService;
        this.productoService = productoService;
        this.varianteRepository = varianteRepository;
        this.objectMapper = objectMapper;
    }

    public Contexto contexto(Long productoId) {
        Producto producto = producto(productoId);
        if (!texto(producto.getMercadoLibreCategoriaId())) {
            throw new IllegalArgumentException("El producto no tiene una categoría de Mercado Libre");
        }
        JsonNode categoria = get("/categories/" + producto.getMercadoLibreCategoriaId());
        String dominioCompleto = categoria.path("settings").path("catalog_domain").asText(
                categoria.path("catalog_domain").asText(""));
        if (!texto(dominioCompleto)) {
            throw new IllegalArgumentException("La categoría seleccionada no informa un dominio de catálogo");
        }
        validarDominioActivo(dominioCompleto);
        JsonNode fichaDominio = get("/domains/" + dominioCompleto + "/technical_specs");
        LinkedHashMap<String, Campo> todos = extraerCampos(fichaDominio);
        List<Campo> filtros = todos.values().stream()
                .filter(c -> c.tags().contains("grid_template_required") || "BRAND".equals(c.id()))
                .filter(c -> !c.soloLectura()).toList();
        if (filtros.stream().noneMatch(c -> "GENDER".equals(c.id()))) {
            throw new IllegalArgumentException("La categoría no tiene configurada una guía de talles");
        }
        return new Contexto(producto, categoria.path("name").asText(producto.getMercadoLibreCategoriaId()),
                dominioCompleto, dominioCorto(dominioCompleto), filtros);
    }

    public ConstructorGuia preparar(Long productoId, String nombre, String tipoMedida,
                                     MultiValueMap<String, String> parametros) {
        Contexto contexto = contexto(productoId);
        validarNombre(nombre);
        String tipoMedidaNormalizado = normalizarTipoMedida(tipoMedida);
        Map<String, Seleccion> filtros = leerSelecciones("filtro_", contexto.filtros(), parametros, true, contexto.producto());
        JsonNode ficha = post("/domains/" + contexto.dominioCompleto() + "/technical_specs?section=grids",
                Map.of("attributes", atributosFichaTecnicaPayload(contexto.filtros(), filtros)));
        List<Campo> campos = new ArrayList<>(extraerCampos(ficha).values());
        List<Campo> generales = campos.stream()
                .filter(c -> !c.soloLectura() && ("PARENT_PK".equals(c.jerarquia()) || "FAMILY".equals(c.jerarquia())))
                .filter(c -> !filtros.containsKey(c.id())).toList();
        List<Campo> filas = campos.stream()
                .filter(c -> (!c.soloLectura() || "FILTRABLE_SIZE".equals(c.id()))
                        && !"PARENT_PK".equals(c.jerarquia()) && !"FAMILY".equals(c.jerarquia()))
                .filter(c -> !c.tags().contains("fixed")).toList();
        filas = filas.stream().filter(c -> esCompatibleConTipoMedida(c, tipoMedidaNormalizado)).toList();
        if (filas.stream().noneMatch(Campo::candidatoPrincipal)) {
            filas = filas.stream().map(c -> esTallePrincipalAlternativo(c)
                    ? new Campo(c.id(), c.nombre(), c.tipo(), c.valores(), c.obligatorio(), true,
                    c.multivalor(), c.unidad(), c.jerarquia(), c.tags()) : c).toList();
        }
        if (filas.stream().noneMatch(Campo::candidatoPrincipal)) {
            throw new IllegalArgumentException("La ficha técnica de Mercado Libre no informó una columna de talle utilizable para esta categoría");
        }
        return new ConstructorGuia(contexto, limpiarNombre(nombre), tipoMedidaNormalizado, filtros,
                generales, filas, ficha.path("input").path("groups").path(0).path("components").path(0)
                        .path("ui_config").path("max_allowed").asInt(75));
    }

    public Guia crear(Long productoId, String nombre, String tipoMedida, String atributoPrincipal,
                      MultiValueMap<String, String> parametros) {
        ConstructorGuia constructor = preparar(productoId, nombre, tipoMedida, parametros);
        Campo principal = constructor.camposFila().stream()
                .filter(c -> c.id().equals(atributoPrincipal) && c.candidatoPrincipal()).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Seleccione un talle principal válido"));
        Map<String, Seleccion> generales = leerSelecciones("general_", constructor.camposGenerales(), parametros,
                false, constructor.contexto().producto());
        Map<String, Campo> camposFila = new LinkedHashMap<>();
        constructor.camposFila().forEach(c -> camposFila.put(c.id(), c));
        SortedSet<Integer> indices = new TreeSet<>();
        for (String clave : parametros.keySet()) {
            if (!clave.startsWith("fila_")) continue;
            String[] partes = clave.split("_", 3);
            if (partes.length == 3) try { indices.add(Integer.parseInt(partes[1])); } catch (NumberFormatException ignored) {}
        }
        List<Map<String, Object>> filas = new ArrayList<>();
        Set<String> principales = new HashSet<>();
        for (Integer indice : indices) {
            List<Map<String, Object>> atributos = new ArrayList<>();
            for (Campo campo : constructor.camposFila()) {
                List<String> valores = parametros.get("fila_" + indice + "_" + campo.id());
                List<Seleccion> selecciones = convertirSelecciones(valores, campo);
                if ("FILTRABLE_SIZE".equals(campo.id())) {
                    selecciones = expandirRangoEquivalencias(selecciones, campo);
                }
                if (selecciones.isEmpty()) {
                    if (campo.obligatorio() || campo.id().equals(principal.id())) {
                        throw new IllegalArgumentException("Complete " + campo.nombre() + " en la fila " + (indice + 1));
                    }
                    continue;
                }
                if (campo.id().equals(principal.id())) {
                    String clave = selecciones.get(0).nombre().trim().toLowerCase(Locale.ROOT);
                    if (!principales.add(clave)) throw new IllegalArgumentException("El talle principal no puede repetirse: " + selecciones.get(0).nombre());
                }
                atributos.add(atributoPayload(campo.id(), selecciones, campo));
            }
            if (!atributos.isEmpty()) filas.add(Map.of("attributes", atributos));
        }
        if (filas.isEmpty()) throw new IllegalArgumentException("Agregue al menos una fila de talles");
        if (filas.size() > constructor.maxFilas()) throw new IllegalArgumentException("La guía admite como máximo " + constructor.maxFilas() + " filas");

        List<Map<String, Object>> atributosGenerales = new ArrayList<>(atributosPayload(constructor.filtros()));
        atributosGenerales.addAll(atributosPayload(generales));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("names", Map.of(SITE_ID, constructor.nombre()));
        body.put("domain_id", constructor.contexto().dominioCorto());
        body.put("site_id", SITE_ID);
        body.put("measure_type", constructor.tipoMedida());
        body.put("main_attribute", Map.of("attributes", List.of(Map.of("site_id", SITE_ID, "id", principal.id()))));
        body.put("attributes", atributosGenerales);
        body.put("rows", filas);
        JsonNode creada = post("/catalog/charts", body);
        return mapearGuia(creada);
    }

    public List<GuiaResumen> buscar(Long productoId, MultiValueMap<String, String> parametros) {
        Contexto contexto = contexto(productoId);
        Map<String, Seleccion> filtros = leerSelecciones("filtro_", contexto.filtros(), parametros, true, contexto.producto());
        JsonNode usuario = get("/users/me");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("domain_id", contexto.dominioCorto());
        body.put("site_id", SITE_ID);
        body.put("seller_id", usuario.path("id").asLong());
        body.put("attributes", atributosPayload(filtros));
        JsonNode respuesta = post("/catalog/charts/search?offset=0&limit=100", body);
        List<GuiaResumen> resultado = new ArrayList<>();
        for (JsonNode chart : respuesta.path("charts")) {
            resultado.add(new GuiaResumen(chart.path("id").asText(), chart.path("names").path(SITE_ID).asText("Guía " + chart.path("id").asText()),
                    chart.path("type").asText(), chart.path("domain_id").asText(), chart.path("main_attribute_id").asText(),
                    chart.path("rows").size()));
        }
        return resultado;
    }

    public Guia consultar(String chartId) {
        if (!texto(chartId)) throw new IllegalArgumentException("Falta el ID de la guía");
        return mapearGuia(get("/catalog/charts/" + chartId.trim()));
    }

    public void asignar(Long productoId, String chartId, String rowId) {
        Producto producto = producto(productoId);
        Contexto contexto = contexto(productoId);
        JsonNode guiaJson = get("/catalog/charts/" + chartId.trim());
        Guia guia = mapearGuia(guiaJson);
        if (!dominioCorto(guia.dominio()).equalsIgnoreCase(contexto.dominioCorto())) {
            throw new IllegalArgumentException("La guía pertenece al dominio " + guia.dominio() + " y no a la categoría del producto");
        }
        if ("SPECIFIC".equalsIgnoreCase(guia.tipo())) {
            long vendedor = get("/users/me").path("id").asLong();
            if (guia.sellerId() != null && guia.sellerId() != vendedor) {
                throw new IllegalArgumentException("La guía personalizada no pertenece a la cuenta conectada");
            }
        }
        aplicarConsistencia(producto, guiaJson.path("attributes"));
        List<ProductoVariante> variantes = varianteRepository.findByProductoIdOrderByNombreAsc(productoId);
        if (!variantes.isEmpty()) {
            for (ProductoVariante variante : variantes) {
                if (!texto(variante.getTalle()) || !tieneFila(guiaJson, variante.getTalle())) {
                    throw new IllegalArgumentException("La guía no contiene el talle de la variante " + variante.getNombreMostrar());
                }
            }
            producto.setMercadoLibreGuiaTallesFilaId(null);
        } else {
            String fila = rowId;
            if (!texto(fila) && guia.filas().size() == 1) fila = guia.filas().get(0).id();
            boolean filaValida = texto(fila) && guia.filas().stream().map(FilaGuia::id).anyMatch(fila::equals);
            if (!filaValida) {
                throw new IllegalArgumentException("Seleccione la fila que corresponde al producto simple");
            }
            producto.setMercadoLibreGuiaTallesFilaId(normalizarFila(chartId, fila));
        }
        producto.setMercadoLibreGuiaTallesId(chartId.trim());
        productoService.saveProducto(producto);
    }

    public void desasignar(Long productoId) {
        Producto producto = producto(productoId);
        producto.setMercadoLibreGuiaTallesId(null);
        producto.setMercadoLibreGuiaTallesFilaId(null);
        productoService.saveProducto(producto);
    }

    private void aplicarConsistencia(Producto producto, JsonNode atributos) {
        for (JsonNode atributo : atributos) {
            String id = atributo.path("id").asText();
            String valor = atributo.path("values").path(0).path("name").asText();
            if ("GENDER".equals(id)) producto.setMercadoLibreGenero(consistente("género", producto.getMercadoLibreGenero(), valor));
            if ("BRAND".equals(id)) producto.setMercadoLibreMarca(consistente("marca", producto.getMercadoLibreMarca(), valor));
        }
    }

    private String consistente(String campo, String actual, String guia) {
        if (!texto(guia)) return actual;
        if (texto(actual) && !actual.trim().equalsIgnoreCase(guia.trim())) {
            throw new IllegalArgumentException("La " + campo + " de la guía (" + guia + ") no coincide con la del producto (" + actual + ")");
        }
        return guia;
    }

    private boolean tieneFila(JsonNode guia, String talle) {
        String buscado = normalizar(talle);
        String principal = guia.path("main_attribute_id").asText();
        for (JsonNode fila : guia.path("rows")) for (JsonNode atributo : fila.path("attributes")) {
            String id = atributo.path("id").asText();
            if (!esAtributoTalleGuia(id, principal)) continue;
            for (JsonNode valor : atributo.path("values")) if (normalizar(valor.path("name").asText()).equals(buscado)) return true;
        }
        return false;
    }

    private boolean esAtributoTalleGuia(String id, String principal) {
        String normalizado = id == null ? "" : id.toUpperCase(Locale.ROOT);
        return normalizado.equals(principal == null ? "" : principal.toUpperCase(Locale.ROOT))
                || normalizado.equals("SIZE") || normalizado.equals("MANUFACTURER_SIZE")
                || (normalizado.contains("FILTRABLE") && normalizado.contains("SIZE"));
    }

    private Guia mapearGuia(JsonNode json) {
        List<FilaGuia> filas = new ArrayList<>();
        for (JsonNode fila : json.path("rows")) {
            Map<String, String> atributos = new LinkedHashMap<>();
            for (JsonNode atributo : fila.path("attributes")) {
                List<String> valores = new ArrayList<>();
                for (JsonNode valor : atributo.path("values")) valores.add(valor.path("name").asText(valor.path("id").asText()));
                atributos.put(atributo.path("name").asText(atributo.path("id").asText()), String.join(", ", valores));
            }
            filas.add(new FilaGuia(fila.path("id").asText(), atributos));
        }
        Map<String, String> generales = new LinkedHashMap<>();
        for (JsonNode atributo : json.path("attributes")) {
            generales.put(atributo.path("name").asText(atributo.path("id").asText()),
                    atributo.path("values").path(0).path("name").asText());
        }
        return new Guia(json.path("id").asText(), json.path("names").path(SITE_ID).asText("Guía " + json.path("id").asText()),
                json.path("domain_id").asText(), json.path("type").asText(),
                json.path("seller_id").isNumber() ? json.path("seller_id").asLong() : null,
                json.path("main_attribute_id").asText(), generales, filas);
    }

    private Map<String, Seleccion> leerSelecciones(String prefijo, List<Campo> campos,
                                                    MultiValueMap<String, String> parametros, boolean obligatorios,
                                                    Producto producto) {
        Map<String, Seleccion> resultado = new LinkedHashMap<>();
        for (Campo campo : campos) {
            String crudo = parametros.getFirst(prefijo + campo.id());
            if (!texto(crudo)) crudo = valorProducto(producto, campo.id());
            Seleccion seleccion = convertirSeleccion(crudo, campo);
            if (seleccion != null) resultado.put(campo.id(), seleccion);
            else if (obligatorios || campo.obligatorio()) throw new IllegalArgumentException("Complete " + campo.nombre());
        }
        return resultado;
    }

    private String valorProducto(Producto producto, String id) {
        return switch (id) {
            case "BRAND" -> producto.getMercadoLibreMarca();
            case "GENDER" -> producto.getMercadoLibreGenero();
            default -> null;
        };
    }

    private List<Seleccion> convertirSelecciones(List<String> crudos, Campo campo) {
        if (crudos == null) return List.of();
        List<Seleccion> resultado = new ArrayList<>();
        for (String crudo : crudos) {
            if (!texto(crudo)) continue;
            if (campo.multivalor() && !"list".equals(campo.tipo()) && crudo.contains(",")) {
                for (String parte : crudo.split(",")) {
                    Seleccion seleccion = convertirSeleccion(parte, campo);
                    if (seleccion != null) resultado.add(seleccion);
                }
            } else {
                Seleccion seleccion = convertirSeleccion(crudo, campo);
                if (seleccion != null) resultado.add(seleccion);
            }
        }
        return resultado;
    }

    private Seleccion convertirSeleccion(String crudo, Campo campo) {
        if (!texto(crudo)) return null;
        String[] partes = crudo.trim().split("\\|\\|\\|", 2);
        if (partes.length == 2) return new Seleccion(partes[0], partes[1], null, null);
        if ("number_unit".equals(campo.tipo())) {
            Matcher matcher = NUMERO_UNIDAD.matcher(crudo.trim());
            if (!matcher.matches()) throw new IllegalArgumentException("El valor de " + campo.nombre() + " debe ser un número y su unidad");
            String numeroTexto = matcher.group(1).replace(',', '.');
            String unidad = texto(matcher.group(2)) ? matcher.group(2).trim() : campo.unidad();
            if (!texto(unidad)) throw new IllegalArgumentException("Indique la unidad de " + campo.nombre());
            return new Seleccion(null, numeroTexto + " " + unidad, new BigDecimal(numeroTexto), unidad);
        }
        return new Seleccion(null, crudo.trim(), null, null);
    }

    private List<Seleccion> expandirRangoEquivalencias(List<Seleccion> selecciones, Campo campo) {
        if (selecciones.size() < 2) return selecciones;
        String desdeId = selecciones.get(0).id();
        String hastaId = selecciones.get(1).id();
        int desde = -1;
        int hasta = -1;
        for (int i = 0; i < campo.valores().size(); i++) {
            if (Objects.equals(campo.valores().get(i).id(), desdeId)) desde = i;
            if (Objects.equals(campo.valores().get(i).id(), hastaId)) hasta = i;
        }
        if (desde < 0 || hasta < 0) {
            throw new IllegalArgumentException("Seleccione equivalencias válidas de Mercado Libre");
        }
        if (hasta < desde) {
            throw new IllegalArgumentException("La equivalencia Hasta debe ser igual o posterior a Desde");
        }
        List<Seleccion> resultado = new ArrayList<>();
        for (int i = desde; i <= hasta; i++) {
            Valor valor = campo.valores().get(i);
            resultado.add(new Seleccion(valor.id(), valor.nombre(), null, null));
        }
        return resultado;
    }

    private List<Map<String, Object>> atributosPayload(Map<String, Seleccion> selecciones) {
        List<Map<String, Object>> resultado = new ArrayList<>();
        selecciones.forEach((id, seleccion) -> resultado.add(atributoPayload(id, List.of(seleccion), null)));
        return resultado;
    }

    /**
     * El endpoint de ficha tecnica no acepta el formato compacto utilizado por
     * /catalog/charts. Necesita los valores seleccionados tanto en la raiz del
     * atributo como dentro de values, igual que los atributos de un item.
     */
    private List<Map<String, Object>> atributosFichaTecnicaPayload(List<Campo> campos,
                                                                    Map<String, Seleccion> selecciones) {
        Map<String, Campo> camposPorId = new HashMap<>();
        campos.forEach(campo -> camposPorId.put(campo.id(), campo));
        List<Map<String, Object>> resultado = new ArrayList<>();
        selecciones.forEach((id, seleccion) -> {
            Map<String, Object> atributo = new LinkedHashMap<>();
            atributo.put("id", id);
            Campo campo = camposPorId.get(id);
            atributo.put("name", campo == null ? id : campo.nombre());
            atributo.put("value_id", texto(seleccion.id()) ? seleccion.id() : null);
            atributo.put("value_name", seleccion.nombre());
            atributo.put("values", atributoPayload(id, seleccion, campo).get("values"));
            resultado.add(atributo);
        });
        return resultado;
    }

    private Map<String, Object> atributoPayload(String id, List<Seleccion> selecciones, Campo campo) {
        List<Map<String, Object>> valores = new ArrayList<>();
        for (Seleccion seleccion : selecciones) {
            Map<String, Object> valor = new LinkedHashMap<>();
            if (texto(seleccion.id())) valor.put("id", seleccion.id());
            if (texto(seleccion.nombre())) valor.put("name", seleccion.nombre());
            if (seleccion.numero() != null) valor.put("struct", Map.of("number", seleccion.numero(), "unit", seleccion.unidad()));
            valores.add(valor);
        }
        return new LinkedHashMap<>(Map.of("id", id, "values", valores));
    }

    private Map<String, Object> atributoPayload(String id, Seleccion seleccion, Campo campo) {
        return atributoPayload(id, List.of(seleccion), campo);
    }

    private LinkedHashMap<String, Campo> extraerCampos(JsonNode raiz) {
        LinkedHashMap<String, Campo> resultado = new LinkedHashMap<>();
        recorrer(raiz, resultado);
        return resultado;
    }

    private void recorrer(JsonNode nodo, LinkedHashMap<String, Campo> resultado) {
        if (nodo == null) return;
        if (nodo.isObject()) {
            JsonNode atributos = nodo.path("attributes");
            if (atributos.isArray()) for (JsonNode atributo : atributos) {
                String id = atributo.path("id").asText();
                if (!texto(id)) continue;
                Set<String> tags = tags(atributo.path("tags"));
                List<Valor> valores = new ArrayList<>();
                for (JsonNode valor : atributo.path("values")) valores.add(new Valor(valor.path("id").asText(), valor.path("name").asText()));
                String unidad = atributo.path("default_unit_id").asText(nodo.path("default_unified_unit_id").asText(""));
                Campo campo = new Campo(id, atributo.path("name").asText(nodo.path("label").asText(id)),
                        atributo.path("value_type").asText("string"), valores, tags.contains("required"),
                        tags.contains("main_attribute_candidate"), tags.contains("multivalued"), unidad,
                        atributo.path("hierarchy").asText(), tags);
                resultado.putIfAbsent(id, campo);
            }
            nodo.fields().forEachRemaining(e -> recorrer(e.getValue(), resultado));
        } else if (nodo.isArray()) nodo.forEach(n -> recorrer(n, resultado));
    }

    private Set<String> tags(JsonNode nodo) {
        Set<String> resultado = new HashSet<>();
        if (nodo.isArray()) nodo.forEach(n -> resultado.add(n.asText()));
        else if (nodo.isTextual() && texto(nodo.asText())) resultado.add(nodo.asText());
        else if (nodo.isObject()) nodo.fields().forEachRemaining(tag -> {
            JsonNode valor = tag.getValue();
            if ((valor.isBoolean() && valor.asBoolean()) || (!valor.isBoolean() && !valor.isNull())) {
                resultado.add(tag.getKey());
            }
        });
        return resultado;
    }

    private boolean esCompatibleConTipoMedida(Campo campo, String tipoMedida) {
        boolean corporal = campo.tags().contains("BODY_MEASURE");
        boolean prenda = campo.tags().contains("CLOTHING_MEASURE");
        if (!corporal && !prenda) return true;
        return campo.tags().contains(tipoMedida);
    }

    private boolean esTallePrincipalAlternativo(Campo campo) {
        String id = campo.id().toUpperCase(Locale.ROOT);
        return id.equals("SIZE") || id.equals("MANUFACTURER_SIZE")
                || (id.endsWith("_SIZE") && !id.contains("GRID") && !id.contains("FILTRABLE"));
    }

    private void validarDominioActivo(String dominio) {
        JsonNode activos = get("/catalog/charts/" + SITE_ID + "/configurations/active_domains");
        for (JsonNode item : activos.path("domains")) if (dominio.equalsIgnoreCase(item.path("domain_id").asText())) return;
        throw new IllegalArgumentException("La categoría no está habilitada por Mercado Libre para usar guías de talles");
    }

    private Producto producto(Long id) {
        return productoService.getProductoById(id).orElseThrow(() -> new IllegalArgumentException("Producto no encontrado"));
    }

    private JsonNode get(String endpoint) {
        return ejecutar(token -> restClient.get().uri("https://api.mercadolibre.com" + endpoint)
                .headers(h -> h.setBearerAuth(token)).retrieve().body(JsonNode.class));
    }

    private JsonNode post(String endpoint, Object body) {
        return ejecutar(token -> restClient.post().uri("https://api.mercadolibre.com" + endpoint)
                .headers(h -> h.setBearerAuth(token)).contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().body(JsonNode.class));
    }

    private JsonNode ejecutar(java.util.function.Function<String, JsonNode> llamada) {
        try {
            JsonNode respuesta = llamada.apply(tokenService.obtenerAccessToken());
            return respuesta == null ? objectMapper.createObjectNode() : respuesta;
        } catch (RestClientResponseException e) {
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                tokenService.invalidarAccessToken();
                try { return llamada.apply(tokenService.obtenerAccessToken()); }
                catch (RestClientResponseException renovado) { throw errorApi(renovado); }
            }
            throw errorApi(e);
        }
    }

    private IllegalArgumentException errorApi(RestClientResponseException e) {
        try {
            JsonNode body = objectMapper.readTree(e.getResponseBodyAsString());
            List<String> mensajes = new ArrayList<>();
            if (body.path("errors").isArray()) body.path("errors").forEach(x -> mensajes.add(x.path("message").asText(x.path("code").asText())));
            if (body.path("cause").isArray()) body.path("cause").forEach(x -> mensajes.add(x.path("message").asText(x.path("code").asText())));
            String mensaje = mensajes.isEmpty() ? body.path("message").asText(body.path("error").asText("Error de Mercado Libre")) : String.join(". ", mensajes);
            return new IllegalArgumentException(mensaje);
        } catch (Exception ignored) {
            return new IllegalArgumentException("Mercado Libre rechazó la operación: " + e.getStatusCode().value());
        }
    }

    private void validarNombre(String nombre) {
        String limpio = limpiarNombre(nombre);
        if (limpio.isBlank()) throw new IllegalArgumentException("Ingrese el nombre de la guía");
        if (limpio.length() > 60) throw new IllegalArgumentException("El nombre de la guía no puede superar 60 caracteres");
        if (!limpio.matches("[\\p{L}\\p{N} ]+")) throw new IllegalArgumentException("El nombre solo puede contener letras, números y espacios");
    }

    private String limpiarNombre(String nombre) { return nombre == null ? "" : nombre.trim().replaceAll("\\s+", " "); }
    private String normalizarTipoMedida(String tipo) { return List.of("BODY_MEASURE", "CLOTHING_MEASURE").contains(tipo) ? tipo : "BODY_MEASURE"; }
    private String dominioCorto(String dominio) { return dominio == null ? "" : dominio.replaceFirst("^[A-Z]{3}-", ""); }
    private boolean texto(String valor) { return valor != null && !valor.isBlank(); }
    private String normalizar(String valor) { return valor == null ? "" : java.text.Normalizer.normalize(valor, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "").replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT); }
    private String normalizarFila(String chartId, String fila) { return fila.contains(":") ? fila : chartId + ":" + fila; }

    public record Valor(String id, String nombre) {
        public String getId() { return id; }
        public String getNombre() { return nombre; }
    }
    public record Campo(String id, String nombre, String tipo, List<Valor> valores, boolean obligatorio,
                        boolean candidatoPrincipal, boolean multivalor, String unidad, String jerarquia, Set<String> tags) {
        public boolean soloLectura() { return tags.contains("read_only"); }
        public String getId() { return id; }
        public String getNombre() { return nombre; }
        public String getTipo() { return tipo; }
        public List<Valor> getValores() { return valores; }
        public boolean isObligatorio() { return obligatorio; }
        public boolean isCandidatoPrincipal() { return candidatoPrincipal; }
        public boolean isMultivalor() { return multivalor; }
        public String getUnidad() { return unidad; }
        public String getJerarquia() { return jerarquia; }
        public Set<String> getTags() { return tags; }
        public boolean isSoloLectura() { return soloLectura(); }
    }
    public record Seleccion(String id, String nombre, BigDecimal numero, String unidad) {
        public String getId() { return id; }
        public String getNombre() { return nombre; }
        public BigDecimal getNumero() { return numero; }
        public String getUnidad() { return unidad; }
    }
    public record Contexto(Producto producto, String categoriaNombre, String dominioCompleto, String dominioCorto,
                            List<Campo> filtros) {
        public Producto getProducto() { return producto; }
        public String getCategoriaNombre() { return categoriaNombre; }
        public String getDominioCompleto() { return dominioCompleto; }
        public String getDominioCorto() { return dominioCorto; }
        public List<Campo> getFiltros() { return filtros; }
    }
    public record ConstructorGuia(Contexto contexto, String nombre, String tipoMedida, Map<String, Seleccion> filtros,
                                  List<Campo> camposGenerales, List<Campo> camposFila, int maxFilas) {
        public Contexto getContexto() { return contexto; }
        public String getNombre() { return nombre; }
        public String getTipoMedida() { return tipoMedida; }
        public Map<String, Seleccion> getFiltros() { return filtros; }
        public List<Campo> getCamposGenerales() { return camposGenerales; }
        public List<Campo> getCamposFila() { return camposFila; }
        public int getMaxFilas() { return maxFilas; }
    }
    public record GuiaResumen(String id, String nombre, String tipo, String dominio, String atributoPrincipal, int cantidadFilas) {
        public String getId() { return id; }
        public String getNombre() { return nombre; }
        public String getTipo() { return tipo; }
        public String getDominio() { return dominio; }
        public String getAtributoPrincipal() { return atributoPrincipal; }
        public int getCantidadFilas() { return cantidadFilas; }
    }
    public record FilaGuia(String id, Map<String, String> atributos) {
        public String getId() { return id; }
        public Map<String, String> getAtributos() { return atributos; }
    }
    public record Guia(String id, String nombre, String dominio, String tipo, Long sellerId, String atributoPrincipal,
                       Map<String, String> atributos, List<FilaGuia> filas) {
        public String getId() { return id; }
        public String getNombre() { return nombre; }
        public String getDominio() { return dominio; }
        public String getTipo() { return tipo; }
        public Long getSellerId() { return sellerId; }
        public String getAtributoPrincipal() { return atributoPrincipal; }
        public Map<String, String> getAtributos() { return atributos; }
        public List<FilaGuia> getFilas() { return filas; }
    }
}

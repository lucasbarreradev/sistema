package com.sistema.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sistema.model.*;
import com.sistema.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class WebhookVentasService {
    private final RestClient restClient = RestClient.create();
    private final ObjectMapper objectMapper;
    private final MercadoLibreTokenService tokenService;
    private final OrdenCanalProcesadaRepository ordenRepository;
    private final PublicacionCanalRepository publicacionRepository;
    private final ProductoRepository productoRepository;
    private final ProductoVarianteRepository varianteRepository;
    private final MovimientoInventarioService movimientoService;
    private final TiendanubeCredencialesService tiendaNubeCredenciales;

    public WebhookVentasService(
            ObjectMapper objectMapper,
            MercadoLibreTokenService tokenService,
            OrdenCanalProcesadaRepository ordenRepository,
            PublicacionCanalRepository publicacionRepository,
            ProductoRepository productoRepository,
            ProductoVarianteRepository varianteRepository,
            MovimientoInventarioService movimientoService,
            TiendanubeCredencialesService tiendaNubeCredenciales) {
        this.objectMapper = objectMapper;
        this.tokenService = tokenService;
        this.ordenRepository = ordenRepository;
        this.publicacionRepository = publicacionRepository;
        this.productoRepository = productoRepository;
        this.varianteRepository = varianteRepository;
        this.movimientoService = movimientoService;
        this.tiendaNubeCredenciales = tiendaNubeCredenciales;
    }

    @Transactional
    public void procesarMercadoLibre(String payload) {
        JsonNode aviso = json(payload);
        String topic = aviso.path("topic").asText();
        String resource = aviso.path("resource").asText();
        if (!("orders_v2".equals(topic) || "orders".equals(topic)) || !resource.matches("/orders/\\d+")) return;
        JsonNode orden = getMercadoLibre(resource);
        long usuarioAvisado = aviso.path("user_id").asLong(0);
        long vendedor = orden.path("seller").path("id").asLong(0);
        if (usuarioAvisado > 0 && vendedor > 0 && usuarioAvisado != vendedor) {
            throw new IllegalArgumentException("La orden de Mercado Libre no pertenece al usuario notificado");
        }
        if (!"paid".equalsIgnoreCase(orden.path("status").asText())
                || "cancelled".equalsIgnoreCase(orden.path("status").asText())) return;
        String ordenId = orden.path("id").asText(resource.substring(resource.lastIndexOf('/') + 1));
        List<LineaExterna> lineas = new ArrayList<>();
        for (JsonNode linea : orden.path("order_items")) {
            JsonNode item = linea.path("item");
            lineas.add(new LineaExterna(item.path("id").asText(), item.path("variation_id").asText(null),
                    item.path("seller_sku").asText(null), linea.path("quantity").asInt(0)));
        }
        procesarOrden(CanalVenta.MERCADO_LIBRE, ordenId, lineas);
    }

    @Transactional
    public void procesarWooCommerce(String payload) {
        JsonNode raiz = json(payload);
        JsonNode orden = raiz.has("order") ? raiz.path("order") : raiz;
        String estado = orden.path("status").asText();
        if (!Set.of("processing", "completed", "on-hold").contains(estado.toLowerCase(Locale.ROOT))) return;
        String ordenId = orden.path("id").asText(orden.path("order_number").asText());
        if (ordenId.isBlank()) throw new IllegalArgumentException("WooCommerce no informó el ID de la orden");
        List<LineaExterna> lineas = new ArrayList<>();
        for (JsonNode linea : orden.path("line_items")) {
            lineas.add(new LineaExterna(linea.path("product_id").asText(),
                    linea.path("variation_id").asLong(0) == 0 ? null : linea.path("variation_id").asText(),
                    linea.path("sku").asText(null), linea.path("quantity").asInt(0)));
        }
        procesarOrden(CanalVenta.WOOCOMMERCE, ordenId, lineas);
    }

    @Transactional
    public void procesarTiendaNube(String payload) {
        JsonNode aviso = json(payload);
        TiendanubeCredencialesService.Credenciales c = tiendaNubeCredenciales.obtener();
        String evento = aviso.path("event").asText();
        if (!("order/paid".equals(evento) || "order/created".equals(evento))) return;
        String storeIdAvisado = aviso.path("store_id").asText();
        if (!storeIdAvisado.isBlank() && !c.storeId().equals(storeIdAvisado)) {
            throw new IllegalArgumentException("El webhook no pertenece a la Tiendanube configurada");
        }
        String ordenId = aviso.path("id").asText();
        if (ordenId.isBlank()) throw new IllegalArgumentException("Tiendanube no informó el ID de la orden");
        JsonNode orden = restClient.get()
                .uri("https://api.tiendanube.com/v1/" + c.storeId() + "/orders/" + ordenId)
                .headers(h -> { h.setBearerAuth(c.token()); h.set("User-Agent", c.userAgent()); })
                .retrieve().body(JsonNode.class);
        if (orden == null || !"paid".equalsIgnoreCase(orden.path("payment_status").asText())
                || "cancelled".equalsIgnoreCase(orden.path("status").asText())) return;
        List<LineaExterna> lineas = new ArrayList<>();
        for (JsonNode linea : orden.path("products")) {
            lineas.add(new LineaExterna(linea.path("product_id").asText(),
                    linea.path("variant_id").asText(null), linea.path("sku").asText(null),
                    linea.path("quantity").asInt(0)));
        }
        procesarOrden(CanalVenta.TIENDANUBE, ordenId, lineas);
    }

    private void procesarOrden(CanalVenta canal, String ordenId, List<LineaExterna> lineas) {
        if (ordenRepository.existsByCanalAndOrdenId(canal, ordenId)) return;
        if (lineas.isEmpty()) throw new IllegalArgumentException("La orden " + ordenId + " no contiene productos");

        Map<String, LineaResuelta> acumuladas = new LinkedHashMap<>();
        for (LineaExterna linea : lineas) {
            if (linea.cantidad() <= 0) continue;
            LineaResuelta resuelta = resolver(canal, linea);
            String clave = resuelta.productoId() + ":" + Objects.toString(resuelta.varianteId(), "");
            acumuladas.merge(clave, resuelta,
                    (a, b) -> new LineaResuelta(a.productoId(), a.varianteId(), a.cantidad() + b.cantidad()));
        }
        if (acumuladas.isEmpty()) throw new IllegalArgumentException("No se pudieron vincular productos de la orden " + ordenId);

        for (LineaResuelta linea : acumuladas.values()) {
            movimientoService.registrarVentaExterna(linea.productoId(), linea.varianteId(), linea.cantidad(),
                    "Venta " + canal.getDescripcion() + " / orden " + ordenId, canal);
        }
        OrdenCanalProcesada procesada = new OrdenCanalProcesada();
        procesada.setCanal(canal);
        procesada.setOrdenId(ordenId);
        procesada.setProcesadaEn(LocalDateTime.now());
        ordenRepository.save(procesada);
    }

    private LineaResuelta resolver(CanalVenta canal, LineaExterna linea) {
        Optional<ProductoVariante> variante = Optional.empty();
        if (linea.varianteExternaId() != null && !linea.varianteExternaId().isBlank()) {
            variante = switch (canal) {
                case MERCADO_LIBRE -> varianteRepository.findByMercadoLibreVariationId(linea.varianteExternaId());
                case WOOCOMMERCE -> varianteRepository.findByWooCommerceVariationId(linea.varianteExternaId());
                case TIENDANUBE -> varianteRepository.findByTiendaNubeVariationId(linea.varianteExternaId());
            };
        }
        if (variante.isEmpty() && canal == CanalVenta.MERCADO_LIBRE
                && linea.productoExternoId() != null) {
            variante = varianteRepository.findByMercadoLibreItemId(linea.productoExternoId());
        }
        if (variante.isEmpty() && linea.sku() != null && !linea.sku().isBlank()) {
            variante = varianteRepository.findBySkuIgnoreCase(linea.sku());
        }
        if (variante.isPresent()) {
            ProductoVariante v = variante.get();
            return new LineaResuelta(v.getProducto().getId(), v.getId(), linea.cantidad());
        }

        Producto producto = null;
        if (linea.productoExternoId() != null && !linea.productoExternoId().isBlank()) {
            producto = publicacionRepository.findByCanalAndIdExterno(canal, linea.productoExternoId())
                    .map(PublicacionCanal::getProducto).orElse(null);
        }
        if (producto == null && linea.sku() != null && !linea.sku().isBlank()) {
            producto = productoRepository.findBySkuIgnoreCase(linea.sku()).orElse(null);
        }
        if (producto == null) {
            throw new IllegalArgumentException("No se encontró en el sistema el producto externo "
                    + linea.productoExternoId() + " (SKU " + linea.sku() + ")");
        }
        List<ProductoVariante> presentaciones =
                varianteRepository.findByProductoIdOrderByNombreAsc(producto.getId());
        if (presentaciones.size() == 1) {
            return new LineaResuelta(producto.getId(), presentaciones.get(0).getId(), linea.cantidad());
        }
        if (!presentaciones.isEmpty()) {
            throw new IllegalArgumentException("La orden no identificó la presentación del producto " + producto.getSku());
        }
        return new LineaResuelta(producto.getId(), null, linea.cantidad());
    }

    private JsonNode getMercadoLibre(String resource) {
        try {
            return getMercadoLibre(resource, tokenService.obtenerAccessToken());
        } catch (RestClientResponseException e) {
            if (e.getStatusCode() != HttpStatus.UNAUTHORIZED) throw e;
            tokenService.invalidarAccessToken();
            return getMercadoLibre(resource, tokenService.obtenerAccessToken());
        }
    }

    private JsonNode getMercadoLibre(String resource, String token) {
        return restClient.get().uri("https://api.mercadolibre.com" + resource)
                .headers(h -> h.setBearerAuth(token)).retrieve().body(JsonNode.class);
    }

    private JsonNode json(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (Exception e) {
            throw new IllegalArgumentException("El webhook no contiene JSON válido", e);
        }
    }

    private record LineaExterna(String productoExternoId, String varianteExternaId, String sku, int cantidad) {}
    private record LineaResuelta(Long productoId, Long varianteId, int cantidad) {}
}

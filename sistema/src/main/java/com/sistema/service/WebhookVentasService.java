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

import java.math.BigDecimal;
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
    private final VentaRepository ventaRepository;

    public WebhookVentasService(
            ObjectMapper objectMapper,
            MercadoLibreTokenService tokenService,
            OrdenCanalProcesadaRepository ordenRepository,
            PublicacionCanalRepository publicacionRepository,
            ProductoRepository productoRepository,
            ProductoVarianteRepository varianteRepository,
            MovimientoInventarioService movimientoService,
            TiendanubeCredencialesService tiendaNubeCredenciales,
            VentaRepository ventaRepository) {
        this.objectMapper = objectMapper;
        this.tokenService = tokenService;
        this.ordenRepository = ordenRepository;
        this.publicacionRepository = publicacionRepository;
        this.productoRepository = productoRepository;
        this.varianteRepository = varianteRepository;
        this.movimientoService = movimientoService;
        this.tiendaNubeCredenciales = tiendaNubeCredenciales;
        this.ventaRepository = ventaRepository;
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
                    item.path("seller_sku").asText(null), linea.path("quantity").asInt(0),
                    decimal(linea, "unit_price")));
        }
        JsonNode comprador = orden.path("buyer");
        procesarOrden(CanalVenta.MERCADO_LIBRE, ordenId, lineas,
                new CompradorExterno(nombre(comprador, "first_name", "last_name",
                                comprador.path("nickname").asText(null)),
                        texto(comprador, "billing_info", "doc_number"), null),
                primero(decimal(orden, "paid_amount"), decimal(orden, "total_amount")));
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
                    linea.path("sku").asText(null), linea.path("quantity").asInt(0),
                    precioWoo(linea)));
        }
        JsonNode comprador = orden.path("billing");
        procesarOrden(CanalVenta.WOOCOMMERCE, ordenId, lineas,
                new CompradorExterno(nombre(comprador, "first_name", "last_name", null),
                        comprador.path("dni").asText(null), comprador.path("email").asText(null)),
                decimal(orden, "total"));
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
                    linea.path("quantity").asInt(0), decimal(linea, "price")));
        }
        JsonNode comprador = orden.path("customer");
        procesarOrden(CanalVenta.TIENDANUBE, ordenId, lineas,
                new CompradorExterno(nombre(comprador, "name", "last_name", null),
                        comprador.path("identification").asText(null),
                        orden.path("contact_email").asText(comprador.path("email").asText(null))),
                decimal(orden, "total"));
    }

    private void procesarOrden(CanalVenta canal, String ordenId, List<LineaExterna> lineas,
                               CompradorExterno comprador, BigDecimal totalOrden) {
        if (ordenRepository.existsByCanalAndOrdenId(canal, ordenId)) return;
        if (ventaRepository.existsByCanalVentaAndOrdenExternaId(canal, ordenId)) return;
        if (lineas.isEmpty()) throw new IllegalArgumentException("La orden " + ordenId + " no contiene productos");

        Map<String, LineaResuelta> acumuladas = new LinkedHashMap<>();
        for (LineaExterna linea : lineas) {
            if (linea.cantidad() <= 0) continue;
            LineaResuelta resuelta = resolver(canal, linea);
            String clave = resuelta.productoId() + ":" + Objects.toString(resuelta.varianteId(), "");
            acumuladas.merge(clave, resuelta,
                    (a, b) -> new LineaResuelta(a.productoId(), a.varianteId(),
                            a.cantidad() + b.cantidad(), a.precioUnitario()));
        }
        if (acumuladas.isEmpty()) throw new IllegalArgumentException("No se pudieron vincular productos de la orden " + ordenId);

        for (LineaResuelta linea : acumuladas.values()) {
            movimientoService.registrarVentaExterna(linea.productoId(), linea.varianteId(), linea.cantidad(),
                    "Venta " + canal.getDescripcion() + " / orden " + ordenId, canal);
        }
        Venta venta = crearVentaExterna(canal, ordenId, acumuladas.values(), comprador, totalOrden);
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
            return new LineaResuelta(v.getProducto().getId(), v.getId(), linea.cantidad(), linea.precioUnitario());
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
            return new LineaResuelta(producto.getId(), presentaciones.get(0).getId(), linea.cantidad(), linea.precioUnitario());
        }
        if (!presentaciones.isEmpty()) {
            throw new IllegalArgumentException("La orden no identificó la presentación del producto " + producto.getSku());
        }
        return new LineaResuelta(producto.getId(), null, linea.cantidad(), linea.precioUnitario());
    }

    private Venta crearVentaExterna(CanalVenta canal, String ordenId,
                                     Collection<LineaResuelta> lineas,
                                     CompradorExterno comprador,
                                     BigDecimal totalOrden) {
        Venta venta = new Venta();
        venta.setCodigo("EXT-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        venta.setOrigen(switch (canal) {
            case MERCADO_LIBRE -> Venta.Origen.MERCADO_LIBRE;
            case WOOCOMMERCE -> Venta.Origen.WOOCOMMERCE;
            case TIENDANUBE -> Venta.Origen.TIENDANUBE;
        });
        venta.setCanalVenta(canal);
        venta.setOrdenExternaId(ordenId);
        venta.setClienteNombreExterno(limpiar(comprador == null ? null : comprador.nombre()));
        venta.setClienteDocumentoExterno(soloDigitos(comprador == null ? null : comprador.documento()));
        venta.setClienteEmailExterno(limpiar(comprador == null ? null : comprador.email()));
        venta.setFormaPago(FormaPago.TARJETA);
        venta.setEstado(Venta.Estado.COMPLETADA);
        venta.setFechaVenta(LocalDateTime.now());
        venta.setNota("Orden " + canal.getDescripcion() + " " + ordenId);

        for (LineaResuelta linea : lineas) {
            Producto producto = productoRepository.findById(linea.productoId())
                    .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado"));
            ProductoVariante variante = linea.varianteId() == null ? null
                    : varianteRepository.findById(linea.varianteId()).orElse(null);
            BigDecimal precio = linea.precioUnitario();
            if (precio == null || precio.signum() < 0) {
                precio = variante == null ? producto.getPrecioContado() : variante.getPrecioContado();
            }
            if (precio == null) precio = BigDecimal.ZERO;

            VentaItem item = new VentaItem();
            item.setProducto(producto);
            item.setVariante(variante);
            item.setCantidad(linea.cantidad());
            item.setPrecioUnitario(precio);
            item.setCostoUnitario(variante != null && variante.getPrecioCompra() != null
                    ? variante.getPrecioCompra()
                    : producto.getPrecioCompra() == null ? BigDecimal.ZERO : producto.getPrecioCompra());
            item.setDescuentoPct(BigDecimal.ZERO);
            item.setAlicuotaIva(producto.getTipoIva() == null
                    ? new BigDecimal("21.00") : producto.getTipoIva().getPorcentaje());
            item.calcularSubtotal();
            venta.agregarItem(item);
        }
        ajustarTotalExterno(venta, totalOrden);
        venta.calcularTotales();
        return ventaRepository.save(venta);
    }

    private void ajustarTotalExterno(Venta venta, BigDecimal totalOrden) {
        if (totalOrden == null || totalOrden.signum() <= 0 || venta.getItems().isEmpty()) return;
        BigDecimal actual = venta.getItems().stream().map(VentaItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (actual.signum() <= 0 || actual.compareTo(totalOrden) == 0) return;
        BigDecimal factor = totalOrden.divide(actual, 12, java.math.RoundingMode.HALF_UP);
        BigDecimal acumulado = BigDecimal.ZERO;
        for (int i = 0; i < venta.getItems().size(); i++) {
            VentaItem item = venta.getItems().get(i);
            BigDecimal subtotal = i == venta.getItems().size() - 1
                    ? totalOrden.subtract(acumulado)
                    : item.getSubtotal().multiply(factor).setScale(2, java.math.RoundingMode.HALF_UP);
            item.setSubtotal(subtotal);
            item.setPrecioUnitario(subtotal.divide(BigDecimal.valueOf(item.getCantidad()),
                    2, java.math.RoundingMode.HALF_UP));
            acumulado = acumulado.add(subtotal);
        }
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

    private BigDecimal precioWoo(JsonNode linea) {
        BigDecimal total = decimal(linea, "total");
        int cantidad = linea.path("quantity").asInt(0);
        if (total != null && cantidad > 0) return total.divide(BigDecimal.valueOf(cantidad), 2, java.math.RoundingMode.HALF_UP);
        return decimal(linea, "price");
    }

    private BigDecimal decimal(JsonNode nodo, String campo) {
        String valor = nodo.path(campo).asText();
        try { return valor == null || valor.isBlank() ? null : new BigDecimal(valor); }
        catch (NumberFormatException e) { return null; }
    }

    private BigDecimal primero(BigDecimal principal, BigDecimal respaldo) {
        return principal == null ? respaldo : principal;
    }

    private String nombre(JsonNode nodo, String campoNombre, String campoApellido, String respaldo) {
        String nombre = nodo.path(campoNombre).asText("").trim();
        String apellido = nodo.path(campoApellido).asText("").trim();
        String completo = (nombre + " " + apellido).trim();
        return completo.isBlank() ? respaldo : completo;
    }

    private String texto(JsonNode nodo, String objeto, String campo) {
        String valor = nodo.path(objeto).path(campo).asText(null);
        return limpiar(valor);
    }

    private String limpiar(String valor) { return valor == null || valor.isBlank() ? null : valor.trim(); }
    private String soloDigitos(String valor) {
        String limpio = valor == null ? null : valor.replaceAll("\\D", "");
        return limpio == null || limpio.isBlank() ? null : limpio;
    }

    private record CompradorExterno(String nombre, String documento, String email) {}
    private record LineaExterna(String productoExternoId, String varianteExternaId, String sku,
                                int cantidad, BigDecimal precioUnitario) {}
    private record LineaResuelta(Long productoId, Long varianteId, int cantidad,
                                 BigDecimal precioUnitario) {}
}

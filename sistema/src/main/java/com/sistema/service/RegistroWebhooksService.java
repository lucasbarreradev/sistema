package com.sistema.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import com.sistema.repository.TenantRepository;
import com.sistema.tenant.TenantContext;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RegistroWebhooksService {
    private static final Logger log = LoggerFactory.getLogger(RegistroWebhooksService.class);
    private final RestClient restClient = RestClient.create();
    private final WebhookSeguridadService seguridad;
    private final WooCommerceCredencialesService wooCredenciales;
    private final TiendanubeCredencialesService tiendaNubeCredenciales;
    private final TenantRepository tenantRepository;
    private final String publicBaseUrl;

    public RegistroWebhooksService(
            WebhookSeguridadService seguridad,
            WooCommerceCredencialesService wooCredenciales,
            TiendanubeCredencialesService tiendaNubeCredenciales,
            TenantRepository tenantRepository,
            @Value("${integraciones.public-base-url:}") String publicBaseUrl) {
        this.seguridad = seguridad;
        this.wooCredenciales = wooCredenciales;
        this.tiendaNubeCredenciales = tiendaNubeCredenciales;
        this.tenantRepository = tenantRepository;
        this.publicBaseUrl = publicBaseUrl;
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void registrarAlIniciar() {
        if (publicBaseUrl == null || publicBaseUrl.isBlank() || !seguridad.configurado()) {
            log.warn("Webhooks no registrados: falta PUBLIC_BASE_URL o INTEGRATIONS_WEBHOOK_SECRET");
            return;
        }
        tenantRepository.findAll().stream().filter(t -> Boolean.TRUE.equals(t.getActivo())).forEach(tenant -> {
            try (TenantContext.Scope ignored = TenantContext.use(tenant.getId())) {
                registrarWooCommerceSeguro();
                registrarTiendaNubeSeguro();
            }
        });
        log.info("Mercado Libre debe usar como URL de notificaciones: {}/webhooks/mercadolibre",
                limpiarUrl(publicBaseUrl));
    }

    @Async
    public void registrarWooCommerceSeguro() {
        if (!wooCredenciales.configurado()) return;
        try { registrarWooCommerce(); }
        catch (Exception e) { log.warn("No se pudieron registrar los webhooks de WooCommerce: {}", mensajeSeguro(e)); }
    }

    @Async
    public void registrarTiendaNubeSeguro() {
        if (!tiendaNubeCredenciales.configurado()) return;
        try { registrarTiendaNube(); }
        catch (Exception e) { log.warn("No se pudo registrar el webhook de Tiendanube: {}", mensajeSeguro(e)); }
    }

    public void registrarTiendaNubeAhora() {
        if (!tiendaNubeCredenciales.configurado()) {
            throw new IllegalStateException("Tiendanube no tiene una cuenta conectada");
        }
        try {
            registrarTiendaNube();
        } catch (RestClientResponseException e) {
            if (e.getStatusCode() == HttpStatus.FORBIDDEN) {
                throw new IllegalStateException(
                        "La cuenta quedó conectada, pero Tiendanube no permitió registrar las ventas. "
                                + "Habilite read_orders y write_products en el Portal de Socios, "
                                + "y luego desconecte y vuelva a conectar la tienda.");
            }
            throw e;
        }
    }

    private void registrarWooCommerce() {
        WooCommerceCredencialesService.Credenciales c = wooCredenciales.obtener();
        String endpoint = c.url() + "/wp-json/wc/v3/webhooks";
        JsonNode existentes = restClient.get().uri(endpoint + "?per_page=100")
                .headers(h -> h.setBasicAuth(c.key(), c.secret())).retrieve().body(JsonNode.class);
        for (String topic : List.of("order.created", "order.updated")) {
            String nombre = "Sistema Stock - " + topic;
            String id = buscarId(existentes, nombre);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("name", nombre);
            body.put("status", "active");
            body.put("topic", topic);
            body.put("delivery_url", limpiarUrl(publicBaseUrl) + "/webhooks/woocommerce");
            body.put("secret", seguridad.secretoRegistro());
            if (id == null) {
                restClient.post().uri(endpoint).headers(h -> h.setBasicAuth(c.key(), c.secret()))
                        .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toBodilessEntity();
            } else {
                restClient.put().uri(endpoint + "/" + id).headers(h -> h.setBasicAuth(c.key(), c.secret()))
                        .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toBodilessEntity();
            }
        }
        log.info("Webhooks de órdenes de WooCommerce registrados");
    }

    private void registrarTiendaNube() {
        TiendanubeCredencialesService.Credenciales c = tiendaNubeCredenciales.obtener();
        String endpoint = "https://api.tiendanube.com/v1/" + c.storeId() + "/webhooks";
        JsonNode existentes = restClient.get().uri(endpoint)
                .headers(h -> { h.setBearerAuth(c.token()); h.set("User-Agent", c.userAgent()); })
                .retrieve().body(JsonNode.class);
        for (String evento : List.of("order/created", "order/paid")) {
            String id = buscarIdPorEvento(existentes, evento);
            Map<String, Object> body = Map.of(
                    "event", evento,
                    "url", limpiarUrl(publicBaseUrl) + "/webhooks/tiendanube");
            if (id == null) {
                restClient.post().uri(endpoint)
                        .headers(h -> { h.setBearerAuth(c.token()); h.set("User-Agent", c.userAgent()); })
                        .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toBodilessEntity();
            } else {
                restClient.put().uri(endpoint + "/" + id)
                        .headers(h -> { h.setBearerAuth(c.token()); h.set("User-Agent", c.userAgent()); })
                        .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toBodilessEntity();
            }
        }
        log.info("Webhooks order/created y order/paid de Tiendanube registrados");
    }

    private String buscarId(JsonNode lista, String nombre) {
        if (lista != null && lista.isArray()) {
            for (JsonNode item : lista) if (nombre.equals(item.path("name").asText())) return item.path("id").asText();
        }
        return null;
    }

    private String buscarIdPorEvento(JsonNode lista, String evento) {
        if (lista != null && lista.isArray()) {
            for (JsonNode item : lista) if (evento.equals(item.path("event").asText())) return item.path("id").asText();
        }
        return null;
    }

    private String limpiarUrl(String valor) {
        return valor == null ? "" : valor.replaceAll("/+$", "");
    }

    private String mensajeSeguro(Exception e) {
        String mensaje = e.getMessage();
        if (mensaje == null || mensaje.isBlank()) mensaje = e.getClass().getSimpleName();
        return mensaje.length() > 500 ? mensaje.substring(0, 500) : mensaje;
    }
}

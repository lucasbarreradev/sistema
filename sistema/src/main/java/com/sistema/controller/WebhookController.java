package com.sistema.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sistema.service.WebhookSeguridadService;
import com.sistema.service.WebhookVentasService;
import com.sistema.service.MercadoLibreTokenService;
import com.sistema.service.TiendanubeCredencialesService;
import com.sistema.service.WooCommerceCredencialesService;
import com.sistema.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class WebhookController {
    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);
    private final WebhookVentasService ventasService;
    private final WebhookSeguridadService seguridadService;
    private final ObjectMapper objectMapper;
    private final MercadoLibreTokenService mercadoLibreTokenService;
    private final WooCommerceCredencialesService wooCredenciales;
    private final TiendanubeCredencialesService tiendaNubeCredenciales;

    public WebhookController(WebhookVentasService ventasService,
                             WebhookSeguridadService seguridadService,
                             ObjectMapper objectMapper,
                             MercadoLibreTokenService mercadoLibreTokenService,
                             WooCommerceCredencialesService wooCredenciales,
                             TiendanubeCredencialesService tiendaNubeCredenciales) {
        this.ventasService = ventasService;
        this.seguridadService = seguridadService;
        this.objectMapper = objectMapper;
        this.mercadoLibreTokenService = mercadoLibreTokenService;
        this.wooCredenciales = wooCredenciales;
        this.tiendaNubeCredenciales = tiendaNubeCredenciales;
    }

    @PostMapping({"/webhooks/mercadolibre", "/canales/mercadolibre/callback"})
    public ResponseEntity<String> mercadoLibre(@RequestBody String payload) {
        try {
            JsonNode aviso = objectMapper.readTree(payload);
            if (!seguridadService.aplicacionMercadoLibreValida(aviso.path("application_id").asText())) {
                return ResponseEntity.status(401).body("Aplicación inválida");
            }
            Long tenantId = mercadoLibreTokenService.resolverTenantPorUsuario(
                    aviso.path("user_id").canConvertToLong() ? aviso.path("user_id").asLong() : null);
            if (tenantId == null) return ResponseEntity.status(404).body("Cuenta de Mercado Libre no vinculada");
            try (TenantContext.Scope ignored = TenantContext.use(tenantId)) {
                ventasService.procesarMercadoLibre(payload);
            }
            return ResponseEntity.ok("ok");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            log.error("Error procesando webhook de Mercado Libre", e);
            return ResponseEntity.internalServerError().body("No se pudo procesar");
        }
    }

    @PostMapping("/webhooks/woocommerce")
    public ResponseEntity<String> wooCommerce(
            @RequestHeader(value = "X-WC-Webhook-Signature", required = false) String firma,
            @RequestHeader(value = "X-WC-Webhook-Topic", required = false) String topic,
            @RequestHeader(value = "X-WC-Webhook-Source", required = false) String source,
            @RequestBody String payload) {
        if (topic != null && !topic.startsWith("order.")) return ResponseEntity.ok("ignorado");
        if (!seguridadService.firmaWooValida(payload, firma)) {
            return ResponseEntity.status(401).body("Firma inválida");
        }
        try {
            Long tenantId = wooCredenciales.resolverTenantPorUrl(source);
            if (tenantId == null) return ResponseEntity.status(404).body("Tienda WooCommerce no vinculada");
            try (TenantContext.Scope ignored = TenantContext.use(tenantId)) {
                ventasService.procesarWooCommerce(payload);
            }
            return ResponseEntity.ok("ok");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            log.error("Error procesando webhook de WooCommerce", e);
            return ResponseEntity.internalServerError().body("No se pudo procesar");
        }
    }

    @PostMapping("/webhooks/tiendanube")
    public ResponseEntity<String> tiendaNube(
            @RequestHeader(value = "x-linkedstore-hmac-sha256", required = false) String firma,
            @RequestBody String payload) {
        if (!seguridadService.firmaTiendaNubeValida(payload, firma)) {
            return ResponseEntity.status(401).body("Firma inválida");
        }
        try {
            JsonNode aviso = objectMapper.readTree(payload);
            Long tenantId = tiendaNubeCredenciales.resolverTenantPorStoreId(aviso.path("store_id").asText());
            if (tenantId == null) return ResponseEntity.status(404).body("Tiendanube no vinculada");
            try (TenantContext.Scope ignored = TenantContext.use(tenantId)) {
                ventasService.procesarTiendaNube(payload);
            }
            return ResponseEntity.ok("ok");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            log.error("Error procesando webhook de Tiendanube", e);
            return ResponseEntity.internalServerError().body("No se pudo procesar");
        }
    }
}

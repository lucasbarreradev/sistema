package com.sistema.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sistema.model.CanalVenta;
import com.sistema.model.ConexionCanalPendiente;
import com.sistema.repository.ConexionCanalPendienteRepository;
import com.sistema.service.RegistroWebhooksService;
import com.sistema.service.WooCommerceCredencialesService;
import com.sistema.tenant.TenantContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Controller
@RequestMapping("/canales/woocommerce")
@PreAuthorize("hasRole('ADMIN')")
public class WooCommerceConexionController {
    private final WooCommerceCredencialesService credenciales;
    private final ConexionCanalPendienteRepository pendientes;
    private final RegistroWebhooksService webhooks;
    private final ObjectMapper objectMapper;
    private final String publicBaseUrl;

    public WooCommerceConexionController(WooCommerceCredencialesService credenciales,
            ConexionCanalPendienteRepository pendientes, RegistroWebhooksService webhooks,
            ObjectMapper objectMapper,
            @Value("${integraciones.public-base-url:}") String publicBaseUrl) {
        this.credenciales = credenciales;
        this.pendientes = pendientes;
        this.webhooks = webhooks;
        this.objectMapper = objectMapper;
        this.publicBaseUrl = publicBaseUrl == null ? "" : publicBaseUrl.replaceAll("/+$", "");
    }

    @PostMapping("/conectar")
    public ResponseEntity<Void> conectar(@RequestParam String tiendaUrl) {
        if (!credenciales.conexionDisponible() || publicBaseUrl.isBlank()) {
            throw new IllegalStateException("Configure PUBLIC_BASE_URL y una clave de cifrado válida antes de conectar WooCommerce");
        }
        String url = credenciales.validarUrl(tiendaUrl);
        ConexionCanalPendiente pendiente = new ConexionCanalPendiente();
        pendiente.setId(UUID.randomUUID().toString());
        pendiente.setTenantId(TenantContext.require());
        pendiente.setCanal(CanalVenta.WOOCOMMERCE);
        pendiente.setDato(url);
        pendiente.setVenceEn(Instant.now().plus(15, ChronoUnit.MINUTES));
        pendientes.save(pendiente);
        String retorno = publicBaseUrl + "/canales/woocommerce/retorno";
        String callback = publicBaseUrl + "/canales/woocommerce/callback";
        URI autorizacion = UriComponentsBuilder.fromUriString(url + "/wc-auth/v1/authorize")
                .queryParam("app_name", "Sistema de stock")
                .queryParam("scope", "read_write")
                .queryParam("user_id", pendiente.getId())
                .queryParam("return_url", retorno)
                .queryParam("callback_url", callback)
                .build().encode().toUri();
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, autorizacion.toString()).build();
    }

    @PostMapping("/callback")
    @PreAuthorize("permitAll()")
    @ResponseBody
    public ResponseEntity<String> callback(@RequestBody String payload) {
        try {
            JsonNode datos = objectMapper.readTree(payload);
            String id = datos.path("user_id").asText();
            ConexionCanalPendiente pendiente = pendientes.findById(id)
                    .filter(p -> p.getCanal() == CanalVenta.WOOCOMMERCE)
                    .filter(p -> p.getVenceEn() != null && p.getVenceEn().isAfter(Instant.now()))
                    .orElseThrow(() -> new IllegalArgumentException("La solicitud de conexión venció o no existe"));
            if (!"read_write".equals(datos.path("key_permissions").asText())) {
                throw new IllegalArgumentException("WooCommerce no concedió permisos de lectura y escritura");
            }
            try (TenantContext.Scope ignored = TenantContext.use(pendiente.getTenantId())) {
                credenciales.vincular(pendiente.getDato(), datos.path("consumer_key").asText(),
                        datos.path("consumer_secret").asText());
                pendientes.delete(pendiente);
                webhooks.registrarWooCommerceSeguro();
            }
            return ResponseEntity.ok("Cuenta conectada");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage() == null ? "No se pudo conectar WooCommerce" : e.getMessage());
        }
    }

    @GetMapping("/retorno")
    public String retorno(@RequestParam(defaultValue = "0") int success, RedirectAttributes ra) {
        if (success == 1 && credenciales.conectado()) {
            ra.addFlashAttribute("mensaje", "Cuenta de WooCommerce conectada correctamente");
        } else if (success == 1) {
            ra.addFlashAttribute("mensaje", "WooCommerce autorizó la conexión. La recepción de credenciales está terminando; actualice esta pantalla.");
        } else {
            ra.addFlashAttribute("error", "La conexión con WooCommerce fue cancelada o rechazada");
        }
        return "redirect:/canales";
    }

    @PostMapping("/desconectar")
    public String desconectar(RedirectAttributes ra) {
        credenciales.desconectar();
        ra.addFlashAttribute("mensaje", "Cuenta de WooCommerce desconectada");
        return "redirect:/canales";
    }
}

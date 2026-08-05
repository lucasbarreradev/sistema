package com.sistema.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sistema.model.CredencialTiendanube;
import com.sistema.repository.CredencialTiendanubeRepository;
import com.sistema.tenant.TenantContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Instant;

@Service
public class TiendanubeCredencialesService {
    private final RestClient restClient = RestClient.create();
    private final ObjectMapper objectMapper;
    private final CredencialTiendanubeRepository repository;
    private final CifradoCredencialesService cifrado;
    private final String storeIdInicial;
    private final String tokenInicial;
    private final String userAgent;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;

    public TiendanubeCredencialesService(CredencialTiendanubeRepository repository,
            CifradoCredencialesService cifrado,
            ObjectMapper objectMapper,
            @Value("${integraciones.tiendanube.store-id:}") String storeIdInicial,
            @Value("${integraciones.tiendanube.access-token:}") String tokenInicial,
            @Value("${integraciones.tiendanube.user-agent:SistemaStock/1.0}") String userAgent,
            @Value("${integraciones.tiendanube.client-id:}") String clientId,
            @Value("${integraciones.tiendanube.client-secret:}") String clientSecret,
            @Value("${integraciones.tiendanube.redirect-uri:}") String redirectUri,
            @Value("${integraciones.public-base-url:}") String publicBaseUrl) {
        this.repository = repository;
        this.cifrado = cifrado;
        this.objectMapper = objectMapper;
        this.storeIdInicial = storeIdInicial;
        this.tokenInicial = tokenInicial;
        this.userAgent = userAgent;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        String base = publicBaseUrl == null ? "" : publicBaseUrl.replaceAll("/+$", "");
        this.redirectUri = redirectUri == null || redirectUri.isBlank()
                ? (base.isBlank() ? "" : base + "/canales/tiendanube/callback")
                : redirectUri;
    }

    public boolean configurado() {
        long tenantId = TenantContext.require();
        return repository.existsByTenantId(tenantId)
                || (tenantId == 1L && !storeIdInicial.isBlank() && !tokenInicial.isBlank());
    }
    public boolean conectado() { return repository.existsByTenantId(TenantContext.require()); }
    public boolean aplicacionConfigurada() {
        return !clientId.isBlank() && !clientSecret.isBlank() && !redirectUri.isBlank() && cifrado.configurado();
    }
    public String getClientId() { return clientId; }
    public String getRedirectUri() { return redirectUri; }

    public String nombreCuentaConectada() {
        CredencialTiendanube credencial = repository.findByTenantId(TenantContext.require()).orElse(null);
        if (credencial == null) return "";
        String nombre = limpiar(credencial.getNombreCuenta());
        if (nombre.isBlank()) {
            try {
                nombre = consultarNombreTienda(credencial.getStoreId(),
                        cifrado.descifrar(credencial.getAccessTokenCifrado()));
            } catch (RuntimeException ignored) {
                nombre = "";
            }
            if (nombre.isBlank()) nombre = "Tienda " + credencial.getStoreId();
            credencial.setNombreCuenta(limitar(nombre));
            repository.save(credencial);
        }
        String respaldo = "Tienda " + credencial.getStoreId();
        return nombre.equalsIgnoreCase(respaldo)
                ? nombre : nombre + " (tienda " + credencial.getStoreId() + ")";
    }

    public Credenciales obtener() {
        long tenantId = TenantContext.require();
        return repository.findByTenantId(tenantId)
                .map(c -> new Credenciales(c.getStoreId(), cifrado.descifrar(c.getAccessTokenCifrado()), userAgent))
                .orElseGet(() -> {
                    if (!configurado()) throw new IllegalStateException("Tiendanube no tiene una cuenta conectada");
                    return new Credenciales(storeIdInicial, tokenInicial, userAgent);
                });
    }

    public String vincularConCodigo(String code) {
        if (!aplicacionConfigurada()) throw new IllegalStateException("La aplicación de Tiendanube no está configurada");
        if (code == null || code.isBlank()) throw new IllegalArgumentException("Tiendanube no devolvió el código de autorización");
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("code", code);
        form.add("grant_type", "authorization_code");
        form.add("redirect_uri", redirectUri);
        String cuerpo = restClient.post().uri("https://www.tiendanube.com/apps/authorize/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).accept(MediaType.APPLICATION_JSON)
                .body(form).retrieve().body(String.class);
        JsonNode respuesta;
        try {
            respuesta = objectMapper.readTree(cuerpo);
        } catch (Exception e) {
            throw new IllegalStateException("Tiendanube devolvió una respuesta que no se pudo interpretar");
        }
        if (respuesta.hasNonNull("error")) {
            String detalle = respuesta.path("error_description").asText(respuesta.path("error").asText());
            throw new IllegalStateException("Tiendanube rechazó la conexión: " + detalle);
        }
        String token = respuesta == null ? "" : respuesta.path("access_token").asText();
        String storeId = respuesta == null ? "" : respuesta.path("user_id").asText();
        if (token.isBlank() || storeId.isBlank()) throw new IllegalStateException("Tiendanube no devolvió la tienda y el token esperados");
        long tenantId = TenantContext.require();
        CredencialTiendanube credencial = repository.findByTenantId(tenantId).orElseGet(CredencialTiendanube::new);
        boolean mismaTienda = storeId.equals(credencial.getStoreId());
        credencial.setTenantId(tenantId);
        credencial.setStoreId(storeId);
        String nombreTienda;
        try {
            nombreTienda = consultarNombreTienda(storeId, token);
        } catch (RuntimeException ignored) {
            nombreTienda = "";
        }
        if (nombreTienda.isBlank() && mismaTienda) {
            nombreTienda = limpiar(credencial.getNombreCuenta());
        }
        if (nombreTienda.isBlank()) nombreTienda = "Tienda " + storeId;
        credencial.setNombreCuenta(limitar(nombreTienda));
        credencial.setAccessTokenCifrado(cifrado.cifrar(token));
        credencial.setActualizadoEn(Instant.now());
        repository.save(credencial);
        return storeId;
    }

    @Transactional
    public void desconectar() { repository.deleteByTenantId(TenantContext.require()); }
    public Long resolverTenantPorStoreId(String storeId) {
        return repository.findByStoreId(storeId).map(CredencialTiendanube::getTenantId)
                .orElseGet(() -> storeId != null && storeId.equals(storeIdInicial) && !storeIdInicial.isBlank() ? 1L : null);
    }

    private String consultarNombreTienda(String storeId, String token) {
        JsonNode tienda = restClient.get()
                .uri("https://api.tiendanube.com/v1/" + storeId + "/store")
                .headers(h -> {
                    h.setBearerAuth(token);
                    h.set("User-Agent", userAgent);
                })
                .retrieve().body(JsonNode.class);
        if (tienda == null) return "";
        JsonNode nombres = tienda.path("name");
        String idiomaPrincipal = limpiar(tienda.path("main_language").asText());
        if (!idiomaPrincipal.isBlank() && nombres.hasNonNull(idiomaPrincipal)) {
            String nombre = limpiar(nombres.path(idiomaPrincipal).asText());
            if (!nombre.isBlank()) return nombre;
        }
        if (nombres.hasNonNull("es")) {
            String nombre = limpiar(nombres.path("es").asText());
            if (!nombre.isBlank()) return nombre;
        }
        if (nombres.isTextual()) return limpiar(nombres.asText());
        if (nombres.fields().hasNext()) return limpiar(nombres.fields().next().getValue().asText());
        return limpiar(tienda.path("business_name").asText());
    }

    private String limpiar(String valor) {
        return valor == null ? "" : valor.trim();
    }

    private String limitar(String valor) {
        String limpio = limpiar(valor);
        return limpio.length() <= 255 ? limpio : limpio.substring(0, 255);
    }

    public record Credenciales(String storeId, String token, String userAgent) {}
}

package com.sistema.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.sistema.model.CredencialMercadoLibre;
import com.sistema.repository.CredencialMercadoLibreRepository;
import com.sistema.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.Set;

@Service
public class MercadoLibreTokenService {
    private static final Logger log = LoggerFactory.getLogger(MercadoLibreTokenService.class);
    private final RestClient restClient = RestClient.create();
    private final CredencialMercadoLibreRepository repository;
    private final CifradoCredencialesService cifrado;
    private final String clientId;
    private final String clientSecret;
    private final String accessTokenInicial;
    private final String refreshTokenInicial;

    public MercadoLibreTokenService(CredencialMercadoLibreRepository repository,
            CifradoCredencialesService cifrado,
            @Value("${integraciones.mercadolibre.client-id:}") String clientId,
            @Value("${integraciones.mercadolibre.client-secret:}") String clientSecret,
            @Value("${integraciones.mercadolibre.access-token:}") String accessTokenInicial,
            @Value("${integraciones.mercadolibre.refresh-token:}") String refreshTokenInicial) {
        this.repository = repository;
        this.cifrado = cifrado;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.accessTokenInicial = accessTokenInicial;
        this.refreshTokenInicial = refreshTokenInicial;
    }

    public boolean configurado() {
        if (clientId.isBlank() || clientSecret.isBlank() || !cifrado.configurado()) return false;
        long tenantId = TenantContext.require();
        return repository.existsByTenantId(tenantId) || (tenantId == 1L && !refreshTokenInicial.isBlank());
    }

    public boolean conectado() {
        return repository.existsByTenantId(TenantContext.require());
    }

    public boolean aplicacionConfigurada() {
        return !clientId.isBlank() && !clientSecret.isBlank() && cifrado.configurado();
    }

    public synchronized Long vincularConCodigo(String code, String redirectUri) {
        validarDatosAplicacion();
        if (code == null || code.isBlank()) throw new IllegalArgumentException("Mercado Libre no devolvió el código de autorización");
        if (redirectUri == null || redirectUri.isBlank()) throw new IllegalStateException("Falta configurar MERCADOLIBRE_REDIRECT_URI");
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("code", code);
        form.add("redirect_uri", redirectUri);
        JsonNode response = solicitarToken(form);
        guardarTokens(response, null);
        return response.path("user_id").isNumber() ? response.path("user_id").asLong() : null;
    }

    @Transactional
    public synchronized void desconectar() {
        repository.deleteByTenantId(TenantContext.require());
    }

    public synchronized String obtenerAccessToken() {
        validarConfiguracion();
        long tenantId = TenantContext.require();
        CredencialMercadoLibre credencial = repository.findByTenantId(tenantId).orElseGet(this::crearSemilla);
        if (credencial.getVenceEn().isBefore(Instant.now().plus(10, ChronoUnit.MINUTES))) {
            credencial = renovar(cifrado.descifrar(credencial.getRefreshTokenCifrado()));
        }
        String token = cifrado.descifrar(credencial.getAccessTokenCifrado());
        if (credencial.getUsuarioExternoId() == null) {
            JsonNode usuario = restClient.get().uri("https://api.mercadolibre.com/users/me")
                    .headers(h -> h.setBearerAuth(token)).retrieve().body(JsonNode.class);
            if (usuario != null && usuario.path("id").canConvertToLong()) {
                long usuarioExternoId = usuario.path("id").asLong();
                validarCuentaDisponible(usuarioExternoId, tenantId);
                credencial.setUsuarioExternoId(usuarioExternoId);
                repository.save(credencial);
            }
        }
        return token;
    }

    public synchronized Long obtenerUsuarioExternoId() {
        // obtenerAccessToken también completa usuario_externo_id para credenciales antiguas.
        obtenerAccessToken();
        return repository.findByTenantId(TenantContext.require())
                .map(CredencialMercadoLibre::getUsuarioExternoId)
                .orElse(null);
    }

    public synchronized void invalidarAccessToken() {
        repository.findByTenantId(TenantContext.require()).ifPresent(credencial -> {
            credencial.setVenceEn(Instant.EPOCH);
            repository.save(credencial);
        });
    }

    @Scheduled(initialDelay = 30_000, fixedDelay = 300_000)
    public void renovarProactivamente() {
        Set<Long> tenants = new LinkedHashSet<>();
        repository.findAllByTenantIdIsNotNull().forEach(c -> tenants.add(c.getTenantId()));
        if (!refreshTokenInicial.isBlank()) tenants.add(1L);
        for (Long tenantId : tenants) {
            try (TenantContext.Scope ignored = TenantContext.use(tenantId)) {
                if (configurado()) obtenerAccessToken();
            } catch (Exception e) {
                log.warn("No se pudo renovar el token de Mercado Libre para tenant {}: {}", tenantId, mensajeSeguro(e));
            }
        }
    }

    private CredencialMercadoLibre crearSemilla() {
        if (refreshTokenInicial.isBlank()) throw new IllegalStateException("Falta MERCADOLIBRE_REFRESH_TOKEN");
        if (accessTokenInicial.isBlank()) return renovar(refreshTokenInicial);
        CredencialMercadoLibre credencial = new CredencialMercadoLibre();
        credencial.setTenantId(TenantContext.require());
        credencial.setAccessTokenCifrado(cifrado.cifrar(accessTokenInicial));
        credencial.setRefreshTokenCifrado(cifrado.cifrar(refreshTokenInicial));
        credencial.setVenceEn(Instant.now().plus(6, ChronoUnit.HOURS));
        credencial.setActualizadoEn(Instant.now());
        return repository.save(credencial);
    }

    private CredencialMercadoLibre renovar(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("refresh_token", refreshToken);
        return guardarTokens(solicitarToken(form), refreshToken);
    }

    private JsonNode solicitarToken(MultiValueMap<String, String> form) {
        JsonNode response = restClient.post().uri("https://api.mercadolibre.com/oauth/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).accept(MediaType.APPLICATION_JSON)
                .body(form).retrieve().body(JsonNode.class);
        if (response == null || response.path("access_token").asText().isBlank()) {
            throw new IllegalStateException("Mercado Libre no devolvió el access token esperado");
        }
        return response;
    }

    private CredencialMercadoLibre guardarTokens(JsonNode response, String refreshTokenAnterior) {
        String refreshTokenNuevo = response.path("refresh_token").asText(refreshTokenAnterior);
        if (refreshTokenNuevo == null || refreshTokenNuevo.isBlank()) {
            throw new IllegalStateException("Mercado Libre no devolvió el refresh token esperado");
        }
        long tenantId = TenantContext.require();
        Long usuarioExternoId = response.path("user_id").canConvertToLong()
                ? response.path("user_id").asLong() : null;
        if (usuarioExternoId != null) validarCuentaDisponible(usuarioExternoId, tenantId);
        CredencialMercadoLibre credencial = repository.findByTenantId(tenantId)
                .orElseGet(CredencialMercadoLibre::new);
        credencial.setTenantId(tenantId);
        if (usuarioExternoId != null) credencial.setUsuarioExternoId(usuarioExternoId);
        credencial.setAccessTokenCifrado(cifrado.cifrar(response.path("access_token").asText()));
        credencial.setRefreshTokenCifrado(cifrado.cifrar(refreshTokenNuevo));
        credencial.setVenceEn(Instant.now().plusSeconds(response.path("expires_in").asLong(21_600)));
        credencial.setActualizadoEn(Instant.now());
        repository.save(credencial);
        log.info("Token de Mercado Libre guardado; próximo vencimiento: {}", credencial.getVenceEn());
        return credencial;
    }

    private void validarCuentaDisponible(Long usuarioExternoId, long tenantId) {
        repository.findByUsuarioExternoId(usuarioExternoId)
                .filter(existente -> !existente.getTenantId().equals(tenantId))
                .ifPresent(existente -> {
                    throw new IllegalStateException(
                            "Esta cuenta de Mercado Libre ya está vinculada a otro negocio. "
                                    + "Desconéctela primero desde ese negocio o utilice otra cuenta.");
                });
    }

    private void validarDatosAplicacion() {
        if (clientId.isBlank() || clientSecret.isBlank() || !cifrado.configurado()) {
            throw new IllegalStateException("Mercado Libre requiere APP_ID, SECRET_KEY y una clave de cifrado válida");
        }
    }

    private void validarConfiguracion() {
        validarDatosAplicacion();
        if (!configurado()) throw new IllegalStateException("Mercado Libre no tiene una cuenta conectada");
    }

    private String mensajeSeguro(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName()
                : e.getMessage().replaceAll("APP[_-][A-Za-z0-9-]+", "[REDACTED]");
    }

    public Long resolverTenantPorUsuario(Long usuarioExternoId) {
        if (usuarioExternoId == null) return null;
        return repository.findByUsuarioExternoId(usuarioExternoId)
                .map(CredencialMercadoLibre::getTenantId).orElse(null);
    }
}

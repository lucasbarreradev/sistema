package com.sistema.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.sistema.model.CredencialWooCommerce;
import com.sistema.repository.CredencialWooCommerceRepository;
import com.sistema.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Instant;

@Service
public class WooCommerceCredencialesService {
    private final RestClient restClient = RestClient.create();
    private final CredencialWooCommerceRepository repository;
    private final CifradoCredencialesService cifrado;

    public WooCommerceCredencialesService(CredencialWooCommerceRepository repository,
            CifradoCredencialesService cifrado) {
        this.repository = repository;
        this.cifrado = cifrado;
    }

    public boolean configurado() { return conectado(); }

    public boolean conectado() { return repository.existsByTenantId(TenantContext.require()); }
    public boolean conexionDisponible() { return cifrado.configurado(); }
    public String urlTienda() { return configurado() ? obtener().url() : ""; }

    public Credenciales obtener() {
        long tenantId = TenantContext.require();
        return repository.findByTenantId(tenantId)
                .map(c -> new Credenciales(c.getUrlTienda(), cifrado.descifrar(c.getConsumerKeyCifrada()),
                        cifrado.descifrar(c.getConsumerSecretCifrado())))
                .orElseThrow(() -> new IllegalStateException("WooCommerce no tiene una cuenta conectada"));
    }

    public void vincular(String url, String key, String secret) {
        if (!cifrado.configurado()) throw new IllegalStateException("Falta configurar una clave de cifrado válida");
        String normalizada = validarUrl(url);
        if (key == null || !key.startsWith("ck_") || secret == null || !secret.startsWith("cs_")) {
            throw new IllegalArgumentException("WooCommerce no devolvió credenciales válidas");
        }
        JsonNode estado = restClient.get().uri(normalizada + "/wp-json/wc/v3/system_status")
                .headers(h -> h.setBasicAuth(key, secret)).retrieve().body(JsonNode.class);
        if (estado == null || !estado.isObject()) throw new IllegalStateException("No se pudo verificar la tienda WooCommerce");
        long tenantId = TenantContext.require();
        CredencialWooCommerce credencial = repository.findByTenantId(tenantId).orElseGet(CredencialWooCommerce::new);
        credencial.setTenantId(tenantId);
        credencial.setUrlTienda(normalizada);
        credencial.setConsumerKeyCifrada(cifrado.cifrar(key));
        credencial.setConsumerSecretCifrado(cifrado.cifrar(secret));
        credencial.setActualizadoEn(Instant.now());
        repository.save(credencial);
    }

    @Transactional
    public void desconectar() { repository.deleteByTenantId(TenantContext.require()); }

    public Long resolverTenantPorUrl(String url) {
        String normalizada = limpiarUrl(url);
        return repository.findByUrlTiendaIgnoreCase(normalizada).map(CredencialWooCommerce::getTenantId)
                .orElse(null);
    }

    public String validarUrl(String valor) {
        String normalizada = limpiarUrl(valor);
        try {
            URI uri = URI.create(normalizada);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) throw new IllegalArgumentException();
            return normalizada;
        } catch (Exception e) {
            throw new IllegalArgumentException("La URL de WooCommerce debe ser una dirección HTTPS válida");
        }
    }

    private String limpiarUrl(String valor) {
        return valor == null ? "" : valor.trim().replaceAll("/+$", "");
    }

    public record Credenciales(String url, String key, String secret) {}
}

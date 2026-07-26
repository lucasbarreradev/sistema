package com.sistema.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

@Service
public class WebhookSeguridadService {
    private final String secreto;
    private final String mercadoLibreAppId;
    private final String tiendaNubeClientSecret;

    public WebhookSeguridadService(
            @Value("${integraciones.webhook-secret:}") String secreto,
            @Value("${integraciones.encryption-key:}") String claveCifrado,
            @Value("${integraciones.mercadolibre.client-id:}") String mercadoLibreAppId,
            @Value("${integraciones.tiendanube.client-secret:}") String tiendaNubeClientSecret) {
        this.secreto = secreto == null || secreto.isBlank() ? claveCifrado : secreto;
        this.mercadoLibreAppId = mercadoLibreAppId;
        this.tiendaNubeClientSecret = tiendaNubeClientSecret;
    }

    public boolean configurado() {
        return secreto != null && !secreto.isBlank();
    }

    public boolean firmaWooValida(String payload, String firma) {
        if (!configurado() || firma == null || firma.isBlank()) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] esperada = Base64.getEncoder().encode(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
            byte[] recibida = firma.getBytes(StandardCharsets.UTF_8);
            return MessageDigest.isEqual(esperada, recibida);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean secretoTiendaNubeValido(String recibido) {
        if (!configurado() || recibido == null) return false;
        return MessageDigest.isEqual(secreto.getBytes(StandardCharsets.UTF_8),
                recibido.getBytes(StandardCharsets.UTF_8));
    }

    public boolean firmaTiendaNubeValida(String payload, String firma) {
        if (tiendaNubeClientSecret == null || tiendaNubeClientSecret.isBlank()
                || firma == null || firma.isBlank()) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(tiendaNubeClientSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            byte[] recibida = firma.trim().getBytes(StandardCharsets.UTF_8);
            byte[] hexadecimal = HexFormat.of().formatHex(digest)
                    .toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
            byte[] base64 = Base64.getEncoder().encode(digest);
            return MessageDigest.isEqual(hexadecimal, recibida)
                    || MessageDigest.isEqual(base64, recibida);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean aplicacionMercadoLibreValida(String applicationId) {
        return mercadoLibreAppId == null || mercadoLibreAppId.isBlank()
                || (applicationId != null && mercadoLibreAppId.equals(applicationId));
    }

    public String secretoRegistro() {
        if (!configurado()) throw new IllegalStateException("Falta configurar INTEGRATIONS_WEBHOOK_SECRET");
        return secreto;
    }
}

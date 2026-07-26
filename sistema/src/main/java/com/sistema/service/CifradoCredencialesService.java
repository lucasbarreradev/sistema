package com.sistema.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class CifradoCredencialesService {
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH = 128;
    private final SecureRandom secureRandom = new SecureRandom();
    private final String claveConfigurada;

    public CifradoCredencialesService(@Value("${integraciones.encryption-key:}") String claveConfigurada) {
        this.claveConfigurada = claveConfigurada;
    }

    public boolean configurado() {
        if (claveConfigurada == null || claveConfigurada.isBlank()) return false;
        try {
            return Base64.getDecoder().decode(claveConfigurada).length == 32;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public String cifrar(String valor) {
        if (valor == null || valor.isBlank()) throw new IllegalArgumentException("No se puede cifrar un token vacío");
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, clave(), new GCMParameterSpec(TAG_LENGTH, iv));
            byte[] cifrado = cipher.doFinal(valor.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + cifrado.length).put(iv).put(cifrado).array());
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo cifrar la credencial", e);
        }
    }

    public String descifrar(String valor) {
        try {
            byte[] datos = Base64.getDecoder().decode(valor);
            ByteBuffer buffer = ByteBuffer.wrap(datos);
            byte[] iv = new byte[IV_LENGTH];
            buffer.get(iv);
            byte[] cifrado = new byte[buffer.remaining()];
            buffer.get(cifrado);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, clave(), new GCMParameterSpec(TAG_LENGTH, iv));
            return new String(cipher.doFinal(cifrado), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo descifrar la credencial. Verifique INTEGRATIONS_ENCRYPTION_KEY", e);
        }
    }

    private SecretKeySpec clave() {
        if (!configurado()) throw new IllegalStateException("INTEGRATIONS_ENCRYPTION_KEY debe ser una clave Base64 de 32 bytes");
        return new SecretKeySpec(Base64.getDecoder().decode(claveConfigurada), "AES");
    }
}

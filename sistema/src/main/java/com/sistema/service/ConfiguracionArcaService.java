package com.sistema.service;

import com.sistema.model.AmbienteArca;
import com.sistema.model.CondicionFiscalArca;
import com.sistema.model.ConfiguracionArca;
import com.sistema.repository.ConfiguracionArcaRepository;
import com.sistema.tenant.TenantContext;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

@Service
public class ConfiguracionArcaService {
    private static final long MAX_ARCHIVO = 1024L * 1024L;

    private final ConfiguracionArcaRepository repository;
    private final CifradoCredencialesService cifrado;

    public ConfiguracionArcaService(ConfiguracionArcaRepository repository,
                                    CifradoCredencialesService cifrado) {
        this.repository = repository;
        this.cifrado = cifrado;
    }

    @Transactional(readOnly = true)
    public Optional<Resumen> resumen() {
        return repository.findByTenantId(TenantContext.require()).map(c ->
                new Resumen(c.getCuit(), c.getPuntoVenta(), c.getCondicionFiscal(),
                        c.getCertificadoTitular(), c.getCertificadoVenceEn(), c.getActualizadoEn()));
    }

    @Transactional(readOnly = true)
    public boolean configurada() {
        return cifrado.configurado() && repository.existsByTenantId(TenantContext.require());
    }

    @Transactional(readOnly = true)
    public Credenciales obtenerCredenciales() {
        ConfiguracionArca c = repository.findByTenantId(TenantContext.require())
                .orElseThrow(() -> new IllegalStateException("Configure ARCA en modo testing antes de facturar"));
        if (c.getAmbiente() != AmbienteArca.HOMOLOGACION) {
            throw new IllegalStateException("La integración está habilitada únicamente para homologación");
        }
        return new Credenciales(c.getCuit(), c.getPuntoVenta(), c.getCondicionFiscal(),
                cifrado.descifrar(c.getCertificadoCifrado()),
                cifrado.descifrar(c.getClavePrivadaCifrada()));
    }

    @Transactional
    public ConfiguracionArca guardar(String cuit, Integer puntoVenta,
                                     CondicionFiscalArca condicionFiscal,
                                     MultipartFile certificado,
                                     MultipartFile clavePrivada) {
        if (!cifrado.configurado()) {
            throw new IllegalStateException("Configure INTEGRATIONS_ENCRYPTION_KEY antes de guardar certificados");
        }
        String cuitLimpio = soloDigitos(cuit);
        validarCuit(cuitLimpio);
        if (puntoVenta == null || puntoVenta < 1 || puntoVenta > 99998) {
            throw new IllegalArgumentException("El punto de venta debe estar entre 1 y 99998");
        }
        if (condicionFiscal == null) throw new IllegalArgumentException("Seleccione la condición fiscal del emisor");

        long tenantId = TenantContext.require();
        ConfiguracionArca destino = repository.findByTenantId(tenantId).orElseGet(ConfiguracionArca::new);
        String certificadoPem = archivoOpcional(certificado);
        String clavePem = archivoOpcional(clavePrivada);
        if (certificadoPem == null && destino.getCertificadoCifrado() != null) {
            certificadoPem = cifrado.descifrar(destino.getCertificadoCifrado());
        }
        if (clavePem == null && destino.getClavePrivadaCifrada() != null) {
            clavePem = cifrado.descifrar(destino.getClavePrivadaCifrada());
        }
        if (certificadoPem == null || clavePem == null) {
            throw new IllegalArgumentException("Adjunte el certificado y la clave privada de homologación");
        }

        X509Certificate x509 = leerCertificado(certificadoPem);
        PrivateKey privateKey = leerClavePrivada(clavePem);
        validarMaterial(x509, privateKey);

        destino.setTenantId(tenantId);
        destino.setCuit(cuitLimpio);
        destino.setPuntoVenta(puntoVenta);
        destino.setCondicionFiscal(condicionFiscal);
        destino.setAmbiente(AmbienteArca.HOMOLOGACION);
        destino.setCertificadoCifrado(cifrado.cifrar(aPem(x509)));
        destino.setClavePrivadaCifrada(cifrado.cifrar(clavePem.trim()));
        destino.setCertificadoTitular(x509.getSubjectX500Principal().getName());
        destino.setCertificadoVenceEn(x509.getNotAfter().toInstant());
        destino.setActualizadoEn(Instant.now());
        return repository.save(destino);
    }

    @Transactional
    public void eliminar() {
        repository.deleteByTenantId(TenantContext.require());
    }

    public X509Certificate leerCertificado(String contenido) {
        try {
            byte[] bytes;
            if (contenido.contains("BEGIN CERTIFICATE")) {
                String base64 = contenido.replaceAll("-----BEGIN CERTIFICATE-----|-----END CERTIFICATE-----|\\s", "");
                bytes = Base64.getDecoder().decode(base64);
            } else {
                bytes = Base64.getDecoder().decode(contenido.replaceAll("\\s", ""));
            }
            return (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(new ByteArrayInputStream(bytes));
        } catch (Exception e) {
            throw new IllegalArgumentException("El certificado de ARCA no es válido", e);
        }
    }

    public PrivateKey leerClavePrivada(String contenido) {
        try (PEMParser parser = new PEMParser(new StringReader(contenido))) {
            Object objeto = parser.readObject();
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
            if (objeto instanceof PEMKeyPair par) return converter.getKeyPair(par).getPrivate();
            if (objeto instanceof PrivateKeyInfo info) return converter.getPrivateKey(info);
            throw new IllegalArgumentException("La clave debe ser PEM PKCS#8 o RSA sin contraseña");
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("No se pudo leer la clave privada de ARCA", e);
        }
    }

    private void validarMaterial(X509Certificate certificado, PrivateKey clave) {
        try {
            certificado.checkValidity();
            String algoritmo = clave.getAlgorithm().equalsIgnoreCase("EC") ? "SHA256withECDSA" : "SHA256withRSA";
            Signature firma = Signature.getInstance(algoritmo);
            firma.initSign(clave);
            firma.update("validacion-arca".getBytes(StandardCharsets.UTF_8));
            byte[] firmada = firma.sign();
            firma.initVerify(certificado.getPublicKey());
            firma.update("validacion-arca".getBytes(StandardCharsets.UTF_8));
            if (!firma.verify(firmada)) throw new IllegalArgumentException("La clave privada no corresponde al certificado");

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("No se pudo validar el certificado y su clave privada", e);
        }
    }

    private String archivoOpcional(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) return null;
        if (archivo.getSize() > MAX_ARCHIVO) throw new IllegalArgumentException("Cada archivo debe pesar menos de 1 MB");
        try {
            byte[] bytes = archivo.getBytes();
            String texto = new String(bytes, StandardCharsets.UTF_8).trim();
            if (texto.contains("-----BEGIN")) return texto;
            return Base64.getEncoder().encodeToString(bytes);
        } catch (Exception e) {
            throw new IllegalArgumentException("No se pudo leer el archivo " + archivo.getOriginalFilename(), e);
        }
    }

    private String aPem(X509Certificate certificado) {
        try {
            String b64 = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8))
                    .encodeToString(certificado.getEncoded());
            return "-----BEGIN CERTIFICATE-----\n" + b64 + "\n-----END CERTIFICATE-----";
        } catch (Exception e) {
            throw new IllegalArgumentException("No se pudo almacenar el certificado", e);
        }
    }

    private void validarCuit(String cuit) {
        if (cuit == null || !cuit.matches("\\d{11}")) throw new IllegalArgumentException("Ingrese un CUIT de 11 dígitos");
        int[] pesos = {5,4,3,2,7,6,5,4,3,2};
        int suma = 0;
        for (int i = 0; i < 10; i++) suma += (cuit.charAt(i) - '0') * pesos[i];
        int verificador = 11 - suma % 11;
        if (verificador == 11) verificador = 0;
        else if (verificador == 10) verificador = 9;
        if (verificador != cuit.charAt(10) - '0') throw new IllegalArgumentException("El CUIT no es válido");
    }

    private String soloDigitos(String valor) { return valor == null ? null : valor.replaceAll("\\D", ""); }

    public record Resumen(String cuit, Integer puntoVenta, CondicionFiscalArca condicionFiscal,
                          String titular, Instant venceEn, Instant actualizadoEn) {
        public String getCuit() { return cuit; }
        public Integer getPuntoVenta() { return puntoVenta; }
        public CondicionFiscalArca getCondicionFiscal() { return condicionFiscal; }
        public String getTitular() { return titular; }
        public Instant getVenceEn() { return venceEn; }
        public Instant getActualizadoEn() { return actualizadoEn; }
    }
    public record Credenciales(String cuit, Integer puntoVenta, CondicionFiscalArca condicionFiscal,
                               String certificadoPem, String clavePrivadaPem) {}
}

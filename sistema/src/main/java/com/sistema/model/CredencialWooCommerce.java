package com.sistema.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(uniqueConstraints = {
        @UniqueConstraint(name = "uk_credencial_woo_tenant", columnNames = "tenant_id"),
        @UniqueConstraint(name = "uk_credencial_woo_url", columnNames = "url_tienda")
})
public class CredencialWooCommerce {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;
    @Column(nullable = false, length = 1000)
    private String urlTienda;
    @Column(name = "nombre_cuenta", length = 255)
    private String nombreCuenta;
    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String consumerKeyCifrada;
    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String consumerSecretCifrado;
    private Instant actualizadoEn;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public String getUrlTienda() { return urlTienda; }
    public void setUrlTienda(String urlTienda) { this.urlTienda = urlTienda; }
    public String getNombreCuenta() { return nombreCuenta; }
    public void setNombreCuenta(String nombreCuenta) { this.nombreCuenta = nombreCuenta; }
    public String getConsumerKeyCifrada() { return consumerKeyCifrada; }
    public void setConsumerKeyCifrada(String consumerKeyCifrada) { this.consumerKeyCifrada = consumerKeyCifrada; }
    public String getConsumerSecretCifrado() { return consumerSecretCifrado; }
    public void setConsumerSecretCifrado(String consumerSecretCifrado) { this.consumerSecretCifrado = consumerSecretCifrado; }
    public Instant getActualizadoEn() { return actualizadoEn; }
    public void setActualizadoEn(Instant actualizadoEn) { this.actualizadoEn = actualizadoEn; }
}

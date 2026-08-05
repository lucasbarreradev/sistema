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
        @UniqueConstraint(name = "uk_credencial_tn_tenant", columnNames = "tenant_id"),
        @UniqueConstraint(name = "uk_credencial_tn_store", columnNames = "store_id")
})
public class CredencialTiendanube {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;
    @Column(nullable = false)
    private String storeId;
    @Column(name = "nombre_cuenta", length = 255)
    private String nombreCuenta;
    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String accessTokenCifrado;
    private Instant actualizadoEn;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public String getStoreId() { return storeId; }
    public void setStoreId(String storeId) { this.storeId = storeId; }
    public String getNombreCuenta() { return nombreCuenta; }
    public void setNombreCuenta(String nombreCuenta) { this.nombreCuenta = nombreCuenta; }
    public String getAccessTokenCifrado() { return accessTokenCifrado; }
    public void setAccessTokenCifrado(String accessTokenCifrado) { this.accessTokenCifrado = accessTokenCifrado; }
    public Instant getActualizadoEn() { return actualizadoEn; }
    public void setActualizadoEn(Instant actualizadoEn) { this.actualizadoEn = actualizadoEn; }
}

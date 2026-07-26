package com.sistema.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Lob;

import java.time.Instant;

@Entity
@Table(uniqueConstraints = {
        @UniqueConstraint(name = "uk_credencial_ml_tenant", columnNames = "tenant_id"),
        @UniqueConstraint(name = "uk_credencial_ml_usuario", columnNames = "usuario_externo_id")
})
public class CredencialMercadoLibre {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;
    @Column(name = "usuario_externo_id")
    private Long usuarioExternoId;
    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String accessTokenCifrado;
    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String refreshTokenCifrado;
    @Column(nullable = false)
    private Instant venceEn;
    private Instant actualizadoEn;

    public CredencialMercadoLibre() {}
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getUsuarioExternoId() { return usuarioExternoId; }
    public void setUsuarioExternoId(Long usuarioExternoId) { this.usuarioExternoId = usuarioExternoId; }
    public String getAccessTokenCifrado() { return accessTokenCifrado; }
    public void setAccessTokenCifrado(String accessTokenCifrado) { this.accessTokenCifrado = accessTokenCifrado; }
    public String getRefreshTokenCifrado() { return refreshTokenCifrado; }
    public void setRefreshTokenCifrado(String refreshTokenCifrado) { this.refreshTokenCifrado = refreshTokenCifrado; }
    public Instant getVenceEn() { return venceEn; }
    public void setVenceEn(Instant venceEn) { this.venceEn = venceEn; }
    public Instant getActualizadoEn() { return actualizadoEn; }
    public void setActualizadoEn(Instant actualizadoEn) { this.actualizadoEn = actualizadoEn; }
}

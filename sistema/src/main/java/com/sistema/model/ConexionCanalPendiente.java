package com.sistema.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import java.time.Instant;

@Entity
public class ConexionCanalPendiente {
    @Id
    private String id;
    private Long tenantId;
    private CanalVenta canal;
    private String dato;
    private Instant venceEn;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public CanalVenta getCanal() { return canal; }
    public void setCanal(CanalVenta canal) { this.canal = canal; }
    public String getDato() { return dato; }
    public void setDato(String dato) { this.dato = dato; }
    public Instant getVenceEn() { return venceEn; }
    public void setVenceEn(Instant venceEn) { this.venceEn = venceEn; }
}

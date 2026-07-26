package com.sistema.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;

public class TenantUserDetails extends User {
    private final Long tenantId;
    private final String tenantNombre;

    public TenantUserDetails(String username, String password, boolean enabled,
                             Collection<? extends GrantedAuthority> authorities,
                             Long tenantId, String tenantNombre) {
        super(username, password, enabled, true, true, true, authorities);
        this.tenantId = tenantId;
        this.tenantNombre = tenantNombre;
    }

    public Long getTenantId() { return tenantId; }
    public String getTenantNombre() { return tenantNombre; }
}

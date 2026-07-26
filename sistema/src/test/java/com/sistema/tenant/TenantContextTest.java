package com.sistema.tenant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TenantContextTest {
    @Test
    void restauraElTenantAnteriorAlCerrarUnScopeAnidado() {
        try (TenantContext.Scope ignored = TenantContext.use(10L)) {
            assertEquals(10L, TenantContext.require());
            try (TenantContext.Scope ignored2 = TenantContext.use(20L)) {
                assertEquals(20L, TenantContext.require());
            }
            assertEquals(10L, TenantContext.require());
        }
        assertNull(TenantContext.get());
    }

    @Test
    void callEjecutaConElTenantIndicadoYLimpiaElContexto() {
        Long resultado = TenantContext.call(42L, TenantContext::require);
        assertEquals(42L, resultado);
        assertNull(TenantContext.get());
    }
}

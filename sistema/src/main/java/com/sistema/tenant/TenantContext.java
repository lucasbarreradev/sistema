package com.sistema.tenant;

import java.util.function.Supplier;

public final class TenantContext {
    public static final String SESSION_ATTRIBUTE = "TENANT_ID";
    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();

    private TenantContext() {}

    public static Long get() { return CURRENT.get(); }

    public static long require() {
        Long tenantId = CURRENT.get();
        if (tenantId == null || tenantId <= 0) {
            throw new IllegalStateException("No hay un tenant autenticado en el contexto actual");
        }
        return tenantId;
    }

    public static Scope use(long tenantId) {
        if (tenantId <= 0) throw new IllegalArgumentException("Tenant inválido");
        Long anterior = CURRENT.get();
        CURRENT.set(tenantId);
        return new Scope(anterior);
    }

    public static void run(long tenantId, Runnable accion) {
        try (Scope ignored = use(tenantId)) { accion.run(); }
    }

    public static <T> T call(long tenantId, Supplier<T> accion) {
        try (Scope ignored = use(tenantId)) { return accion.get(); }
    }

    public static final class Scope implements AutoCloseable {
        private final Long anterior;
        private Scope(Long anterior) { this.anterior = anterior; }
        @Override public void close() {
            if (anterior == null) CURRENT.remove(); else CURRENT.set(anterior);
        }
    }
}

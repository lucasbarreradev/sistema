package com.sistema.service;

import com.sistema.model.Tenant;
import com.sistema.repository.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TenantDeletionService {
    private static final Logger log = LoggerFactory.getLogger(TenantDeletionService.class);

    /*
     * El orden respeta las claves foráneas: primero comprobantes e ítems,
     * luego sus cabeceras y finalmente productos, clientes y proveedores.
     */
    private static final List<String> TABLAS_TENANT_EN_ORDEN = List.of(
            "comprobante_arca",
            "remito_item",
            "remito",
            "venta_item",
            "venta",
            "presupuesto_detalle",
            "presupuesto",
            "movimiento_inventario",
            "precio_producto",
            "publicacion_canal",
            "producto_variante",
            "producto",
            "orden_canal_procesada",
            "gasto",
            "configuracion_documento",
            "configuracion_arca",
            "trabajo_sincronizacion",
            "conexion_canal_pendiente",
            "credencial_mercado_libre",
            "credencial_woo_commerce",
            "credencial_tiendanube",
            "cliente",
            "proveedor"
    );

    private final TenantRepository tenantRepository;
    private final JdbcTemplate jdbcTemplate;

    public TenantDeletionService(TenantRepository tenantRepository, JdbcTemplate jdbcTemplate) {
        this.tenantRepository = tenantRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public String eliminar(Long tenantId, String confirmacion, Long tenantActual, String usuarioActual) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Negocio no encontrado"));
        validarEliminacion(tenant, confirmacion, tenantActual);

        Integer trabajosActivos = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM trabajo_sincronizacion
                WHERE tenant_id = ? AND estado IN ('PENDIENTE', 'PROCESANDO')
                """, Integer.class, tenantId);
        if (trabajosActivos != null && trabajosActivos > 0) {
            throw new IllegalStateException("El negocio tiene trabajos en proceso. Espere a que terminen o cancélelos antes de borrarlo");
        }

        jdbcTemplate.update("""
                DELETE ur FROM usuario_roles ur
                INNER JOIN usuario u ON u.id = ur.usuario_id
                WHERE u.tenant_id = ?
                """, tenantId);
        for (String tabla : TABLAS_TENANT_EN_ORDEN) {
            jdbcTemplate.update("DELETE FROM " + tabla + " WHERE tenant_id = ?", tenantId);
        }
        jdbcTemplate.update("DELETE FROM usuario WHERE tenant_id = ?", tenantId);
        int eliminados = jdbcTemplate.update("DELETE FROM tenant WHERE id = ?", tenantId);
        if (eliminados != 1) {
            throw new IllegalStateException("No se pudo eliminar el negocio");
        }

        log.warn("Negocio eliminado por superadministrador: tenantId={}, codigo={}, usuario={}",
                tenantId, tenant.getCodigo(), usuarioActual == null ? "desconocido" : usuarioActual);
        return tenant.getNombre();
    }

    private void validarEliminacion(Tenant tenant, String confirmacion, Long tenantActual) {
        if (tenant.getId() == 1L) {
            throw new IllegalArgumentException("El negocio principal no puede borrarse");
        }
        if (tenant.getId().equals(tenantActual)) {
            throw new IllegalArgumentException("No puede borrar el negocio de la sesión actual");
        }
        if (Boolean.TRUE.equals(tenant.getActivo())) {
            throw new IllegalArgumentException("Primero debe desactivar el negocio antes de borrarlo");
        }
        String recibido = confirmacion == null ? "" : confirmacion.trim();
        if (!tenant.getCodigo().equalsIgnoreCase(recibido)) {
            throw new IllegalArgumentException("El código de confirmación no coincide. No se borró el negocio");
        }
    }
}

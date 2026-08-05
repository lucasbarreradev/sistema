package com.sistema.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;

import java.util.List;

@Component
@Order(0)
public class MigracionEsquemaService implements ApplicationRunner {
    private final JdbcTemplate jdbcTemplate;

    public MigracionEsquemaService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        migrarTenants();
        if (tipoColumna("venta", "origen")
                .map(tipo -> tipo.startsWith("enum(")).orElse(false)) {
            jdbcTemplate.execute("ALTER TABLE venta MODIFY COLUMN origen VARCHAR(30) NOT NULL");
        }
        if (tipoColumna("trabajo_sincronizacion", "tipo_trabajo")
                .map(tipo -> tipo.startsWith("enum(")).orElse(false)) {
            jdbcTemplate.execute("""
                    ALTER TABLE trabajo_sincronizacion
                    MODIFY COLUMN tipo_trabajo VARCHAR(40) NULL
                    """);
        }
        if (tipoColumna("trabajo_sincronizacion", "estado")
                .map(tipo -> tipo.startsWith("enum(")).orElse(false)) {
            jdbcTemplate.execute("""
                    ALTER TABLE trabajo_sincronizacion
                    MODIFY COLUMN estado VARCHAR(40) NOT NULL
                    """);
        }
        List<String> tipos = jdbcTemplate.queryForList("""
                SELECT COLUMN_TYPE
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'publicacion_canal'
                  AND COLUMN_NAME = 'estado'
                """, String.class);
        if (!tipos.isEmpty() && tipos.get(0).toLowerCase().startsWith("enum(")) {
            jdbcTemplate.execute("ALTER TABLE publicacion_canal MODIFY COLUMN estado VARCHAR(30) NOT NULL");
        }
    }

    private void migrarTenants() {
        if (tipoColumna("usuario_roles", "rol").map(tipo -> tipo.startsWith("enum(")).orElse(false)) {
            jdbcTemplate.execute("ALTER TABLE usuario_roles MODIFY COLUMN rol VARCHAR(30) NOT NULL");
        }
        asegurarAutoIncremento("credencial_mercado_libre");
        asegurarAutoIncremento("credencial_woo_commerce");
        asegurarAutoIncremento("credencial_tiendanube");
        jdbcTemplate.update("""
                INSERT INTO tenant (id, codigo, nombre, activo, creado_en)
                SELECT 1, 'principal', 'Negocio principal', 1, NOW()
                WHERE NOT EXISTS (SELECT 1 FROM tenant WHERE id = 1)
                """);
        List<String> tablasTenant = List.of(
                "cliente", "gasto", "movimiento_inventario", "precio_producto",
                "presupuesto", "presupuesto_detalle", "producto", "producto_variante",
                "proveedor", "publicacion_canal", "orden_canal_procesada", "remito",
                "remito_item", "venta", "venta_item", "configuracion_documento", "configuracion_arca");
        for (String tabla : tablasTenant) {
            if (columnaExiste(tabla, "tenant_id")) {
                jdbcTemplate.update("UPDATE " + tabla + " SET tenant_id = 1 WHERE tenant_id IS NULL OR tenant_id = 0");
            }
        }
        for (String tabla : List.of("usuario", "credencial_mercado_libre",
                "credencial_woo_commerce", "credencial_tiendanube")) {
            if (columnaExiste(tabla, "tenant_id")) {
                jdbcTemplate.update("UPDATE " + tabla + " SET tenant_id = 1 WHERE tenant_id IS NULL OR tenant_id = 0");
            }
        }
        reemplazarIndice("producto", "UK_clpng6f2m2r9i1y5g2yxajyuq",
                "uk_producto_tenant_sku", "tenant_id, sku");
        reemplazarIndice("producto_variante", "uk_producto_variante_sku",
                "uk_producto_variante_sku", "tenant_id, sku");
        eliminarIndice("producto_variante", "UK_hgvccvik2dcv4koagwophhodf");
        crearIndiceSiFalta("producto_variante", "uk_producto_variante_barra", "tenant_id, codigo_barras");
        crearIndiceNormalSiFalta("producto_variante", "idx_producto_variante_producto", "producto_id");
        reemplazarIndice("producto_variante", "uk_producto_variante_ml",
                "uk_producto_variante_ml", "tenant_id, producto_id, mercado_libre_variation_id");
        crearIndiceNormalSiFalta("precio_producto", "idx_precio_producto_producto", "producto_id");
        reemplazarIndice("precio_producto", "UKov5533ytr2ij0bao4g9le87r7",
                "uk_precio_tenant_producto_forma", "tenant_id, producto_id, forma_pago");
        reemplazarIndice("presupuesto", "UK_rrkqek6neg7eh6n4k2demntbg",
                "uk_presupuesto_tenant_codigo", "tenant_id, codigo");
        crearIndiceNormalSiFalta("publicacion_canal", "idx_publicacion_producto", "producto_id");
        reemplazarIndice("publicacion_canal", "UKpbbvixtqs7cl4hlug5n7irfy0",
                "uk_publicacion_tenant_producto_canal", "tenant_id, producto_id, canal");
        reemplazarIndice("orden_canal_procesada", "uk_orden_canal_procesada",
                "uk_orden_canal_procesada", "tenant_id, canal, orden_id");
        reemplazarIndice("remito", "UK_69k8k1pgu7gaa17l9wbocarwk",
                "uk_remito_tenant_codigo", "tenant_id, codigo");
        reemplazarIndice("venta", "UK_4e2obucvhvadmspx337jj0nex",
                "uk_venta_tenant_codigo", "tenant_id, codigo");
        crearIndiceSiFalta("venta", "uk_venta_tenant_canal_orden",
                "tenant_id, canal_venta, orden_externa_id");
    }

    private boolean columnaExiste(String tabla, String columna) {
        Integer cantidad = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?
                """, Integer.class, tabla, columna);
        return cantidad != null && cantidad > 0;
    }

    private java.util.Optional<String> tipoColumna(String tabla, String columna) {
        List<String> tipos = jdbcTemplate.queryForList("""
                SELECT LOWER(COLUMN_TYPE)
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?
                """, String.class, tabla, columna);
        return tipos.stream().findFirst();
    }

    private void asegurarAutoIncremento(String tabla) {
        List<String> extras = jdbcTemplate.queryForList("""
                SELECT LOWER(EXTRA)
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = 'id'
                """, String.class, tabla);
        if (!extras.isEmpty() && !extras.get(0).contains("auto_increment")) {
            jdbcTemplate.execute("ALTER TABLE " + tabla
                    + " MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT");
        }
    }

    private boolean indiceExiste(String tabla, String indice) {
        Integer cantidad = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ?
                """, Integer.class, tabla, indice);
        return cantidad != null && cantidad > 0;
    }

    private void eliminarIndice(String tabla, String indice) {
        if (indiceExiste(tabla, indice)) jdbcTemplate.execute("ALTER TABLE " + tabla + " DROP INDEX " + indice);
    }

    private void crearIndiceSiFalta(String tabla, String indice, String columnas) {
        if (!indiceExiste(tabla, indice)) {
            jdbcTemplate.execute("ALTER TABLE " + tabla + " ADD CONSTRAINT " + indice
                    + " UNIQUE (" + columnas + ")");
        }
    }

    private void crearIndiceNormalSiFalta(String tabla, String indice, String columnas) {
        if (!indiceExiste(tabla, indice)) {
            jdbcTemplate.execute("ALTER TABLE " + tabla + " ADD INDEX " + indice + " (" + columnas + ")");
        }
    }

    private void reemplazarIndice(String tabla, String anterior, String nuevo, String columnas) {
        if (indiceCoincide(tabla, nuevo, columnas)) {
            if (!anterior.equals(nuevo)) eliminarIndice(tabla, anterior);
            return;
        }
        eliminarIndice(tabla, anterior);
        if (!anterior.equals(nuevo)) eliminarIndice(tabla, nuevo);
        crearIndiceSiFalta(tabla, nuevo, columnas);
    }

    private boolean indiceCoincide(String tabla, String indice, String columnas) {
        List<String> existentes = jdbcTemplate.queryForList("""
                SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',')
                FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ?
                GROUP BY INDEX_NAME
                """, String.class, tabla, indice);
        if (existentes.isEmpty()) return false;
        return existentes.get(0).replace(" ", "").equalsIgnoreCase(columnas.replace(" ", ""));
    }
}

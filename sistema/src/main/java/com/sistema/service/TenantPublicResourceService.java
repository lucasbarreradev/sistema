package com.sistema.service;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Resuelve el tenant únicamente para recursos públicos sin sesión, como las
 * imágenes que descargan los canales de venta. El tenant nunca se expone en la URL.
 */
@Service
public class TenantPublicResourceService {
    private final JdbcTemplate jdbcTemplate;

    public TenantPublicResourceService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<Long> buscarTenantProducto(Long productoId) {
        return buscarTenant("SELECT tenant_id FROM producto WHERE id = ?", productoId);
    }

    public Optional<Long> buscarTenantVariante(Long varianteId) {
        return buscarTenant("SELECT tenant_id FROM producto_variante WHERE id = ?", varianteId);
    }

    private Optional<Long> buscarTenant(String sql, Long id) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, Long.class, id));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
}

package com.sistema.repository;

import com.sistema.model.CredencialWooCommerce;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CredencialWooCommerceRepository extends JpaRepository<CredencialWooCommerce, Long> {
    Optional<CredencialWooCommerce> findByTenantId(Long tenantId);
    Optional<CredencialWooCommerce> findByUrlTiendaIgnoreCase(String urlTienda);
    boolean existsByTenantId(Long tenantId);
    void deleteByTenantId(Long tenantId);
}

package com.sistema.repository;

import com.sistema.model.CredencialTiendanube;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CredencialTiendanubeRepository extends JpaRepository<CredencialTiendanube, Long> {
    Optional<CredencialTiendanube> findByTenantId(Long tenantId);
    Optional<CredencialTiendanube> findByStoreId(String storeId);
    boolean existsByTenantId(Long tenantId);
    void deleteByTenantId(Long tenantId);
}

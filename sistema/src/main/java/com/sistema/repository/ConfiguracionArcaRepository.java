package com.sistema.repository;

import com.sistema.model.ConfiguracionArca;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConfiguracionArcaRepository extends JpaRepository<ConfiguracionArca, Long> {
    Optional<ConfiguracionArca> findByTenantId(Long tenantId);
    boolean existsByTenantId(Long tenantId);
    void deleteByTenantId(Long tenantId);
}

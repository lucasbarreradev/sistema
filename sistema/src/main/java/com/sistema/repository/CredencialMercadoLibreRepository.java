package com.sistema.repository;

import com.sistema.model.CredencialMercadoLibre;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

public interface CredencialMercadoLibreRepository extends JpaRepository<CredencialMercadoLibre, Long> {
    Optional<CredencialMercadoLibre> findByTenantId(Long tenantId);
    Optional<CredencialMercadoLibre> findByUsuarioExternoId(Long usuarioExternoId);
    boolean existsByTenantId(Long tenantId);
    void deleteByTenantId(Long tenantId);
    List<CredencialMercadoLibre> findAllByTenantIdIsNotNull();
}

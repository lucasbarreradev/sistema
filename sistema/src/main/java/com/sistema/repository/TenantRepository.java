package com.sistema.repository;

import com.sistema.model.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TenantRepository extends JpaRepository<Tenant, Long> {
    Optional<Tenant> findByCodigoIgnoreCase(String codigo);
    List<Tenant> findAllByOrderByNombreAsc();
}

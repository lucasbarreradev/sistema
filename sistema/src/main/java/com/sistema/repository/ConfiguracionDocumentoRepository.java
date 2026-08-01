package com.sistema.repository;

import com.sistema.model.ConfiguracionDocumento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConfiguracionDocumentoRepository
        extends JpaRepository<ConfiguracionDocumento, Long> {
    Optional<ConfiguracionDocumento> findFirstByOrderByIdAsc();
}

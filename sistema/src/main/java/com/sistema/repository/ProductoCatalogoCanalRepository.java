package com.sistema.repository;

import com.sistema.model.CanalVenta;
import com.sistema.model.ProductoCatalogoCanal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ProductoCatalogoCanalRepository
        extends JpaRepository<ProductoCatalogoCanal, Long> {
    List<ProductoCatalogoCanal> findByCanalOrderByDescripcionAsc(CanalVenta canal);
    List<ProductoCatalogoCanal> findByCanalAndIdExternoIn(
            CanalVenta canal, Collection<String> idsExternos);
    boolean existsByCanal(CanalVenta canal);
}

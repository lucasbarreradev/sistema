package com.sistema.repository;

import com.sistema.model.Producto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductoRepository extends JpaRepository<Producto, Long> {
    List<Producto> findAllByOrderByDescripcionAsc();
    List<Producto> findByDescripcionContainingIgnoreCaseOrSkuContainingIgnoreCase(
            String descripcion,
            String sku
    );

    @Query("""
SELECT COUNT(p) 
FROM Producto p 
WHERE p.sku LIKE CONCAT(:prefijo, '%')
""")
    long countBySkuPrefix(@Param("prefijo") String prefijo);

    boolean existsByProveedorId(Long proveedorId);
    Optional<Producto> findBySkuIgnoreCase(String sku);
    Optional<Producto> findByMercadoLibreId(String mercadoLibreId);
    Optional<Producto> findByMercadoLibreFamilyId(String mercadoLibreFamilyId);
}

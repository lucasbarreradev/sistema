package com.sistema.repository;

import com.sistema.model.ProductoVariante;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;
import java.util.Optional;
import java.util.Collection;

public interface ProductoVarianteRepository extends JpaRepository<ProductoVariante, Long> {
    @EntityGraph(attributePaths = "producto")
    List<ProductoVariante> findByProductoIdOrderByNombreAsc(Long productoId);
    @EntityGraph(attributePaths = "producto")
    List<ProductoVariante> findByProductoIdInOrderByProductoIdAscNombreAsc(
            Collection<Long> productoIds);
    @EntityGraph(attributePaths = "producto")
    Optional<ProductoVariante> findBySkuIgnoreCase(String sku);
    Optional<ProductoVariante> findByProductoIdAndMercadoLibreVariationId(Long productoId, String variationId);
    Optional<ProductoVariante> findByProductoIdAndMercadoLibreItemId(Long productoId, String itemId);
    Optional<ProductoVariante> findByProductoIdAndMercadoLibreProductNumber(Long productoId, String productNumber);
    Optional<ProductoVariante> findByProductoIdAndWooCommerceVariationId(Long productoId, String variationId);
    Optional<ProductoVariante> findByProductoIdAndTiendaNubeVariationId(Long productoId, String variationId);
    Optional<ProductoVariante> findByMercadoLibreVariationId(String variationId);
    Optional<ProductoVariante> findByMercadoLibreItemId(String itemId);
    Optional<ProductoVariante> findByWooCommerceVariationId(String variationId);
    Optional<ProductoVariante> findByTiendaNubeVariationId(String variationId);
    boolean existsBySkuIgnoreCaseAndIdNot(String sku, Long id);
    boolean existsBySkuIgnoreCase(String sku);
    boolean existsByCodigoBarras(String codigoBarras);
    boolean existsByProductoId(Long productoId);
    List<ProductoVariante> findBySkuContainingIgnoreCaseOrNombreContainingIgnoreCaseOrCodigoBarrasContainingIgnoreCase(
            String sku, String nombre, String codigoBarras);
}

package com.sistema.repository;

import com.sistema.model.Producto;
import com.sistema.dto.ProductoFotoProjection;
import com.sistema.dto.ProductoListadoProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    @Query(value = """
            SELECT p.id AS id,
                   p.sku AS sku,
                   p.descripcion AS descripcion,
                   p.cantidad AS cantidad,
                   proveedor.nombreRazonSocial AS proveedorNombre,
                   COUNT(variante.id) AS cantidadVariantes,
                   COALESCE(SUM(variante.stock), 0) AS stockVariantes,
                   MIN(COALESCE(variante.precioContado, p.precioContado)) AS precioContadoMinimo,
                   MAX(COALESCE(variante.precioContado, p.precioContado)) AS precioContadoMaximo,
                   MIN(COALESCE(variante.precioTarjeta, variante.precioContado,
                                p.precioTarjeta, p.precioContado)) AS precioTarjetaMinimo,
                   MAX(COALESCE(variante.precioTarjeta, variante.precioContado,
                                p.precioTarjeta, p.precioContado)) AS precioTarjetaMaximo,
                   MIN(COALESCE(variante.precioCuentaCorriente, variante.precioContado,
                                p.precioCuentaCorriente, p.precioContado)) AS precioCuentaCorrienteMinimo,
                   MAX(COALESCE(variante.precioCuentaCorriente, variante.precioContado,
                                p.precioCuentaCorriente, p.precioContado)) AS precioCuentaCorrienteMaximo,
                   MAX(CASE WHEN p.fotoContenido IS NOT NULL
                                  OR (p.fotoUrlExterna IS NOT NULL AND TRIM(p.fotoUrlExterna) <> '')
                            THEN 1 ELSE 0 END) AS indicadorFoto
              FROM Producto p
              LEFT JOIN p.proveedor proveedor
              LEFT JOIN p.variantes variante
             WHERE (:busqueda = ''
                    OR LOWER(p.descripcion) LIKE LOWER(CONCAT('%', :busqueda, '%'))
                    OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :busqueda, '%')))
             GROUP BY p.id, p.sku, p.descripcion, p.cantidad, proveedor.nombreRazonSocial
             ORDER BY LOWER(p.descripcion), p.id
            """,
            countQuery = """
            SELECT COUNT(p)
              FROM Producto p
             WHERE (:busqueda = ''
                    OR LOWER(p.descripcion) LIKE LOWER(CONCAT('%', :busqueda, '%'))
                    OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :busqueda, '%')))
            """)
    Page<ProductoListadoProjection> buscarPaginaListado(
            @Param("busqueda") String busqueda, Pageable pageable);

    @Query("""
            SELECT p.id
              FROM Producto p
             WHERE (:busqueda = ''
                    OR LOWER(p.descripcion) LIKE LOWER(CONCAT('%', :busqueda, '%'))
                    OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :busqueda, '%')))
             ORDER BY LOWER(p.descripcion), p.id
            """)
    List<Long> buscarIdsListado(@Param("busqueda") String busqueda);

    @Query("""
            SELECT p.fotoContenido AS fotoContenido,
                   p.fotoUrlExterna AS fotoUrlExterna,
                   p.fotoTipoContenido AS fotoTipoContenido
              FROM Producto p
             WHERE p.id = :id
            """)
    Optional<ProductoFotoProjection> buscarFotoPorId(@Param("id") Long id);
}

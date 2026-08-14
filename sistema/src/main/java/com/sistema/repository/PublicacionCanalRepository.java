package com.sistema.repository;

import com.sistema.model.CanalVenta;
import com.sistema.model.PublicacionCanal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import com.sistema.dto.PublicacionCanalListadoDto;

import java.util.List;
import java.util.Optional;

public interface PublicacionCanalRepository extends JpaRepository<PublicacionCanal, Long> {
    Optional<PublicacionCanal> findByProductoIdAndCanal(Long productoId, CanalVenta canal);
    @EntityGraph(attributePaths = "producto")
    Optional<PublicacionCanal> findByCanalAndIdExterno(CanalVenta canal, String idExterno);
    @EntityGraph(attributePaths = "producto")
    List<PublicacionCanal> findByCanalAndIdExternoIsNotNull(CanalVenta canal);
    @EntityGraph(attributePaths = "producto")
    Optional<PublicacionCanal> findWithProductoById(Long id);
    List<PublicacionCanal> findAllByOrderByFechaActualizacionDesc();
    @Query("""
            SELECT new com.sistema.dto.PublicacionCanalListadoDto(
                   publicacion.id, producto.descripcion, publicacion.canal,
                   publicacion.estado, publicacion.idExterno,
                   publicacion.fechaActualizacion, publicacion.ultimoError)
              FROM PublicacionCanal publicacion
              JOIN publicacion.producto producto
             ORDER BY publicacion.fechaActualizacion DESC, publicacion.id DESC
            """)
    List<PublicacionCanalListadoDto> buscarHistorialLiviano(Pageable pageable);
    void deleteAllByProductoId(Long productoId);
}

package com.sistema.repository;

import com.sistema.model.CanalVenta;
import com.sistema.model.PublicacionCanal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;
import java.util.Optional;

public interface PublicacionCanalRepository extends JpaRepository<PublicacionCanal, Long> {
    Optional<PublicacionCanal> findByProductoIdAndCanal(Long productoId, CanalVenta canal);
    Optional<PublicacionCanal> findByCanalAndIdExterno(CanalVenta canal, String idExterno);
    List<PublicacionCanal> findByCanalAndIdExternoIsNotNull(CanalVenta canal);
    @EntityGraph(attributePaths = "producto")
    Optional<PublicacionCanal> findWithProductoById(Long id);
    List<PublicacionCanal> findAllByOrderByFechaActualizacionDesc();
    void deleteAllByProductoId(Long productoId);
}

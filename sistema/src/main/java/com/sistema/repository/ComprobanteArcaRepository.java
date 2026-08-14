package com.sistema.repository;

import com.sistema.model.ComprobanteArca;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ComprobanteArcaRepository extends JpaRepository<ComprobanteArca, Long> {
    @EntityGraph(attributePaths = {"facturaOrigen", "facturaOrigen.cliente", "facturaOrigen.items",
            "facturaOrigen.items.producto", "facturaOrigen.items.variante"})
    List<ComprobanteArca> findByFacturaOrigenIdOrderByFechaComprobanteDescIdDesc(Long ventaId);

    @EntityGraph(attributePaths = {"facturaOrigen", "facturaOrigen.cliente", "facturaOrigen.items",
            "facturaOrigen.items.producto", "facturaOrigen.items.variante"})
    Optional<ComprobanteArca> findWithFacturaOrigenById(Long id);

    @EntityGraph(attributePaths = "facturaOrigen")
    List<ComprobanteArca> findAllByOrderByFechaComprobanteDescIdDesc();
}

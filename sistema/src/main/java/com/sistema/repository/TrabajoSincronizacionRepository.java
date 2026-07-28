package com.sistema.repository;

import com.sistema.model.EstadoTrabajoSincronizacion;
import com.sistema.model.TrabajoSincronizacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface TrabajoSincronizacionRepository extends JpaRepository<TrabajoSincronizacion, Long> {
    List<TrabajoSincronizacion> findTop10ByOrderByCreadoEnDesc();
    List<TrabajoSincronizacion> findByEstadoIn(Collection<EstadoTrabajoSincronizacion> estados);
    boolean existsByEstadoIn(Collection<EstadoTrabajoSincronizacion> estados);
}

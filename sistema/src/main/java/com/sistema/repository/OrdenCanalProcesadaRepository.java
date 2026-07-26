package com.sistema.repository;

import com.sistema.model.CanalVenta;
import com.sistema.model.OrdenCanalProcesada;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrdenCanalProcesadaRepository extends JpaRepository<OrdenCanalProcesada, Long> {
    boolean existsByCanalAndOrdenId(CanalVenta canal, String ordenId);
}

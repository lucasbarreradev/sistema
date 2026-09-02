package com.sistema.dto;

import com.sistema.model.CanalVenta;

import java.io.Serializable;
import java.util.List;

public record RevisionPublicacionSesion(
        long tenantId,
        List<Long> productoIds,
        List<CanalVenta> canales,
        Long trabajoPreparacionId) implements Serializable {

    public RevisionPublicacionSesion(
            long tenantId, List<Long> productoIds, List<CanalVenta> canales) {
        this(tenantId, productoIds, canales, null);
    }
}

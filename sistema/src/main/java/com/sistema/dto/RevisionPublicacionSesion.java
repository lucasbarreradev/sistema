package com.sistema.dto;

import com.sistema.model.CanalVenta;

import java.io.Serializable;
import java.util.List;

public record RevisionPublicacionSesion(
        long tenantId,
        List<Long> productoIds,
        List<CanalVenta> canales) implements Serializable {
}

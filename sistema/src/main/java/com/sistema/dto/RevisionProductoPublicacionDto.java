package com.sistema.dto;

import com.sistema.model.Producto;
import com.sistema.model.ProductoVariante;

import java.util.List;

public record RevisionProductoPublicacionDto(
        Producto producto,
        List<ProductoVariante> variantes,
        List<String> faltantes,
        List<String> atributosFaltantes,
        List<String> atributosObligatorios) {

    public boolean isListo() {
        return faltantes == null || faltantes.isEmpty();
    }

    public boolean isMarcaObligatoria() {
        return atributosObligatorios != null && atributosObligatorios.contains("Marca");
    }

    public boolean isModeloObligatorio() {
        return atributosObligatorios != null && atributosObligatorios.contains("Modelo");
    }
}

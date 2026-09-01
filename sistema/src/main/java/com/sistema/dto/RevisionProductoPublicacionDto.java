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

    // Jasper/JSP resuelve propiedades JavaBean (getX/isX), no los accesores
    // compactos que generan los records (producto(), variantes(), etc.).
    public Producto getProducto() {
        return producto;
    }

    public List<ProductoVariante> getVariantes() {
        return variantes;
    }

    public List<String> getFaltantes() {
        return faltantes;
    }

    public List<String> getAtributosFaltantes() {
        return atributosFaltantes;
    }

    public List<String> getAtributosObligatorios() {
        return atributosObligatorios;
    }

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

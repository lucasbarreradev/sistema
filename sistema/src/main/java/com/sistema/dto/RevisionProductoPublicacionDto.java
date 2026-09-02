package com.sistema.dto;

import com.sistema.model.Producto;
import com.sistema.model.ProductoVariante;
import com.sistema.dto.AtributoVarianteMl;

import java.util.List;
import java.util.Map;

public record RevisionProductoPublicacionDto(
        Producto producto,
        List<ProductoVariante> variantes,
        List<String> faltantes,
        List<String> atributosFaltantes,
        List<String> atributosObligatorios,
        List<AtributoVarianteMl> atributosGenerales,
        List<AtributoVarianteMl> atributosDeVariante,
        Map<String, String> valoresAtributosGenerales,
        Map<Long, Map<String, String>> valoresAtributosVariantes) {

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

    public List<AtributoVarianteMl> getAtributosGenerales() {
        return atributosGenerales;
    }

    public List<AtributoVarianteMl> getAtributosDeVariante() {
        return atributosDeVariante;
    }

    public Map<String, String> getValoresAtributosGenerales() {
        return valoresAtributosGenerales;
    }

    public Map<Long, Map<String, String>> getValoresAtributosVariantes() {
        return valoresAtributosVariantes;
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

    public boolean isFaltaCategoriaMercadoLibre() {
        return faltantes != null
                && faltantes.contains("Categoría de Mercado Libre");
    }
}

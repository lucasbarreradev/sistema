package com.sistema.dto;

import java.util.List;

public record AtributoVarianteMl(
        String id,
        String nombre,
        String tipo,
        List<String> valores,
        List<String> unidades,
        String unidadPredeterminada,
        boolean obligatorio) {
    public String getId() { return id; }
    public String getNombre() { return nombre; }
    public String getTipo() { return tipo; }
    public List<String> getValores() { return valores; }
    public List<String> getUnidades() { return unidades; }
    public String getUnidadPredeterminada() { return unidadPredeterminada; }
    public boolean isObligatorio() { return obligatorio; }
}

package com.sistema.dto;

import java.util.List;

public record AtributoProductoMl(String id, String nombre, String tipo, List<Valor> valores,
                                 List<String> unidades, String unidadPredeterminada, boolean obligatorio) {
    public String getId() { return id; }
    public String getNombre() { return nombre; }
    public String getTipo() { return tipo; }
    public List<Valor> getValores() { return valores; }
    public List<String> getUnidades() { return unidades; }
    public String getUnidadPredeterminada() { return unidadPredeterminada; }
    public boolean isObligatorio() { return obligatorio; }

    public record Valor(String id, String nombre) {
        public String getId() { return id; }
        public String getNombre() { return nombre; }
        public String getValorFormulario() { return (id == null ? "" : id) + "|||" + nombre; }
    }
}

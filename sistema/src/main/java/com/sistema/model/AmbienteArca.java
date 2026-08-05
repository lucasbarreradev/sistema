package com.sistema.model;

public enum AmbienteArca {
    HOMOLOGACION("Homologación (pruebas)"),
    PRODUCCION("Producción");

    private final String descripcion;

    AmbienteArca(String descripcion) { this.descripcion = descripcion; }
    public String getDescripcion() { return descripcion; }
}

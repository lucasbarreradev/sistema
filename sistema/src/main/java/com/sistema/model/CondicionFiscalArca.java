package com.sistema.model;

public enum CondicionFiscalArca {
    RESPONSABLE_INSCRIPTO("Responsable Inscripto"),
    MONOTRIBUTO("Monotributo"),
    EXENTO("Exento");

    private final String descripcion;

    CondicionFiscalArca(String descripcion) { this.descripcion = descripcion; }
    public String getDescripcion() { return descripcion; }
}

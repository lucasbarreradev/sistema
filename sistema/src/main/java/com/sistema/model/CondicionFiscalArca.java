package com.sistema.model;

public enum CondicionFiscalArca {
    RESPONSABLE_INSCRIPTO("Responsable Inscripto"),
    MONOTRIBUTO("Responsable Monotributo"),
    MONOTRIBUTISTA_SOCIAL("Monotributista Social"),
    MONOTRIBUTO_TRABAJADOR_INDEPENDIENTE_PROMOVIDO(
            "Monotributo Trabajador Independiente Promovido"),
    EXENTO("IVA Sujeto Exento"),
    IVA_NO_ALCANZADO("IVA No Alcanzado");

    private final String descripcion;

    CondicionFiscalArca(String descripcion) { this.descripcion = descripcion; }
    public String getDescripcion() { return descripcion; }
}

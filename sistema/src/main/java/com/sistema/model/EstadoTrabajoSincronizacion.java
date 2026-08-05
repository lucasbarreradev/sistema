package com.sistema.model;

public enum EstadoTrabajoSincronizacion {
    PENDIENTE("Pendiente"),
    PROCESANDO("Procesando"),
    CANCELADO("Cancelado"),
    COMPLETADA("Completada"),
    COMPLETADA_CON_ERRORES("Completada con errores"),
    ERROR("Error");

    private final String descripcion;

    EstadoTrabajoSincronizacion(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public boolean estaActivo() {
        return this == PENDIENTE || this == PROCESANDO;
    }
}

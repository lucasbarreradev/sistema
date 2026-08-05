package com.sistema.dto;

import java.util.Objects;

public class CategoriaRemotaSeleccionable {
    private final String id;
    private final String nombre;

    public CategoriaRemotaSeleccionable(String id, String nombre) {
        this.id = id == null ? "" : id.trim();
        this.nombre = nombre == null || nombre.isBlank() ? this.id : nombre.trim();
    }

    public String getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    @Override
    public boolean equals(Object otro) {
        if (this == otro) return true;
        if (!(otro instanceof CategoriaRemotaSeleccionable categoria)) return false;
        return id.equals(categoria.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

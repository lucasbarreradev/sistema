package com.sistema.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

public class ProductoRemotoSeleccionable {
    private final String idExterno;
    private final String sku;
    private final String descripcion;
    private final Integer stock;
    private final BigDecimal precio;
    private final String fotoUrl;
    private final int variantes;
    private final List<CategoriaRemotaSeleccionable> categorias;
    private final String estado;

    public ProductoRemotoSeleccionable(
            String idExterno, String sku, String descripcion, Integer stock,
            BigDecimal precio, String fotoUrl, int variantes,
            List<CategoriaRemotaSeleccionable> categorias, String estado) {
        this.idExterno = idExterno;
        this.sku = sku;
        this.descripcion = descripcion;
        this.stock = stock;
        this.precio = precio;
        this.fotoUrl = fotoUrl;
        this.variantes = variantes;
        this.categorias = categorias == null ? List.of() : List.copyOf(categorias);
        this.estado = estado == null ? "" : estado.trim();
    }

    public String getIdExterno() {
        return idExterno;
    }

    public String getSku() {
        return sku;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public Integer getStock() {
        return stock;
    }

    public BigDecimal getPrecio() {
        return precio;
    }

    public String getFotoUrl() {
        return fotoUrl;
    }

    public int getVariantes() {
        return variantes;
    }

    public List<CategoriaRemotaSeleccionable> getCategorias() {
        return categorias;
    }

    public String getCategoriaIdsFiltro() {
        return categorias.stream().map(CategoriaRemotaSeleccionable::getId)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.joining("|", "|", "|"));
    }

    public String getEstado() {
        return estado;
    }

    public String getEstadoDescripcion() {
        return switch (estado.toLowerCase(java.util.Locale.ROOT)) {
            case "active" -> "Activa";
            case "paused" -> "Pausada";
            case "closed" -> "Cerrada";
            case "pending" -> "Pendiente";
            case "under_review" -> "En revisión";
            case "not_yet_active" -> "Todavía no activa";
            case "inactive" -> "Inactiva";
            default -> estado.isBlank() ? "-" : estado;
        };
    }

    public String getEstadoClase() {
        return switch (estado.toLowerCase(java.util.Locale.ROOT)) {
            case "active" -> "badge-success";
            case "paused", "pending", "under_review", "not_yet_active" -> "badge-warning";
            case "closed", "inactive" -> "badge-secondary";
            default -> "badge-light";
        };
    }
}

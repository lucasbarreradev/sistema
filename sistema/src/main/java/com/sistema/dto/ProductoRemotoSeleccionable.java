package com.sistema.dto;

import java.math.BigDecimal;

public class ProductoRemotoSeleccionable {
    private final String idExterno;
    private final String sku;
    private final String descripcion;
    private final Integer stock;
    private final BigDecimal precio;
    private final String fotoUrl;
    private final int variantes;

    public ProductoRemotoSeleccionable(
            String idExterno, String sku, String descripcion, Integer stock,
            BigDecimal precio, String fotoUrl, int variantes) {
        this.idExterno = idExterno;
        this.sku = sku;
        this.descripcion = descripcion;
        this.stock = stock;
        this.precio = precio;
        this.fotoUrl = fotoUrl;
        this.variantes = variantes;
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
}

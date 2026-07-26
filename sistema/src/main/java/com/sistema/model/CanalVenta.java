package com.sistema.model;

public enum CanalVenta {
    WOOCOMMERCE("WooCommerce"),
    MERCADO_LIBRE("Mercado Libre"),
    TIENDANUBE("Tiendanube");

    private final String descripcion;

    CanalVenta(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }
}

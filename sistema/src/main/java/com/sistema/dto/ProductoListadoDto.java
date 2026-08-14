package com.sistema.dto;

import java.math.BigDecimal;

public class ProductoListadoDto {
    private final Long id;
    private final String sku;
    private final String descripcion;
    private final int stockTotal;
    private final boolean tieneVariantes;
    private final boolean tieneFoto;
    private final String proveedorNombre;
    private final String precioContadoListado;
    private final String precioTarjetaListado;
    private final String precioCuentaCorrienteListado;

    public ProductoListadoDto(ProductoListadoProjection producto) {
        this.id = producto.getId();
        this.sku = producto.getSku();
        this.descripcion = producto.getDescripcion();
        long variantes = valor(producto.getCantidadVariantes());
        this.tieneVariantes = variantes > 0;
        this.stockTotal = tieneVariantes
                ? Math.toIntExact(valor(producto.getStockVariantes()))
                : producto.getCantidad() == null ? 0 : producto.getCantidad();
        this.tieneFoto = producto.getIndicadorFoto() != null && producto.getIndicadorFoto() > 0;
        this.proveedorNombre = producto.getProveedorNombre();
        this.precioContadoListado = rango(
                producto.getPrecioContadoMinimo(), producto.getPrecioContadoMaximo());
        this.precioTarjetaListado = rango(
                producto.getPrecioTarjetaMinimo(), producto.getPrecioTarjetaMaximo());
        this.precioCuentaCorrienteListado = rango(
                producto.getPrecioCuentaCorrienteMinimo(), producto.getPrecioCuentaCorrienteMaximo());
    }

    private long valor(Long valor) {
        return valor == null ? 0 : valor;
    }

    private String rango(BigDecimal minimo, BigDecimal maximo) {
        if (minimo == null) return "-";
        String desde = minimo.stripTrailingZeros().toPlainString();
        String hasta = maximo == null ? desde : maximo.stripTrailingZeros().toPlainString();
        return desde.equals(hasta) ? desde : desde + " - " + hasta;
    }

    public Long getId() { return id; }
    public String getSku() { return sku; }
    public String getDescripcion() { return descripcion; }
    public int getStockTotal() { return stockTotal; }
    public boolean isTieneVariantes() { return tieneVariantes; }
    public boolean isTieneFoto() { return tieneFoto; }
    public String getProveedorNombre() { return proveedorNombre; }
    public String getPrecioContadoListado() { return precioContadoListado; }
    public String getPrecioTarjetaListado() { return precioTarjetaListado; }
    public String getPrecioCuentaCorrienteListado() { return precioCuentaCorrienteListado; }
}

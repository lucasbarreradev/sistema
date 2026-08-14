package com.sistema.dto;

import java.math.BigDecimal;

public interface ProductoListadoProjection {
    Long getId();
    String getSku();
    String getDescripcion();
    Integer getCantidad();
    String getProveedorNombre();
    Long getCantidadVariantes();
    Long getStockVariantes();
    BigDecimal getPrecioContadoMinimo();
    BigDecimal getPrecioContadoMaximo();
    BigDecimal getPrecioTarjetaMinimo();
    BigDecimal getPrecioTarjetaMaximo();
    BigDecimal getPrecioCuentaCorrienteMinimo();
    BigDecimal getPrecioCuentaCorrienteMaximo();
    Integer getIndicadorFoto();
}

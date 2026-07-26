package com.sistema.dto;

import java.math.BigDecimal;

public record ProductoOpcionDto(Long id, Long varianteId, String descripcion, Integer cantidad,
                                BigDecimal precioCompra, BigDecimal precioContado,
                                BigDecimal precioTarjeta, BigDecimal precioCuentaCorriente) {
}

package com.sistema.service;

import com.sistema.model.CanalVenta;

public record StockProductoCambiadoEvent(Long productoId, CanalVenta canalOrigen) {
    public StockProductoCambiadoEvent(Long productoId) {
        this(productoId, null);
    }
}

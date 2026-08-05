package com.sistema.service.canal;

import com.sistema.dto.ProductoCanalImportado;
import com.sistema.model.CanalVenta;

import java.util.List;
import java.util.function.BooleanSupplier;

public interface ImportadorCanal {
    CanalVenta canal();
    boolean configurado();
    List<ProductoCanalImportado> obtenerProductos();

    default List<ProductoCanalImportado> obtenerProductos(boolean incluirInactivas) {
        return obtenerProductos();
    }

    default List<ProductoCanalImportado> obtenerProductos(
            boolean incluirInactivas, BooleanSupplier cancelacionSolicitada) {
        if (cancelacionSolicitada.getAsBoolean()) return List.of();
        return obtenerProductos(incluirInactivas);
    }
}

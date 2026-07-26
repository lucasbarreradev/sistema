package com.sistema.service.canal;

import com.sistema.dto.ProductoCanalImportado;
import com.sistema.model.CanalVenta;

import java.util.List;

public interface ImportadorCanal {
    CanalVenta canal();
    boolean configurado();
    List<ProductoCanalImportado> obtenerProductos();
}

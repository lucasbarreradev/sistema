package com.sistema.service.canal;

import com.sistema.model.CanalVenta;
import com.sistema.model.Producto;

public interface PublicadorCanal {
    CanalVenta canal();
    boolean configurado();
    ResultadoPublicacion publicar(Producto producto, String idExternoActual);
}

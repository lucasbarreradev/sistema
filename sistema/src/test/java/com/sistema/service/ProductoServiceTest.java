package com.sistema.service;

import com.sistema.model.Producto;
import com.sistema.repository.*;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class ProductoServiceTest {

    @Test
    void generarSkuSaltaNumerosOcupadosAunqueElConteoTengaHuecos() {
        ProductoRepository productos = mock(ProductoRepository.class);
        when(productos.countBySkuPrefix("REME")).thenReturn(10L);
        Producto ocupado = new Producto(); ocupado.setSku("REME-011");
        when(productos.findBySkuIgnoreCase("REME-011")).thenReturn(Optional.of(ocupado));
        when(productos.findBySkuIgnoreCase("REME-012")).thenReturn(Optional.empty());
        ProductoService service = new ProductoService(productos, mock(ProveedorRepository.class),
                mock(MovimientoInventarioRepository.class), mock(PresupuestoService.class),
                mock(PublicacionCanalRepository.class), mock(ProductoVarianteRepository.class));

        assertEquals("REME-012", service.generarSku("Remera deportiva"));
    }
}

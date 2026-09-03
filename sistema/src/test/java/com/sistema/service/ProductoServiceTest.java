package com.sistema.service;

import com.sistema.model.Producto;
import com.sistema.dto.ProductoListadoProjection;
import com.sistema.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;
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

    @Test
    void listadoLivianoCalculaStockRangoDePreciosYFotoSinEntidadesCompletas() {
        ProductoRepository productos = mock(ProductoRepository.class);
        ProductoListadoProjection fila = mock(ProductoListadoProjection.class);
        PageRequest pagina = PageRequest.of(0, 50);
        when(fila.getId()).thenReturn(8L);
        when(fila.getSku()).thenReturn("ZAPA-008");
        when(fila.getDescripcion()).thenReturn("Zapatilla");
        when(fila.getCantidadVariantes()).thenReturn(2L);
        when(fila.getStockVariantes()).thenReturn(7L);
        when(fila.getIndicadorFoto()).thenReturn(1);
        when(fila.getPrecioContadoMinimo()).thenReturn(new BigDecimal("100.00"));
        when(fila.getPrecioContadoMaximo()).thenReturn(new BigDecimal("120.00"));
        when(productos.buscarPaginaListado("zapa", pagina))
                .thenReturn(new PageImpl<>(List.of(fila), pagina, 1));
        ProductoService service = new ProductoService(productos, mock(ProveedorRepository.class),
                mock(MovimientoInventarioRepository.class), mock(PresupuestoService.class),
                mock(PublicacionCanalRepository.class), mock(ProductoVarianteRepository.class));

        var resultado = service.getProductosListado("  zapa  ", pagina).getContent().get(0);

        assertEquals(7, resultado.getStockTotal());
        assertEquals("100 - 120", resultado.getPrecioContadoListado());
        assertEquals(true, resultado.isTieneVariantes());
        assertEquals(true, resultado.isTieneFoto());
        verify(productos).buscarPaginaListado("zapa", pagina);
    }

    @Test
    void obtieneTodosLosIdsQueCoincidenConLaBusquedaSinLimiteDePagina() {
        ProductoRepository productos = mock(ProductoRepository.class);
        when(productos.buscarIdsListado("neumatico")).thenReturn(List.of(4L, 9L, 15L));
        ProductoService service = new ProductoService(productos, mock(ProveedorRepository.class),
                mock(MovimientoInventarioRepository.class), mock(PresupuestoService.class),
                mock(PublicacionCanalRepository.class), mock(ProductoVarianteRepository.class));

        List<Long> ids = service.getIdsProductosListado("  neumatico  ");

        assertEquals(List.of(4L, 9L, 15L), ids);
        verify(productos).buscarIdsListado("neumatico");
    }

    @Test
    void fijaLaCategoriaCuandoSeCambiaDesdeEditarProducto() {
        ProductoRepository productos = mock(ProductoRepository.class);
        ProductoVarianteRepository variantes = mock(ProductoVarianteRepository.class);
        PresupuestoService presupuestos = mock(PresupuestoService.class);
        Producto existente = new Producto();
        existente.setId(7L);
        existente.setMercadoLibreCategoriaId("MLA410902");
        Producto editado = new Producto();
        editado.setDescripcion("Botellón Suavizante/Jabón Liquido");
        editado.setMercadoLibreCategoriaId("MLA412620");
        when(productos.findById(7L)).thenReturn(Optional.of(existente));
        when(productos.save(existente)).thenReturn(existente);
        when(variantes.existsByProductoId(7L)).thenReturn(false);
        ProductoService service = new ProductoService(productos,
                mock(ProveedorRepository.class), mock(MovimientoInventarioRepository.class),
                presupuestos, mock(PublicacionCanalRepository.class), variantes);

        Producto guardado = service.updateProducto(7L, editado);

        assertEquals("MLA412620", guardado.getMercadoLibreCategoriaId());
        assertEquals(true, guardado.getMercadoLibreCategoriaFijada());
    }
}

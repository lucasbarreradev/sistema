package com.sistema.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sistema.dto.ProductoCanalImportado;
import com.sistema.model.CanalVenta;
import com.sistema.model.ProductoCatalogoCanal;
import com.sistema.repository.ProductoCatalogoCanalRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

class CatalogoImportacionServiceTest {

    @Test
    void guardaElProductoCompletoYPermiteImportarSoloElSeleccionado() {
        ProductoCatalogoCanalRepository repository =
                mock(ProductoCatalogoCanalRepository.class);
        when(repository.findByCanalOrderByDescripcionAsc(CanalVenta.MERCADO_LIBRE))
                .thenReturn(List.of());
        CatalogoImportacionService service =
                new CatalogoImportacionService(repository, new ObjectMapper());
        ProductoCanalImportado primero = producto("MLA1", "SKU-1");
        ProductoCanalImportado segundo = producto("MLA2", "SKU-2");

        service.guardar(CanalVenta.MERCADO_LIBRE, List.of(primero, segundo));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<ProductoCatalogoCanal>> captor =
                ArgumentCaptor.forClass(Iterable.class);
        verify(repository).saveAll(captor.capture());
        List<ProductoCatalogoCanal> guardados =
                ((Collection<ProductoCatalogoCanal>) captor.getValue()).stream().toList();
        assertEquals(2, guardados.size());
        ProductoCatalogoCanal entidadSegundo = guardados.stream()
                .filter(producto -> "MLA2".equals(producto.getIdExterno()))
                .findFirst().orElseThrow();
        assertTrue(entidadSegundo.getProductoJson().contains("\"SKU-2\""));

        when(repository.findByCanalAndIdExternoIn(
                eq(CanalVenta.MERCADO_LIBRE), anyCollection()))
                .thenReturn(List.of(entidadSegundo));

        assertEquals(List.of(segundo),
                service.seleccionar(CanalVenta.MERCADO_LIBRE, List.of("MLA2")));
    }

    @Test
    void conservaRegistrosExistentesYEliminaLosQueYaNoEstanEnElCanal() {
        ProductoCatalogoCanalRepository repository =
                mock(ProductoCatalogoCanalRepository.class);
        ProductoCatalogoCanal vigente = entidad("MLA1");
        ProductoCatalogoCanal eliminado = entidad("MLA-ELIMINADO");
        when(repository.findByCanalOrderByDescripcionAsc(CanalVenta.MERCADO_LIBRE))
                .thenReturn(List.of(vigente, eliminado));
        CatalogoImportacionService service =
                new CatalogoImportacionService(repository, new ObjectMapper());

        service.guardar(CanalVenta.MERCADO_LIBRE, List.of(producto("MLA1", "SKU-NUEVO")));

        verify(repository).deleteAll(List.of(eliminado));
        assertEquals("SKU-NUEVO", vigente.getSku());
    }

    private ProductoCatalogoCanal entidad(String idExterno) {
        ProductoCatalogoCanal entidad = new ProductoCatalogoCanal();
        entidad.setCanal(CanalVenta.MERCADO_LIBRE);
        entidad.setIdExterno(idExterno);
        return entidad;
    }

    private ProductoCanalImportado producto(String idExterno, String sku) {
        return new ProductoCanalImportado(
                idExterno, sku, "Producto " + sku, 4, new BigDecimal("1500"),
                null, null, Map.of("marca", "Prueba"), List.of());
    }
}

package com.sistema.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProductoVarianteTest {

    @Test
    void reemplazaPresentacionUnicaPorElNombreDelProductoAlMostrar() {
        Producto producto = new Producto();
        producto.setDescripcion("Aceite aromatizante Black vetiver");
        ProductoVariante variante = new ProductoVariante();
        variante.setProducto(producto);
        variante.setNombre("Presentación única");

        assertEquals("Aceite aromatizante Black vetiver", variante.getNombreMostrar());
    }

    @Test
    void conservaUnNombreDePresentacionReal() {
        ProductoVariante variante = new ProductoVariante();
        variante.setNombre("Black vetiver");

        assertEquals("Black vetiver", variante.getNombreMostrar());
    }
}

package com.sistema.service.canal;

import com.sistema.model.Producto;
import com.sistema.model.ProductoVariante;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FotoCanalHelperTest {
    @Test
    void generaUnaUrlPublicaConExtensionSegunElTipoDeImagen() {
        Producto producto = new Producto();
        producto.setId(12L);
        producto.setFotoContenido(new byte[]{1});
        producto.setFotoTipoContenido("image/jpeg");

        assertEquals("https://sistema.test/productos/12/foto/producto-12.jpg",
                FotoCanalHelper.resolverUrl(producto, "https://sistema.test/"));
    }

    @Test
    void generaUrlDeVarianteConNombreYExtensionAceptadosPorWordpress() {
        Producto producto = new Producto();
        producto.setId(12L);
        ProductoVariante variante = new ProductoVariante();
        variante.setId(7L);
        variante.setProducto(producto);
        variante.setFotoContenido(new byte[]{1});
        variante.setFotoTipoContenido("image/png");

        assertEquals("https://sistema.test/productos/12/variantes/7/foto/variante-7.png",
                FotoCanalHelper.resolverUrl(variante, "https://sistema.test/"));
    }
}

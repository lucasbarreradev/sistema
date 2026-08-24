package com.sistema.service;

import com.sistema.model.Producto;
import com.sistema.model.ProductoVariante;
import com.sistema.repository.ProductoRepository;
import com.sistema.repository.ProductoVarianteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.List;

@Service
public class EdicionMasivaPrecioService {
    private static final BigDecimal MENOS_CIEN = new BigDecimal("-100");
    private static final BigDecimal MAXIMO = new BigDecimal("1000");

    private final ProductoRepository productoRepository;
    private final ProductoVarianteRepository productoVarianteRepository;

    public EdicionMasivaPrecioService(ProductoRepository productoRepository,
                                      ProductoVarianteRepository productoVarianteRepository) {
        this.productoRepository = productoRepository;
        this.productoVarianteRepository = productoVarianteRepository;
    }

    @Transactional
    public ResultadoEdicionMasiva ajustarProductos(
            Collection<Long> productoIds, BigDecimal porcentaje) {
        validarPorcentaje(porcentaje);
        if (productoIds == null || productoIds.isEmpty()) {
            throw new IllegalArgumentException("Seleccione al menos un producto");
        }

        List<Producto> productos = productoRepository.findAllById(productoIds);
        if (productos.isEmpty()) {
            throw new IllegalArgumentException("No se encontraron los productos seleccionados");
        }

        int variantesAjustadas = 0;
        for (Producto producto : productos) {
            producto.setPrecioContado(ajustar(producto.getPrecioContado(), porcentaje));
            producto.setPrecioTarjeta(ajustar(producto.getPrecioTarjeta(), porcentaje));
            producto.setPrecioCuentaCorriente(
                    ajustar(producto.getPrecioCuentaCorriente(), porcentaje));

            List<ProductoVariante> variantes =
                    productoVarianteRepository.findByProductoIdOrderByNombreAsc(producto.getId());
            for (ProductoVariante variante : variantes) {
                variante.setPrecioContado(ajustar(variante.getPrecioContado(), porcentaje));
                variante.setPrecioTarjeta(ajustar(variante.getPrecioTarjeta(), porcentaje));
                variante.setPrecioCuentaCorriente(
                        ajustar(variante.getPrecioCuentaCorriente(), porcentaje));
            }
            if (!variantes.isEmpty()) {
                productoVarianteRepository.saveAll(variantes);
                variantesAjustadas += variantes.size();
            }
        }
        productoRepository.saveAll(productos);
        return new ResultadoEdicionMasiva(productos.size(), variantesAjustadas);
    }

    BigDecimal ajustar(BigDecimal precio, BigDecimal porcentaje) {
        if (precio == null) return null;
        BigDecimal factor = BigDecimal.ONE.add(
                porcentaje.divide(new BigDecimal("100"), 8, RoundingMode.HALF_UP));
        return precio.multiply(factor).setScale(2, RoundingMode.HALF_UP);
    }

    private void validarPorcentaje(BigDecimal porcentaje) {
        if (porcentaje == null) {
            throw new IllegalArgumentException("Ingrese el porcentaje de ajuste");
        }
        if (porcentaje.compareTo(MENOS_CIEN) <= 0) {
            throw new IllegalArgumentException("La reducción debe ser menor al 100%");
        }
        if (porcentaje.compareTo(MAXIMO) > 0) {
            throw new IllegalArgumentException("El aumento no puede superar el 1000%");
        }
    }

    public record ResultadoEdicionMasiva(int productos, int variantes) {
    }
}

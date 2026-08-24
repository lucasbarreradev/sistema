package com.sistema.service;

import com.sistema.model.MovimientoInventario;
import com.sistema.model.Producto;
import com.sistema.model.ProductoVariante;
import com.sistema.repository.MovimientoInventarioRepository;
import com.sistema.repository.ProductoRepository;
import com.sistema.repository.ProductoVarianteRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Service
public class EdicionMasivaStockService {
    private static final int STOCK_MAXIMO = 1_000_000;

    private final ProductoRepository productoRepository;
    private final ProductoVarianteRepository varianteRepository;
    private final MovimientoInventarioRepository movimientoRepository;
    private final ApplicationEventPublisher eventPublisher;

    public EdicionMasivaStockService(
            ProductoRepository productoRepository,
            ProductoVarianteRepository varianteRepository,
            MovimientoInventarioRepository movimientoRepository,
            ApplicationEventPublisher eventPublisher) {
        this.productoRepository = productoRepository;
        this.varianteRepository = varianteRepository;
        this.movimientoRepository = movimientoRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public ResultadoEdicionMasiva fijarStock(
            Collection<Long> productoIds, Integer nuevoStock) {
        return ajustarStock(productoIds, nuevoStock, Operacion.FIJAR);
    }

    @Transactional
    public ResultadoEdicionMasiva ajustarStock(
            Collection<Long> productoIds, Integer cantidad, Operacion operacion) {
        if (productoIds == null || productoIds.stream().noneMatch(id -> id != null)) {
            throw new IllegalArgumentException("Seleccione al menos un producto");
        }
        if (cantidad == null) {
            throw new IllegalArgumentException("Ingrese la cantidad de stock");
        }
        if (cantidad < 0 || cantidad > STOCK_MAXIMO) {
            throw new IllegalArgumentException(
                    "La cantidad debe estar entre 0 y " + STOCK_MAXIMO);
        }
        if (operacion == null) operacion = Operacion.FIJAR;

        List<Long> ids = productoIds.stream().filter(id -> id != null).distinct().toList();
        List<Producto> productos = productoRepository.findAllById(ids);
        if (productos.isEmpty()) {
            throw new IllegalArgumentException("No se encontraron los productos seleccionados");
        }

        List<Producto> modificados = new ArrayList<>();
        List<ProductoVariante> variantesModificadas = new ArrayList<>();
        List<MovimientoInventario> movimientos = new ArrayList<>();
        int sinCambios = 0;
        LocalDateTime ahora = LocalDateTime.now();
        for (Producto producto : productos) {
            List<ProductoVariante> variantes =
                    varianteRepository.findByProductoIdOrderByNombreAsc(producto.getId());
            boolean productoCambiado = false;
            if (variantes.isEmpty()) {
                int anterior = valorStock(producto.getCantidad());
                int posterior = calcularStock(anterior, cantidad, operacion);
                if (anterior == posterior) {
                    sinCambios++;
                } else {
                    producto.setCantidad(posterior);
                    movimientos.add(crearMovimiento(
                            producto, null, anterior, posterior, ahora));
                    productoCambiado = true;
                }
            } else {
                for (ProductoVariante variante : variantes) {
                    int anterior = valorStock(variante.getStock());
                    int posterior = calcularStock(anterior, cantidad, operacion);
                    if (anterior == posterior) {
                        sinCambios++;
                        continue;
                    }
                    variante.setStock(posterior);
                    variantesModificadas.add(variante);
                    movimientos.add(crearMovimiento(
                            producto, variante, anterior, posterior, ahora));
                    productoCambiado = true;
                }
                long totalCalculado = variantes.stream()
                        .mapToLong(variante -> valorStock(variante.getStock())).sum();
                if (totalCalculado > Integer.MAX_VALUE) {
                    throw new IllegalArgumentException(
                            "El stock total del producto supera el máximo permitido");
                }
                int totalVariantes = (int) totalCalculado;
                if (valorStock(producto.getCantidad()) != totalVariantes) {
                    producto.setCantidad(totalVariantes);
                    productoCambiado = true;
                }
            }
            if (productoCambiado) modificados.add(producto);
        }

        if (!modificados.isEmpty()) {
            if (!variantesModificadas.isEmpty()) {
                varianteRepository.saveAll(variantesModificadas);
            }
            productoRepository.saveAll(modificados);
            if (!movimientos.isEmpty()) movimientoRepository.saveAll(movimientos);
            modificados.forEach(producto -> eventPublisher
                    .publishEvent(new StockProductoCambiadoEvent(producto.getId())));
        }
        return new ResultadoEdicionMasiva(
                modificados.size(), variantesModificadas.size(), sinCambios);
    }

    private int calcularStock(int anterior, int cantidad, Operacion operacion) {
        long resultado = switch (operacion) {
            case FIJAR -> cantidad;
            case SUMAR -> (long) anterior + cantidad;
            case RESTAR -> Math.max(0, anterior - cantidad);
        };
        if (resultado > STOCK_MAXIMO) {
            throw new IllegalArgumentException(
                    "La operación supera el stock máximo permitido de " + STOCK_MAXIMO);
        }
        return (int) resultado;
    }

    private int valorStock(Integer stock) {
        return stock == null ? 0 : stock;
    }

    private MovimientoInventario crearMovimiento(
            Producto producto, ProductoVariante variante, int anterior,
            int posterior, LocalDateTime fecha) {
        MovimientoInventario movimiento = new MovimientoInventario();
        movimiento.setProducto(producto);
        movimiento.setVariante(variante);
        movimiento.setTipo(MovimientoInventario.Tipo.AJUSTE);
        movimiento.setCantidad(Math.abs(posterior - anterior));
        movimiento.setStockPrevio(anterior);
        movimiento.setStockPosterior(posterior);
        movimiento.setFechaMovimiento(fecha);
        return movimiento;
    }

    public enum Operacion {
        FIJAR,
        SUMAR,
        RESTAR
    }

    public record ResultadoEdicionMasiva(
            int productosActualizados, int variantesActualizadas, int sinCambios) {
    }
}

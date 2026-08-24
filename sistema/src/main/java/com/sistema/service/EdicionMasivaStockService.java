package com.sistema.service;

import com.sistema.model.MovimientoInventario;
import com.sistema.model.Producto;
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
        if (productoIds == null || productoIds.stream().noneMatch(id -> id != null)) {
            throw new IllegalArgumentException("Seleccione al menos un producto");
        }
        if (nuevoStock == null) {
            throw new IllegalArgumentException("Ingrese el stock que desea asignar");
        }
        if (nuevoStock < 0 || nuevoStock > STOCK_MAXIMO) {
            throw new IllegalArgumentException(
                    "El stock debe estar entre 0 y " + STOCK_MAXIMO);
        }

        List<Long> ids = productoIds.stream().filter(id -> id != null).distinct().toList();
        List<Producto> productos = productoRepository.findAllById(ids);
        if (productos.isEmpty()) {
            throw new IllegalArgumentException("No se encontraron los productos seleccionados");
        }

        List<Producto> modificados = new ArrayList<>();
        List<MovimientoInventario> movimientos = new ArrayList<>();
        int sinCambios = 0;
        int omitidosConVariantes = 0;
        LocalDateTime ahora = LocalDateTime.now();
        for (Producto producto : productos) {
            if (varianteRepository.existsByProductoId(producto.getId())) {
                omitidosConVariantes++;
                continue;
            }
            int anterior = producto.getCantidad() == null ? 0 : producto.getCantidad();
            if (anterior == nuevoStock) {
                sinCambios++;
                continue;
            }
            producto.setCantidad(nuevoStock);
            modificados.add(producto);

            MovimientoInventario movimiento = new MovimientoInventario();
            movimiento.setProducto(producto);
            movimiento.setTipo(MovimientoInventario.Tipo.AJUSTE);
            movimiento.setCantidad(Math.abs(nuevoStock - anterior));
            movimiento.setStockPrevio(anterior);
            movimiento.setStockPosterior(nuevoStock);
            movimiento.setFechaMovimiento(ahora);
            movimientos.add(movimiento);
        }

        if (modificados.isEmpty() && sinCambios == 0 && omitidosConVariantes > 0) {
            throw new IllegalArgumentException(
                    "Los productos seleccionados tienen variantes. Edite el stock de cada variante desde Variantes");
        }
        if (!modificados.isEmpty()) {
            productoRepository.saveAll(modificados);
            movimientoRepository.saveAll(movimientos);
            modificados.forEach(producto -> eventPublisher
                    .publishEvent(new StockProductoCambiadoEvent(producto.getId())));
        }
        return new ResultadoEdicionMasiva(
                modificados.size(), sinCambios, omitidosConVariantes);
    }

    public record ResultadoEdicionMasiva(
            int actualizados, int sinCambios, int omitidosConVariantes) {
    }
}

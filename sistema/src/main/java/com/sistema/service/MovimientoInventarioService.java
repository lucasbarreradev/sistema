package com.sistema.service;

import com.sistema.model.MovimientoInventario;
import com.sistema.model.CanalVenta;
import com.sistema.model.Producto;
import com.sistema.model.ProductoVariante;
import com.sistema.repository.MovimientoInventarioRepository;
import com.sistema.repository.ProductoRepository;
import com.sistema.repository.ProductoVarianteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;

@Service
public class MovimientoInventarioService {

        private final MovimientoInventarioRepository movRepo;
        private final ProductoRepository productoRepo;
        private final ProductoVarianteRepository varianteRepo;
        private final ApplicationEventPublisher eventPublisher;

        public MovimientoInventarioService(
                MovimientoInventarioRepository movRepo,
                ProductoRepository productoRepo,
                ProductoVarianteRepository varianteRepo,
                ApplicationEventPublisher eventPublisher
        ) {
            this.movRepo = movRepo;
            this.productoRepo = productoRepo;
            this.varianteRepo = varianteRepo;
            this.eventPublisher = eventPublisher;
        }

    @Transactional
    public MovimientoInventario registrarDevolucion(
            Long productoId,
            Integer cantidad,
            String nota
    ) {
        return registrarDevolucion(productoId, null, cantidad, nota);
    }

    @Transactional
    public MovimientoInventario registrarDevolucion(Long productoId, Long varianteId, Integer cantidad, String nota) {
        Producto producto = productoRepo.findById(productoId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Producto no encontrado"));

        if (cantidad <= 0) {
            throw new IllegalArgumentException("Cantidad inválida");
        }

        MovimientoInventario mov = new MovimientoInventario();
        mov.setProducto(producto);
        ProductoVariante variante = resolverVariante(producto, varianteId);
        mov.setVariante(variante);
        mov.setTipo(MovimientoInventario.Tipo.ENTRADA);
        mov.setCantidad(cantidad);
        int stockPrevio = variante == null ? producto.getCantidad() : variante.getStock();
        mov.setStockPrevio(stockPrevio);
        mov.setFechaMovimiento(LocalDateTime.now());

        // 🔁 Devolver stock
        if (variante == null) producto.setCantidad(producto.getCantidad() + cantidad);
        else { variante.setStock(variante.getStock() + cantidad); varianteRepo.save(variante); sincronizarTotal(producto); }

        mov.setStockPosterior(variante == null ? producto.getCantidad() : variante.getStock());

        MovimientoInventario guardado = movRepo.save(mov);
        eventPublisher.publishEvent(new StockProductoCambiadoEvent(producto.getId()));
        return guardado;
    }

        @Transactional
        public MovimientoInventario registrarVenta(
                Long productoId,
                Integer cantidad,
                String nota
        ) {
            return registrarVenta(productoId, null, cantidad, nota);
        }

        @Transactional
        public MovimientoInventario registrarVenta(Long productoId, Long varianteId, Integer cantidad, String nota) {
            Producto producto = productoRepo.findById(productoId)
                    .orElseThrow(() ->
                            new IllegalArgumentException("Producto no encontrado"));

            if (cantidad <= 0) {
                throw new IllegalArgumentException("Cantidad inválida");
            }

            ProductoVariante variante = resolverVariante(producto, varianteId);
            int disponible = variante == null ? producto.getCantidad() : variante.getStock();
            if (disponible < cantidad) {
                throw new IllegalStateException("Stock insuficiente. Disponible: " + disponible
                );
            }

            MovimientoInventario mov = new MovimientoInventario();
            mov.setProducto(producto);
            mov.setVariante(variante);
            mov.setTipo(MovimientoInventario.Tipo.SALIDA);
            mov.setCantidad(cantidad);
            mov.setStockPrevio(disponible);
            mov.setFechaMovimiento(LocalDateTime.now());

            // Actualizar stock
            if (variante == null) producto.setCantidad(producto.getCantidad() - cantidad);
            else { variante.setStock(variante.getStock() - cantidad); varianteRepo.save(variante); sincronizarTotal(producto); }

            mov.setStockPosterior(variante == null ? producto.getCantidad() : variante.getStock());

            MovimientoInventario guardado = movRepo.save(mov);
            eventPublisher.publishEvent(new StockProductoCambiadoEvent(producto.getId()));
            return guardado;
        }

        @Transactional
        public MovimientoInventario registrarVentaExterna(Long productoId, Long varianteId, Integer cantidad,
                                                          String nota, CanalVenta canalOrigen) {
            Producto producto = productoRepo.findById(productoId)
                    .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado"));
            if (cantidad == null || cantidad <= 0) throw new IllegalArgumentException("Cantidad inválida");

            ProductoVariante variante = resolverVariante(producto, varianteId);
            int stockPrevio = variante == null
                    ? (producto.getCantidad() == null ? 0 : producto.getCantidad())
                    : (variante.getStock() == null ? 0 : variante.getStock());
            int stockPosterior = Math.max(0, stockPrevio - cantidad);

            MovimientoInventario mov = new MovimientoInventario();
            mov.setProducto(producto);
            mov.setVariante(variante);
            mov.setTipo(MovimientoInventario.Tipo.SALIDA);
            mov.setCantidad(cantidad);
            mov.setStockPrevio(stockPrevio);
            mov.setStockPosterior(stockPosterior);
            mov.setFechaMovimiento(LocalDateTime.now());

            if (variante == null) {
                producto.setCantidad(stockPosterior);
                productoRepo.save(producto);
            } else {
                variante.setStock(stockPosterior);
                varianteRepo.save(variante);
                sincronizarTotal(producto);
            }

            MovimientoInventario guardado = movRepo.save(mov);
            eventPublisher.publishEvent(new StockProductoCambiadoEvent(producto.getId(), canalOrigen));
            return guardado;
        }

        private ProductoVariante resolverVariante(Producto producto, Long varianteId) {
            if (varianteId == null || varianteId == 0) {
                if (varianteRepo.existsByProductoId(producto.getId())) throw new IllegalArgumentException("Debe seleccionar una variante");
                return null;
            }
            ProductoVariante variante = varianteRepo.findById(varianteId)
                    .orElseThrow(() -> new IllegalArgumentException("Variante no encontrada"));
            if (!variante.getProducto().getId().equals(producto.getId())) throw new IllegalArgumentException("La variante no pertenece al producto");
            return variante;
        }

        private void sincronizarTotal(Producto producto) {
            producto.setCantidad(varianteRepo.findByProductoIdOrderByNombreAsc(producto.getId()).stream()
                    .mapToInt(v -> v.getStock() == null ? 0 : v.getStock()).sum());
            productoRepo.save(producto);
        }


    }


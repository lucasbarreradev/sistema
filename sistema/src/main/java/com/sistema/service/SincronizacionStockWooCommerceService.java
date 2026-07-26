package com.sistema.service;

import com.sistema.model.CanalVenta;
import com.sistema.model.EstadoPublicacion;
import com.sistema.model.Producto;
import com.sistema.model.PublicacionCanal;
import com.sistema.repository.ProductoRepository;
import com.sistema.repository.PublicacionCanalRepository;
import com.sistema.service.canal.WooCommercePublicador;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;

@Service
public class SincronizacionStockWooCommerceService {
    private final ProductoRepository productoRepository;
    private final PublicacionCanalRepository publicacionRepository;
    private final WooCommercePublicador wooCommercePublicador;

    public SincronizacionStockWooCommerceService(ProductoRepository productoRepository,
                                                  PublicacionCanalRepository publicacionRepository,
                                                  WooCommercePublicador wooCommercePublicador) {
        this.productoRepository = productoRepository;
        this.publicacionRepository = publicacionRepository;
        this.wooCommercePublicador = wooCommercePublicador;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sincronizar(StockProductoCambiadoEvent evento) {
        if (evento.canalOrigen() == CanalVenta.WOOCOMMERCE) return;
        Producto producto = productoRepository.findById(evento.productoId()).orElse(null);
        if (producto == null) return;
        PublicacionCanal publicacion = publicacionRepository
                .findByProductoIdAndCanal(producto.getId(), CanalVenta.WOOCOMMERCE).orElse(null);
        if (publicacion == null || publicacion.getIdExterno() == null || publicacion.getIdExterno().isBlank()) return;

        try {
            wooCommercePublicador.sincronizarStock(producto, publicacion.getIdExterno());
            if (publicacion.getEstado() == null || publicacion.getEstado() == EstadoPublicacion.ERROR) {
                publicacion.setEstado(EstadoPublicacion.PUBLICADO);
            }
            publicacion.setUltimoError(null);
        } catch (Exception e) {
            publicacion.setEstado(EstadoPublicacion.ERROR);
            publicacion.setUltimoError("No se pudo sincronizar el stock con WooCommerce: " + mensajeSeguro(e));
        }
        publicacion.setFechaActualizacion(LocalDateTime.now());
        publicacionRepository.save(publicacion);
    }

    private String mensajeSeguro(Exception e) {
        String mensaje = e.getMessage();
        if (mensaje == null || mensaje.isBlank()) mensaje = e.getClass().getSimpleName();
        return mensaje.length() > 1800 ? mensaje.substring(0, 1800) : mensaje;
    }
}

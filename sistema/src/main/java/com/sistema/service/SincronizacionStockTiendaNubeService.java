package com.sistema.service;

import com.sistema.model.*;
import com.sistema.repository.ProductoRepository;
import com.sistema.repository.PublicacionCanalRepository;
import com.sistema.service.canal.TiendanubePublicador;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;

@Service
public class SincronizacionStockTiendaNubeService {
    private final ProductoRepository productoRepository;
    private final PublicacionCanalRepository publicacionRepository;
    private final TiendanubePublicador publicador;

    public SincronizacionStockTiendaNubeService(ProductoRepository productoRepository,
                                                PublicacionCanalRepository publicacionRepository,
                                                TiendanubePublicador publicador) {
        this.productoRepository = productoRepository;
        this.publicacionRepository = publicacionRepository;
        this.publicador = publicador;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sincronizar(StockProductoCambiadoEvent evento) {
        if (evento.canalOrigen() == CanalVenta.TIENDANUBE || !publicador.configurado()) return;
        Producto producto = productoRepository.findById(evento.productoId()).orElse(null);
        if (producto == null) return;
        PublicacionCanal publicacion = publicacionRepository
                .findByProductoIdAndCanal(producto.getId(), CanalVenta.TIENDANUBE).orElse(null);
        if (publicacion == null || publicacion.getIdExterno() == null || publicacion.getIdExterno().isBlank()) return;
        try {
            publicador.sincronizarStock(producto, publicacion.getIdExterno());
            if (publicacion.getEstado() == null || publicacion.getEstado() == EstadoPublicacion.ERROR) {
                publicacion.setEstado(EstadoPublicacion.PUBLICADO);
            }
            publicacion.setUltimoError(null);
        } catch (Exception e) {
            publicacion.setEstado(EstadoPublicacion.ERROR);
            publicacion.setUltimoError("No se pudo sincronizar el stock con Tiendanube: " + mensajeSeguro(e));
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

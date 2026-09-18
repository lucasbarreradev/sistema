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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

@Service
public class SincronizacionStockTiendaNubeService {
    private static final Logger log = LoggerFactory.getLogger(SincronizacionStockTiendaNubeService.class);
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
            log.error("Error técnico al sincronizar stock con Tiendanube para el producto {}. Respuesta completa: {}",
                    producto.getId(), MensajeErrorIntegracion.detalleTecnico(e), e);
            publicacion.setEstado(EstadoPublicacion.ERROR);
            publicacion.setUltimoError("No se pudo sincronizar el stock con Tiendanube: "
                    + MensajeErrorIntegracion.paraUsuario(CanalVenta.TIENDANUBE, e));
        }
        publicacion.setFechaActualizacion(LocalDateTime.now());
        publicacionRepository.save(publicacion);
    }

}

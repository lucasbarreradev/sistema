package com.sistema.service;

import com.sistema.model.CanalVenta;
import com.sistema.model.EstadoPublicacion;
import com.sistema.model.Producto;
import com.sistema.model.PublicacionCanal;
import com.sistema.repository.ProductoRepository;
import com.sistema.repository.PublicacionCanalRepository;
import com.sistema.service.canal.MercadoLibrePublicador;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class SincronizacionStockMercadoLibreService {
    private final ProductoRepository productoRepository;
    private final PublicacionCanalRepository publicacionRepository;
    private final MercadoLibrePublicador mercadoLibrePublicador;

    public SincronizacionStockMercadoLibreService(ProductoRepository productoRepository,
                                                   PublicacionCanalRepository publicacionRepository,
                                                   MercadoLibrePublicador mercadoLibrePublicador) {
        this.productoRepository = productoRepository;
        this.publicacionRepository = publicacionRepository;
        this.mercadoLibrePublicador = mercadoLibrePublicador;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sincronizar(StockProductoCambiadoEvent evento) {
        if (evento.canalOrigen() == CanalVenta.MERCADO_LIBRE) return;
        Producto producto = productoRepository.findById(evento.productoId()).orElse(null);
        if (producto == null) return;

        Optional<PublicacionCanal> encontrada = publicacionRepository
                .findByProductoIdAndCanal(producto.getId(), CanalVenta.MERCADO_LIBRE);
        String idExterno = encontrada.map(PublicacionCanal::getIdExterno)
                .filter(id -> !id.isBlank()).orElse(producto.getMercadoLibreId());
        if (idExterno == null || idExterno.isBlank()) return;

        PublicacionCanal publicacion = encontrada.orElseGet(() -> nuevaPublicacion(producto, idExterno));
        try {
            mercadoLibrePublicador.sincronizarStock(producto, idExterno);
            if (publicacion.getEstado() == null || publicacion.getEstado() == EstadoPublicacion.ERROR) {
                publicacion.setEstado(EstadoPublicacion.PUBLICADO);
            }
            publicacion.setUltimoError(null);
        } catch (Exception e) {
            publicacion.setEstado(EstadoPublicacion.ERROR);
            publicacion.setUltimoError("No se pudo sincronizar el stock: " + mensajeSeguro(e));
        }
        publicacion.setFechaActualizacion(LocalDateTime.now());
        publicacionRepository.save(publicacion);
    }

    private PublicacionCanal nuevaPublicacion(Producto producto, String idExterno) {
        PublicacionCanal publicacion = new PublicacionCanal();
        publicacion.setProducto(producto);
        publicacion.setCanal(CanalVenta.MERCADO_LIBRE);
        publicacion.setIdExterno(idExterno);
        return publicacion;
    }

    private String mensajeSeguro(Exception e) {
        String mensaje = e.getMessage();
        if (mensaje == null || mensaje.isBlank()) mensaje = e.getClass().getSimpleName();
        return mensaje.length() > 1850 ? mensaje.substring(0, 1850) : mensaje;
    }
}

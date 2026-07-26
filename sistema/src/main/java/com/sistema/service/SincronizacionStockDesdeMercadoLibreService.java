package com.sistema.service;

import com.sistema.dto.ProductoCanalImportado;
import com.sistema.dto.VarianteCanalImportada;
import com.sistema.model.MovimientoInventario;
import com.sistema.model.Producto;
import com.sistema.model.ProductoVariante;
import com.sistema.model.PublicacionCanal;
import com.sistema.repository.MovimientoInventarioRepository;
import com.sistema.repository.ProductoRepository;
import com.sistema.repository.ProductoVarianteRepository;
import com.sistema.repository.PublicacionCanalRepository;
import com.sistema.service.canal.MercadoLibreImportador;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class SincronizacionStockDesdeMercadoLibreService {
    private static final Logger log = LoggerFactory.getLogger(SincronizacionStockDesdeMercadoLibreService.class);

    private final PublicacionCanalRepository publicacionRepository;
    private final ProductoRepository productoRepository;
    private final ProductoVarianteRepository varianteRepository;
    private final MovimientoInventarioRepository movimientoRepository;
    private final MercadoLibreImportador importador;

    public SincronizacionStockDesdeMercadoLibreService(PublicacionCanalRepository publicacionRepository,
                                                        ProductoRepository productoRepository,
                                                        ProductoVarianteRepository varianteRepository,
                                                        MovimientoInventarioRepository movimientoRepository,
                                                        MercadoLibreImportador importador) {
        this.publicacionRepository = publicacionRepository;
        this.productoRepository = productoRepository;
        this.varianteRepository = varianteRepository;
        this.movimientoRepository = movimientoRepository;
        this.importador = importador;
    }

    @Transactional
    public void sincronizarPublicacion(Long publicacionId) {
        PublicacionCanal publicacion = publicacionRepository.findWithProductoById(publicacionId).orElse(null);
        if (publicacion == null || publicacion.getIdExterno() == null) return;
        ProductoCanalImportado remoto = importador.obtenerProducto(publicacion.getIdExterno());
        Producto producto = publicacion.getProducto();

        if (remoto.variantes() == null || remoto.variantes().isEmpty()) {
            ajustarProducto(producto, Optional.ofNullable(remoto.cantidad()).orElse(0), publicacion.getIdExterno());
        } else {
            for (VarianteCanalImportada dato : remoto.variantes()) ajustarVariante(producto, dato, publicacion.getIdExterno());
            producto.setCantidad(varianteRepository.findByProductoIdOrderByNombreAsc(producto.getId()).stream()
                    .mapToInt(v -> Optional.ofNullable(v.getStock()).orElse(0)).sum());
            productoRepository.save(producto);
        }

        publicacion.setUltimoError(null);
        publicacion.setFechaActualizacion(LocalDateTime.now());
        publicacionRepository.save(publicacion);
    }

    private void ajustarProducto(Producto producto, int stockNuevo, String itemId) {
        int stockAnterior = Optional.ofNullable(producto.getCantidad()).orElse(0);
        if (stockAnterior == stockNuevo) return;
        producto.setCantidad(stockNuevo);
        productoRepository.save(producto);
        registrarMovimiento(producto, null, stockAnterior, stockNuevo, itemId);
    }

    private void ajustarVariante(Producto producto, VarianteCanalImportada dato, String itemId) {
        Optional<ProductoVariante> encontrada = varianteRepository
                .findByProductoIdAndMercadoLibreVariationId(producto.getId(), dato.idExterno());
        if (encontrada.isEmpty() && dato.sku() != null && !dato.sku().isBlank()) {
            encontrada = varianteRepository.findBySkuIgnoreCase(dato.sku());
        }
        if (encontrada.isEmpty()) return;
        ProductoVariante variante = encontrada.get();
        int anterior = Optional.ofNullable(variante.getStock()).orElse(0);
        int nuevo = Optional.ofNullable(dato.stock()).orElse(0);
        if (anterior == nuevo) return;
        variante.setStock(nuevo);
        varianteRepository.save(variante);
        registrarMovimiento(producto, variante, anterior, nuevo, itemId);
    }

    private void registrarMovimiento(Producto producto, ProductoVariante variante, int anterior, int nuevo, String itemId) {
        MovimientoInventario movimiento = new MovimientoInventario();
        movimiento.setProducto(producto);
        movimiento.setVariante(variante);
        movimiento.setTipo(MovimientoInventario.Tipo.AJUSTE);
        movimiento.setCantidad(Math.abs(nuevo - anterior));
        movimiento.setStockPrevio(anterior);
        movimiento.setStockPosterior(nuevo);
        movimiento.setFechaMovimiento(LocalDateTime.now());
        movimientoRepository.save(movimiento);
        log.info("Stock actualizado desde Mercado Libre {}: {} -> {}", itemId, anterior, nuevo);
    }

    @Transactional
    public void registrarError(Long publicacionId, Exception e) {
        publicacionRepository.findById(publicacionId).ifPresent(publicacion -> {
            publicacion.setUltimoError("No se pudo traer el stock desde Mercado Libre: " + mensajeSeguro(e));
            publicacion.setFechaActualizacion(LocalDateTime.now());
            publicacionRepository.save(publicacion);
        });
    }

    private String mensajeSeguro(Exception e) {
        String mensaje = e.getMessage();
        if (mensaje == null || mensaje.isBlank()) mensaje = e.getClass().getSimpleName();
        return mensaje.length() > 1800 ? mensaje.substring(0, 1800) : mensaje;
    }
}

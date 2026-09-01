package com.sistema.service;

import com.sistema.dto.ResultadoPublicacionLote;
import com.sistema.model.*;
import com.sistema.repository.ProductoRepository;
import com.sistema.repository.PublicacionCanalRepository;
import com.sistema.service.canal.PublicadorCanal;
import com.sistema.service.canal.ResultadoPublicacion;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;
import com.sistema.dto.PublicacionCanalListadoDto;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.function.BooleanSupplier;

@Service
public class PublicacionService {
    private final ProductoRepository productoRepository;
    private final PublicacionCanalRepository publicacionRepository;
    private final Map<CanalVenta, PublicadorCanal> publicadores;

    public PublicacionService(ProductoRepository productoRepository,
                              PublicacionCanalRepository publicacionRepository,
                              List<PublicadorCanal> publicadores) {
        this.productoRepository = productoRepository;
        this.publicacionRepository = publicacionRepository;
        this.publicadores = publicadores.stream().collect(Collectors.toMap(PublicadorCanal::canal, p -> p));
    }

    public Map<CanalVenta, Boolean> estadoConfiguracion() {
        Map<CanalVenta, Boolean> estado = new EnumMap<>(CanalVenta.class);
        for (CanalVenta canal : CanalVenta.values()) estado.put(canal, publicadores.get(canal).configurado());
        return estado;
    }

    public List<PublicacionCanalListadoDto> historial() {
        return publicacionRepository.buscarHistorialLiviano(PageRequest.of(0, 100));
    }

    public ResultadoPublicacionLote publicar(Collection<Long> productoIds, Collection<CanalVenta> canales) {
        return publicar(productoIds, canales, () -> false);
    }

    public ResultadoPublicacionLote publicar(Collection<Long> productoIds, Collection<CanalVenta> canales,
                                             BooleanSupplier cancelacionSolicitada) {
        ResultadoPublicacionLote lote = new ResultadoPublicacionLote();
        for (Long productoId : productoIds) {
            if (cancelacionSolicitada.getAsBoolean()) return lote;
            Producto producto = productoRepository.findById(productoId).orElse(null);
            if (producto == null) { lote.error("Producto " + productoId + ": no encontrado"); continue; }
            if (Optional.ofNullable(producto.getCantidad()).orElse(0) <= 0) continue;
            for (CanalVenta canal : canales) {
                if (cancelacionSolicitada.getAsBoolean()) return lote;
                publicarUno(producto, canal, lote);
            }
        }
        return lote;
    }

    private void publicarUno(Producto producto, CanalVenta canal, ResultadoPublicacionLote lote) {
        PublicacionCanal publicacion = publicacionRepository.findByProductoIdAndCanal(producto.getId(), canal)
                .orElseGet(() -> nuevaPublicacion(producto, canal));
        try {
            ResultadoPublicacion resultado = publicadores.get(canal).publicar(producto, publicacion.getIdExterno());
            publicacion.setIdExterno(resultado.idExterno());
            publicacion.setEstado(EstadoPublicacion.PUBLICADO);
            publicacion.setUltimoError(null);
            lote.exito();
        } catch (Exception e) {
            publicacion.setEstado(EstadoPublicacion.ERROR);
            publicacion.setUltimoError(mensajeSeguro(e));
            lote.error(referenciaProducto(producto) + " / " + canal.getDescripcion() + ": " + mensajeSeguro(e));
        }
        publicacion.setFechaActualizacion(LocalDateTime.now());
        publicacionRepository.save(publicacion);
    }

    private PublicacionCanal nuevaPublicacion(Producto producto, CanalVenta canal) {
        PublicacionCanal p = new PublicacionCanal();
        p.setProducto(producto);
        p.setCanal(canal);
        return p;
    }

    private String mensajeSeguro(Exception e) {
        String mensaje = e.getMessage();
        if (mensaje == null || mensaje.isBlank()) mensaje = e.getClass().getSimpleName();
        return mensaje.length() > 1900 ? mensaje.substring(0, 1900) : mensaje;
    }

    private String referenciaProducto(Producto producto) {
        if (producto.getSku() != null && !producto.getSku().isBlank()) return producto.getSku().trim();
        if (producto.getDescripcion() != null && !producto.getDescripcion().isBlank()) {
            return producto.getDescripcion().trim();
        }
        return "Producto " + producto.getId();
    }
}

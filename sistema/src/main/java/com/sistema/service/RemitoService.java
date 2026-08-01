package com.sistema.service;

import com.sistema.model.*;
import com.sistema.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class RemitoService {

    private final RemitoRepository remitoRepo;
    private final ProductoRepository productoRepo;
    private final VentaRepository ventaRepo;
    private final MovimientoInventarioService movimientoService;

    public RemitoService(RemitoRepository remitoRepo,
                         ProductoRepository productoRepo,
                         VentaRepository ventaRepo,
                         MovimientoInventarioService movimientoService) {
        this.remitoRepo = remitoRepo;
        this.productoRepo = productoRepo;
        this.ventaRepo = ventaRepo;
        this.movimientoService = movimientoService;
    }

    // ==========================================
    // CREAR REMITO
    // ==========================================
    public Remito crear(Cliente cliente,
                        List<Long> productoIds,
                        List<Integer> cantidades,
                        List<BigDecimal> precios,
                        String direccionEntrega,
                        String observaciones,
                        Boolean incluyePrecios,
                        Boolean descontarStock) {

        Remito remito = new Remito();
        remito.setCliente(cliente);
        remito.setDireccionEntrega(direccionEntrega);
        remito.setObservaciones(observaciones);
        remito.setIncluyePrecios(incluyePrecios);
        remito.setEstado(Remito.Estado.PENDIENTE);
        remito.setCodigo(generarCodigo());

        for (int i = 0; i < productoIds.size(); i++) {
            Producto pt = productoRepo.findById(productoIds.get(i))
                    .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado"));

            RemitoItem item = new RemitoItem();
            item.setProducto(pt);
            item.setCantidad(cantidades.get(i));

            if (incluyePrecios && precios != null && i < precios.size()) {
                item.setPrecioUnitario(precios.get(i));
                item.calcularSubtotal();
            }

            remito.agregarItem(item);
        }

        if (incluyePrecios) {
            remito.calcularTotal();
        }

        Remito remitoGuardado = remitoRepo.save(remito);

        // Descontar stock si se solicita
        if (descontarStock) {
            descontarStockRemito(remitoGuardado);
        }

        return remitoGuardado;
    }

    // ==========================================
    // MARCAR COMO ENTREGADO
    // ==========================================
    public void marcarComoEntregado(Long remitoId, Boolean descontarStock) {
        Remito remito = remitoRepo.findById(remitoId)
                .orElseThrow(() -> new IllegalArgumentException("Remito no encontrado"));

        if (remito.getEstado() != Remito.Estado.PENDIENTE) {
            throw new IllegalStateException("Solo se pueden entregar remitos pendientes");
        }

        remito.setEstado(Remito.Estado.ENTREGADO);
        remitoRepo.save(remito);

        if (descontarStock) {
            descontarStockRemito(remito);
        }
    }

    // ==========================================
    // CONVERTIR A VENTA
    // ==========================================
    public Venta convertirAVenta(Long remitoId,
                                 FormaPago formaPago,
                                 Boolean descontarStock) {

        Remito remito = remitoRepo.findById(remitoId)
                .orElseThrow(() -> new IllegalArgumentException("Remito no encontrado"));

        if (remito.getEstado() == Remito.Estado.CONVERTIDO) {
            throw new IllegalStateException("Este remito ya fue convertido a venta");
        }

        if (remito.getEstado() == Remito.Estado.ANULADO) {
            throw new IllegalStateException("No se puede convertir un remito anulado");
        }

        Venta venta = new Venta();
        venta.setCliente(remito.getCliente());
        venta.setFormaPago(formaPago);
        venta.setFechaVenta(LocalDateTime.now());
        venta.setEstado(Venta.Estado.COMPLETADA);

        BigDecimal total = BigDecimal.ZERO;

        for (RemitoItem remitoItem : remito.getItems()) {
            Producto pt = remitoItem.getProducto();
            ProductoVariante variante = remitoItem.getVariante();

            BigDecimal precio = variante == null
                    ? pt.getPrecioSegunFormaPago(formaPago)
                    : variante.precio(formaPago);

            VentaItem ventaItem = new VentaItem();
            ventaItem.setProducto(pt);
            ventaItem.setVariante(variante);
            ventaItem.setCantidad(remitoItem.getCantidad());
            ventaItem.setPrecioUnitario(precio);
            ventaItem.setCostoUnitario(variante != null && variante.getPrecioCompra() != null
                    ? variante.getPrecioCompra()
                    : pt.getPrecioCompra() != null ? pt.getPrecioCompra() : BigDecimal.ZERO);
            ventaItem.setDescuentoPct(BigDecimal.ZERO);
            ventaItem.calcularSubtotal();

            venta.agregarItem(ventaItem);
            total = total.add(ventaItem.getSubtotal());
        }

        venta.setTotal(total);
        Venta ventaGuardada = ventaRepo.save(venta);

        // Descontar stock si no se descontó antes
        if (descontarStock) {
            for (VentaItem item : ventaGuardada.getItems()) {
                Producto pt = item.getProducto();
                pt.setCantidad(pt.getCantidad() - item.getCantidad());
                productoRepo.save(pt);

                movimientoService.registrarVenta(
                        pt.getId(),
                        item.getCantidad(),
                        "Venta " + ventaGuardada.getCodigo() + " (desde remito " + remito.getCodigo() + ")"
                );
            }
        }

        // Marcar remito como convertido
        remito.setEstado(Remito.Estado.CONVERTIDO);
        remito.setVenta(ventaGuardada);
        remito.setFechaConversion(LocalDate.now());
        remitoRepo.save(remito);

        return ventaGuardada;
    }

    // ==========================================
    // ANULAR REMITO
    // ==========================================
    public void anular(Long remitoId, Boolean devolverStock) {
        Remito remito = remitoRepo.findById(remitoId)
                .orElseThrow(() -> new IllegalArgumentException("Remito no encontrado"));

        if (remito.getEstado() == Remito.Estado.CONVERTIDO) {
            throw new IllegalStateException("No se puede anular un remito convertido a venta");
        }

        remito.setEstado(Remito.Estado.ANULADO);
        remitoRepo.save(remito);

        if (devolverStock && remito.getEstado() == Remito.Estado.ENTREGADO) {
            devolverStockRemito(remito);
        }
    }

    // ==========================================
    // HELPERS - STOCK
    // ==========================================
    private void descontarStockRemito(Remito remito) {
        for (RemitoItem item : remito.getItems()) {
            Producto pt = item.getProducto();
            pt.setCantidad(pt.getCantidad() - item.getCantidad());
            productoRepo.save(pt);

            movimientoService.registrarDevolucion(
                    pt.getId(),
                    item.getVariante() == null ? null : item.getVariante().getId(),
                    item.getCantidad(),
                    "Remito " + remito.getCodigo()
            );
        }
    }

    private void devolverStockRemito(Remito remito) {
        for (RemitoItem item : remito.getItems()) {
            Producto pt = item.getProducto();
            pt.setCantidad(pt.getCantidad() + item.getCantidad());
            productoRepo.save(pt);

            movimientoService.registrarVenta(
                    pt.getId(),
                    item.getVariante() == null ? null : item.getVariante().getId(),
                    item.getCantidad(),
                    "Anulación remito " + remito.getCodigo()
            );
        }
    }

    // ==========================================
// CREAR REMITO DESDE VENTA
// ==========================================
    public Remito crearDesdeVenta(Venta venta) {

        Remito remito = new Remito();
        remito.setCliente(venta.getCliente());
        remito.setEstado(Remito.Estado.ENTREGADO); // Ya está entregado (es una venta)
        remito.setTipo(Remito.Tipo.ENTREGA);
        remito.setIncluyePrecios(true);
        remito.setObservaciones("Generado desde venta " + venta.getCodigo());
        remito.setCodigo(generarCodigo());

        BigDecimal total = BigDecimal.ZERO;

        for (VentaItem ventaItem : venta.getItems()) {
            RemitoItem remitoItem = new RemitoItem();
            remitoItem.setProducto(ventaItem.getProducto());
            remitoItem.setVariante(ventaItem.getVariante());
            remitoItem.setCantidad(ventaItem.getCantidad());
            remitoItem.setPrecioUnitario(ventaItem.getPrecioUnitario());
            remitoItem.calcularSubtotal();

            remito.agregarItem(remitoItem);
            total = total.add(remitoItem.getSubtotal());
        }

        remito.setTotal(total);
        remito.setVenta(venta);

        Remito remitoGuardado = remitoRepo.save(remito);

        return remitoGuardado;
    }

    private String generarCodigo() {
        Long maxId = remitoRepo.findMaxId();
        long siguiente = (maxId != null ? maxId : 0L) + 1;
        return String.format("%04d", siguiente);
    }

    public Remito buscarPorVentaId(Long ventaId) {
        return remitoRepo.findByVentaId(ventaId).stream().findFirst().orElse(null);
    }

    // ==========================================
    // LISTADOS
    // ==========================================
    public List<Remito> listarTodos() {
        return remitoRepo.findAllByOrderByFechaEmisionDesc();
    }

    public List<Remito> listarPorEstado(Remito.Estado estado) {
        return remitoRepo.findByEstadoOrderByFechaEmisionDesc(estado);
    }

    public List<Remito> listarPorCliente(Long clienteId) {
        return remitoRepo.findByClienteIdOrderByFechaEmisionDesc(clienteId);
    }

    public Remito buscarPorId(Long id) {
        return remitoRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Remito no encontrado"));
    }


}

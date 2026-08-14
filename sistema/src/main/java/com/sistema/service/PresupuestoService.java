package com.sistema.service;

import com.sistema.model.*;
import com.sistema.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class PresupuestoService {

    private final PresupuestoRepository presupuestoRepo;
    private final ProductoRepository productoRepo;
    private final ClienteRepository clienteRepo;
    private final VentaService ventaService;
    private final VentaRepository ventaRepo;
    private final MovimientoInventarioService movimientoService;
    private final ProductoVarianteRepository varianteRepo;

    public PresupuestoService(PresupuestoRepository presupuestoRepo,
                              ProductoRepository productoRepo,
                              ClienteRepository clienteRepo,
                              VentaService ventaService,
                              VentaRepository ventaRepo,
                              MovimientoInventarioService movimientoService,
                              ProductoVarianteRepository varianteRepo) {
        this.presupuestoRepo = presupuestoRepo;
        this.productoRepo = productoRepo;
        this.clienteRepo = clienteRepo;
        this.ventaService = ventaService;
        this.ventaRepo = ventaRepo;
        this.movimientoService = movimientoService;
        this.varianteRepo = varianteRepo;
    }

    // ==========================================
    // CREAR PRESUPUESTO
    // ==========================================
    public Presupuesto crear(Long clienteId,
                             FormaPago formaPago,
                             List<Long> productoIds,
                             List<Long> varianteIds,
                             List<Integer> cantidades,
                             List<BigDecimal> descuentos,
                             LocalDate fechaValidez,
                             List<BigDecimal> precios) {

        // Validaciones
        if (productoIds == null || productoIds.isEmpty()) {
            throw new IllegalArgumentException("Debe agregar al menos un producto");
        }

        if (productoIds.size() != cantidades.size()) {
            throw new IllegalArgumentException("Datos inconsistentes");
        }

        // Buscar cliente (opcional)
        Cliente cliente = null;
        if (clienteId != null) {
            cliente = clienteRepo.findById(clienteId)
                    .orElseThrow(() -> new IllegalArgumentException("Cliente no encontrado"));
        }

        // Crear presupuesto
        Presupuesto presupuesto = new Presupuesto();
        presupuesto.setCodigo(generarCodigo());
        presupuesto.setCliente(cliente);
        presupuesto.setEstado(EstadoPresupuesto.PENDIENTE);
        presupuesto.setFecha(LocalDateTime.now());
        presupuesto.setFormaPago(formaPago);

        presupuesto.setFechaValidez(
                fechaValidez != null ? fechaValidez : LocalDate.now().plusDays(30)
        );

        presupuesto.setMoneda(Presupuesto.Moneda.ARS);
        presupuesto.setTipoCambio(null);
        presupuesto.setNotaTipoCambio(null);

        // Agregar detalles
        for (int i = 0; i < productoIds.size(); i++) {

            Long productoId = productoIds.get(i);

            Producto producto = productoRepo.findById(productoId)
                    .orElseThrow(() ->
                            new IllegalArgumentException("Producto no encontrado: " + productoId));
            ProductoVariante variante = resolverVariante(producto, varianteIds, i);

            Integer cantidad = cantidades.get(i);

            BigDecimal descuento = (descuentos != null && i < descuentos.size())
                    ? descuentos.get(i)
                    : BigDecimal.ZERO;

            BigDecimal precio;

            if (precios != null
                    && i < precios.size()
                    && precios.get(i) != null) {

                precio = precios.get(i);

            } else {

                precio = variante == null ? producto.getPrecioSegunFormaPago(formaPago) : variante.precio(formaPago);
            }

            DetallePresupuesto detalle = new DetallePresupuesto();
            detalle.setProducto(producto);
            detalle.setVariante(variante);
            detalle.setCantidad(cantidad);
            detalle.setPrecioUnitario(precio);
            detalle.setDescuentoPct(descuento);
            detalle.setAlicuotaIva(producto.getTipoIva().getPorcentaje());
            detalle.calcularSubtotal();

            presupuesto.agregarDetalle(detalle);
        }


// Calcular total
        presupuesto.calcularTotal();

        return presupuestoRepo.save(presupuesto);

    }

    // ==========================================
    // APROBAR PRESUPUESTO → CREAR VENTA
    // ==========================================
    public Venta aprobar(Long presupuestoId) {

        Presupuesto presupuesto = presupuestoRepo.findById(presupuestoId)
                .orElseThrow(() -> new IllegalArgumentException("Presupuesto no encontrado"));

        if (presupuesto.getEstado() != EstadoPresupuesto.PENDIENTE) {
            throw new IllegalArgumentException(
                    "Solo se puede aprobar un presupuesto PENDIENTE");
        }

        // 👉 Crear venta
        Venta venta = ventaService.crearDesdePresupuesto(
                presupuestoId,
                presupuesto.getFormaPago()
        );

        // 👉 Cambiar estado del presupuesto
        presupuesto.setEstado(EstadoPresupuesto.APROBADO);
        presupuestoRepo.save(presupuesto);

        return venta;
    }

    public TotalesConIva calcularTotalesConIvaMap(Presupuesto presupuesto) {

        BigDecimal netoAcum = BigDecimal.ZERO;
        BigDecimal ivaAcum = BigDecimal.ZERO;
        Map<BigDecimal, BigDecimal> ivasMap = new HashMap<>();

        for (DetallePresupuesto detalle : presupuesto.getDetalles()) {

            BigDecimal ivaRate = detalle.getAlicuotaIva(); // Ej: 21.00, 10.50, 0.00
            BigDecimal subtotal = detalle.getSubtotal();

            BigDecimal netoItem = subtotal.divide(
                    BigDecimal.ONE.add(ivaRate.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)),
                    2,
                    RoundingMode.HALF_UP
            );

            BigDecimal ivaItem = subtotal.subtract(netoItem);

            netoAcum = netoAcum.add(netoItem);
            ivaAcum = ivaAcum.add(ivaItem);

            ivasMap.merge(ivaRate, ivaItem, BigDecimal::add);
        }

        BigDecimal total = netoAcum.add(ivaAcum);

        // 👉 Si es consumidor final, no discriminar IVA
        if (presupuesto.getCliente() == null
                || presupuesto.getCliente().getCondicionIva() == null
                || !presupuesto.getCliente().getCondicionIva().isReceptorFacturaA()) {
            netoAcum = total;
            ivaAcum = BigDecimal.ZERO;
            ivasMap.clear(); // no mostramos líneas de IVA
        }

        return new TotalesConIva(netoAcum, ivaAcum, total, ivasMap);
    }


    // ==========================================
    // RECHAZAR PRESUPUESTO
    // ==========================================
    public Presupuesto rechazar(Long presupuestoId) {
        Presupuesto presupuesto = presupuestoRepo.findById(presupuestoId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Presupuesto no encontrado"));

        if (presupuesto.getEstado() != EstadoPresupuesto.PENDIENTE) {
            throw new IllegalArgumentException(
                    "Solo se puede rechazar un presupuesto PENDIENTE");
        }

        presupuesto.setEstado(EstadoPresupuesto.RECHAZADO);
        return presupuestoRepo.save(presupuesto);
    }

    @Transactional
    public void cambiarEstado(Long id, EstadoPresupuesto nuevoEstado) {

        Presupuesto p = presupuestoRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Presupuesto no encontrado"));

        if (p.getEstado() != EstadoPresupuesto.PENDIENTE) {
            throw new IllegalStateException("Solo se pueden modificar presupuestos pendientes");
        }

        p.setEstado(nuevoEstado);
    }




    // ==========================================
    // BUSCAR
    // ==========================================
    public Presupuesto buscarPorId(Long id) {
        return presupuestoRepo.findByIdConDetalles(id)
                .orElseThrow(() -> new IllegalArgumentException("Presupuesto no encontrado"));
    }

    public Presupuesto buscarPorCodigo(String codigo) {
        return presupuestoRepo.findByCodigo(codigo)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Presupuesto no encontrado: " + codigo));
    }

    public List<Presupuesto> buscarTodos() {
        return presupuestoRepo.findAllByOrderByFechaDesc();
    }

    public List<Presupuesto> buscarPorEstado(EstadoPresupuesto estado) {
        return presupuestoRepo.findByEstadoOrderByFechaDesc(estado);
    }

    public List<Presupuesto> buscarPorCliente(Long clienteId) {
        return presupuestoRepo.findByClienteIdOrderByFechaDesc(clienteId);
    }

    // ==========================================
    // GENERAR CÓDIGO SECUENCIAL
    // ==========================================
    private String generarCodigo() {
        Long ultimo = presupuestoRepo.count();
        return String.format("%04d", ultimo + 1);
    }

    // ==========================================
    // ACTUALIZAR PRESUPUESTO (antes de aprobar)
    // ==========================================
    public Presupuesto actualizar(Long id,
                                  Long clienteId,
                                  List<Long> productoIds,
                                  List<Long> varianteIds,
                                  List<Integer> cantidades,
                                  List<BigDecimal> descuentos,
                                  List<BigDecimal> precios,
                                  List<Long> actualizarPrecioProducto,
                                  FormaPago formaPago,
                                  LocalDate fechaValidez) {

        Presupuesto presupuesto = buscarPorId(id);

        if (presupuesto.getEstado() != EstadoPresupuesto.PENDIENTE &&
                presupuesto.getEstado() != EstadoPresupuesto.APROBADO) {
            throw new IllegalArgumentException(
                    "Solo se puede editar un presupuesto PENDIENTE o APROBADO");
        }

        if (presupuesto.getEstado() == EstadoPresupuesto.APROBADO) {
            anularVentaDelPresupuesto(presupuesto);
            presupuesto.setEstado(EstadoPresupuesto.PENDIENTE);
        }

        if (clienteId != null) {
            Cliente cliente = clienteRepo.findById(clienteId)
                    .orElseThrow(() -> new IllegalArgumentException("Cliente no encontrado"));
            presupuesto.setCliente(cliente);
        } else {
            presupuesto.setCliente(null);
        }

        presupuesto.setFormaPago(formaPago);
        presupuesto.getDetalles().clear();
        if (fechaValidez != null) {
            presupuesto.setFechaValidez(fechaValidez);
        }

        presupuesto.setMoneda(Presupuesto.Moneda.ARS);
        presupuesto.setTipoCambio(null);
        presupuesto.setNotaTipoCambio(null);

        presupuestoRepo.saveAndFlush(presupuesto);



        for (int i = 0; i < productoIds.size(); i++) {

            Producto producto = productoRepo.findById(productoIds.get(i))
                    .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado"));
            ProductoVariante variante = resolverVariante(producto, varianteIds, i);

            Integer cantidad = cantidades.get(i);
            if (cantidad == null || cantidad <= 0) continue;

            BigDecimal descuento = (descuentos != null && i < descuentos.size())
                    ? descuentos.get(i) : BigDecimal.ZERO;

            BigDecimal precio;
            if (precios != null && i < precios.size() && precios.get(i) != null) {
                precio = precios.get(i);
            } else {
                precio = variante == null ? producto.getPrecioSegunFormaPago(formaPago) : variante.precio(formaPago);
            }

            if (actualizarPrecioProducto != null
                    && actualizarPrecioProducto.contains(producto.getId())) {

                switch (formaPago) {

                    case CONTADO:
                        producto.setPrecioContado(precio);
                        break;

                    case TARJETA:
                        producto.setPrecioTarjeta(precio);
                        break;

                    case CUENTA_CORRIENTE:
                        producto.setPrecioCuentaCorriente(precio);
                        break;
                }

                productoRepo.save(producto);

                actualizarPrecioProductoEnPendientes(
                        producto.getId(),
                        producto.getPrecioContado(),
                        producto.getPrecioTarjeta(),
                        producto.getPrecioCuentaCorriente()
                );
            }

            DetallePresupuesto detalle = new DetallePresupuesto();
            detalle.setProducto(producto);
            detalle.setVariante(variante);
            detalle.setCantidad(cantidad);
            detalle.setPrecioUnitario(precio);
            detalle.setDescuentoPct(descuento);
            detalle.setAlicuotaIva(producto.getTipoIva().getPorcentaje());
            detalle.calcularSubtotal();

            presupuesto.agregarDetalle(detalle);
        }

        presupuesto.calcularTotal();
        return presupuestoRepo.save(presupuesto);
    }

    private ProductoVariante resolverVariante(Producto producto, List<Long> varianteIds, int indice) {
        Long varianteId = varianteIds != null && indice < varianteIds.size() ? varianteIds.get(indice) : null;
        if (varianteId == null || varianteId == 0) {
            if (varianteRepo.existsByProductoId(producto.getId())) throw new IllegalArgumentException("Debe seleccionar una variante para " + producto.getDescripcion());
            return null;
        }
        ProductoVariante variante = varianteRepo.findById(varianteId)
                .orElseThrow(() -> new IllegalArgumentException("Variante no encontrada"));
        if (!variante.getProducto().getId().equals(producto.getId())) throw new IllegalArgumentException("La variante no pertenece al producto");
        return variante;
    }

    // ==========================================
// ANULAR VENTA ASOCIADA
// ==========================================
    private void anularVentaDelPresupuesto(Presupuesto presupuesto) {
        List<Venta> ventas = ventaRepo.findAllByPresupuestoCodigo(presupuesto.getCodigo());

        for (Venta venta : ventas) {
            if (venta.getEstado() == Venta.Estado.COMPLETADA) {
                for (VentaItem item : venta.getItems()) {
                    movimientoService.registrarDevolucion(
                            item.getProducto().getId(),
                            item.getVariante() == null ? null : item.getVariante().getId(),
                            item.getCantidad(),
                            "Anulación por edición de presupuesto " + presupuesto.getCodigo()
                    );
                }
                venta.setEstado(Venta.Estado.ANULADA);
                ventaRepo.save(venta);
            }
        }
    }


    // ==========================================
    // ELIMINAR (solo PENDIENTE)
    // ==========================================
    public void eliminar(Long id) {
        Presupuesto presupuesto = buscarPorId(id);

        if (presupuesto.getEstado() != EstadoPresupuesto.PENDIENTE) {
            throw new IllegalArgumentException(
                    "Solo se puede eliminar un presupuesto PENDIENTE");
        }

        presupuestoRepo.delete(presupuesto);
    }

    @Transactional
    public void actualizarPrecioProductoEnPendientes(
            Long productoId,
            BigDecimal nuevoPrecioContado,
            BigDecimal nuevoPrecioTarjeta,
            BigDecimal nuevoPrecioCC) {

        List<Presupuesto> pendientes =
                presupuestoRepo.findByEstado(EstadoPresupuesto.PENDIENTE);

        for (Presupuesto presupuesto : pendientes) {

            boolean modificado = false;

            for (DetallePresupuesto detalle : presupuesto.getDetalles()) {

                if (detalle.getProducto().getId().equals(productoId)) {

                    BigDecimal precioNuevo;

                    switch (presupuesto.getFormaPago()) {
                        case TARJETA:
                            precioNuevo = nuevoPrecioTarjeta;
                            break;

                        case CUENTA_CORRIENTE:
                            precioNuevo = nuevoPrecioCC;
                            break;

                        default:
                            precioNuevo = nuevoPrecioContado;
                    }

                    detalle.setPrecioUnitario(precioNuevo);
                    detalle.calcularSubtotal();

                    modificado = true;
                }
            }

            if (modificado) {
                presupuesto.calcularTotal();
                presupuestoRepo.save(presupuesto);
            }
        }
    }

}

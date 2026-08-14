package com.sistema.service;

import com.sistema.model.*;
import com.sistema.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@Transactional
public class VentaService {

    private final VentaRepository ventaRepo;
    private final ProductoRepository productoRepo;
    private final ClienteRepository clienteRepo;
    private final PresupuestoRepository presupuestoRepo;
    private final MovimientoInventarioService movimientoService;
    private final ProductoVarianteRepository varianteRepo;

    public VentaService(VentaRepository ventaRepo,
                        ProductoRepository productoRepo,
                        ClienteRepository clienteRepo,
                        PresupuestoRepository presupuestoRepo,
                        MovimientoInventarioService movimientoService,
                        ProductoVarianteRepository varianteRepo) {
        this.ventaRepo = ventaRepo;
        this.productoRepo = productoRepo;
        this.clienteRepo = clienteRepo;
        this.presupuestoRepo = presupuestoRepo;
        this.movimientoService = movimientoService;
        this.varianteRepo = varianteRepo;
    }

    // =====================================================
    // 1️⃣ VENTA DIRECTA (cliente opcional)
    // =====================================================
    public Venta crearVentaDirecta(Long clienteId,
                                   List<VentaItem> items,
                                   FormaPago formaPago,
                                   String nota) {

        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException(
                    "La venta debe tener al menos un item");
        }

        // Cliente opcional
        Cliente cliente = null;
        if (clienteId != null) {
            cliente = clienteRepo.findById(clienteId)
                    .orElseThrow(() ->
                            new IllegalArgumentException("Cliente no encontrado"));
        }

        // Crear venta
        Venta venta = new Venta(
                generarCodigoVenta(),
                cliente,
                Venta.Origen.DIRECTA,
                formaPago,
                null,
                nota
        );

        // Procesar items
        for (VentaItem item : items) {

            Producto producto = productoRepo.findById(
                    item.getProducto().getId()
            ).orElseThrow(() ->
                    new IllegalArgumentException("Producto no encontrado"));

            // Asegurar entidad gestionada
            item.setProducto(producto);

            ProductoVariante variante = null;
            if (item.getVariante() != null && item.getVariante().getId() != null) {
                variante = varianteRepo.findById(item.getVariante().getId())
                        .orElseThrow(() -> new IllegalArgumentException("Variante no encontrada"));
                if (!variante.getProducto().getId().equals(producto.getId())) throw new IllegalArgumentException("La variante no pertenece al producto");
                item.setVariante(variante);
            } else if (varianteRepo.existsByProductoId(producto.getId())) {
                throw new IllegalArgumentException("Debe seleccionar una variante para " + producto.getDescripcion());
            }

            BigDecimal precio = variante == null
                    ? producto.getPrecioSegunFormaPago(venta.getFormaPago())
                    : variante.precio(venta.getFormaPago());

            if (precio == null) {
                throw new IllegalStateException("El producto no tiene precio configurado");
            }

            // Snapshot del precio actual
            item.setPrecioUnitario(precio);

            item.setCostoUnitario(
                    variante != null && variante.getPrecioCompra() != null ? variante.getPrecioCompra() : producto.getPrecioCompra() != null
                            ? producto.getPrecioCompra()
                            : BigDecimal.ZERO
            );

            item.setAlicuotaIva(producto.getTipoIva().getPorcentaje());

            // Calcular subtotal
            calcularItem(item);

            // Asociar item a la venta
            venta.agregarItem(item);

            int stockDisponible = variante == null ? producto.getCantidad() : variante.getStock();
            if (stockDisponible < item.getCantidad()) {
                throw new IllegalStateException("Stock insuficiente");
            }


            // Movimiento de inventario (SALIDA)
            movimientoService.registrarVenta(
                    producto.getId(),
                    variante == null ? null : variante.getId(),
                    item.getCantidad(),
                    "Venta " + venta.getCodigo()
            );
        }

        // Calcular total de la venta
        venta.calcularTotales();
        venta.setEstado(Venta.Estado.COMPLETADA);
        return ventaRepo.save(venta);
    }

    // =====================================================
    // 2️⃣ VENTA DESDE PRESUPUESTO
    // =====================================================
    @Transactional
    public Venta crearDesdePresupuesto(Long presupuestoId,
                                       FormaPago formaPago) {

        Presupuesto p = presupuestoRepo.findById(presupuestoId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Presupuesto no encontrado"));

        if (p.getEstado() != EstadoPresupuesto.PENDIENTE) {
            throw new IllegalStateException(
                    "Solo se puede vender un presupuesto PENDIENTE");
        }

        Venta venta = new Venta(
                generarCodigoVenta(),
                p.getCliente(),
                Venta.Origen.PRESUPUESTO,
                formaPago,
                p.getCodigo(),
                "Generada desde presupuesto " + p.getCodigo()
        );

        for (DetallePresupuesto dp : p.getDetalles()) {

            Producto producto = productoRepo.findById(
                    dp.getProducto().getId()
            ).orElseThrow(() ->
                    new IllegalArgumentException("Producto no encontrado"));
            ProductoVariante variante = dp.getVariante() == null ? null : varianteRepo.findById(dp.getVariante().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Variante no encontrada"));

            BigDecimal precio = dp.getPrecioUnitario();

            if (precio == null) {
                throw new IllegalStateException(
                        "El detalle del presupuesto no tiene precio configurado");
            }

            VentaItem item = new VentaItem();
            item.setPrecioUnitario(precio); // ← precio del presupuesto
            item.setProducto(producto);
            item.setVariante(variante);
            item.setCantidad(dp.getCantidad());
            item.setCostoUnitario(
                    variante != null && variante.getPrecioCompra() != null ? variante.getPrecioCompra() : producto.getPrecioCompra() != null
                            ? producto.getPrecioCompra()
                            : BigDecimal.ZERO
            );
            item.setDescuentoPct(dp.getDescuentoPct());
            item.setAlicuotaIva(producto.getTipoIva().getPorcentaje());
            item.calcularSubtotal();

            venta.agregarItem(item);

            movimientoService.registrarVenta(
                    producto.getId(),
                    variante == null ? null : variante.getId(),
                    dp.getCantidad(),
                    "Venta desde presupuesto " + p.getCodigo()
            );
        }

        venta.calcularTotales();
        venta.setEstado(Venta.Estado.COMPLETADA);
        Venta ventaGuardada = ventaRepo.save(venta);

        p.setEstado(EstadoPresupuesto.VENDIDO);
        presupuestoRepo.save(p);

        return ventaGuardada;
    }


    public TotalesConIva calcularTotalesConIvaMap(Venta venta) {

        BigDecimal netoAcum = BigDecimal.ZERO;
        BigDecimal ivaAcum = BigDecimal.ZERO;
        Map<BigDecimal, BigDecimal> ivasMap = new HashMap<>();

        for (VentaItem item : venta.getItems()) {

            BigDecimal ivaRate = item.getAlicuotaIva(); // Ej: 21.00, 10.50, 0.00
            BigDecimal subtotal = item.getSubtotal();

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
        if (venta.getCliente() == null
                || venta.getCliente().getCondicionIva() == null
                || !venta.getCliente().getCondicionIva().isReceptorFacturaA()) {
            netoAcum = total;
            ivaAcum = BigDecimal.ZERO;
            ivasMap.clear(); // no mostramos líneas de IVA
        }

        return new TotalesConIva(netoAcum, ivaAcum, total, ivasMap);
    }




    // =====================================================
    // 3️⃣ ANULAR VENTA
    // =====================================================
    public void anularVenta(Long ventaId) {

        Venta venta = ventaRepo.findById(ventaId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Venta no encontrada"));

        if (venta.getEstado() != Venta.Estado.COMPLETADA) {
            throw new IllegalStateException(
                    "Solo se pueden anular ventas completadas");
        }

        for (VentaItem item : venta.getItems()) {
            movimientoService.registrarDevolucion(
                    item.getProducto().getId(),
                    item.getVariante() == null ? null : item.getVariante().getId(),
                    item.getCantidad(),
                    "Anulación venta " + venta.getCodigo()
            );
        }

        venta.setEstado(Venta.Estado.ANULADA);
        ventaRepo.save(venta);
    }

    // =====================================================
    // 4️⃣ LISTADOS
    // =====================================================
    public List<Venta> listarVentasNoAnuladas() {
        return ventaRepo.findByEstadoNotOrderByFechaVentaDesc(Venta.Estado.ANULADA);
    }

    // =====================================================
    // 🔢 CÁLCULO DE ITEM
    // =====================================================
    private void calcularItem(VentaItem item) {

        BigDecimal precio = item.getPrecioUnitario();
        BigDecimal cantidad = BigDecimal.valueOf(item.getCantidad());

        BigDecimal bruto = precio.multiply(cantidad);

        BigDecimal descuentoPct = item.getDescuentoPct() != null
                ? item.getDescuentoPct()
                : BigDecimal.ZERO;

        BigDecimal descuentoMonto = bruto
                .multiply(descuentoPct)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        BigDecimal totalConDescuento = bruto.subtract(descuentoMonto);

        item.setSubtotal(totalConDescuento);
    }




    // =====================================================
    // 🧾 CÓDIGO DE VENTA
    // =====================================================
    private String generarCodigoVenta() {
        return "VENTA-" + System.currentTimeMillis();
    }

    public BigDecimal calcularGananciaTotal(List<Venta> ventas) {

        return ventas.stream()
                .flatMap(v -> v.getItems().stream())
                .map(VentaItem::getGanancia)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }


}




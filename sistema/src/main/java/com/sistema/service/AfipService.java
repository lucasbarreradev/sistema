package com.sistema.service;

import com.sistema.model.*;
import com.sistema.repository.ComprobanteArcaRepository;
import com.sistema.repository.VentaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.RoundingMode;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AfipService {
    private final VentaRepository ventaRepo;
    private final ComprobanteArcaRepository comprobanteRepo;
    private final AfipClient afipClient;
    private final ConfiguracionArcaService configuracionArca;

    public AfipService(VentaRepository ventaRepo, ComprobanteArcaRepository comprobanteRepo,
                       AfipClient afipClient,
                       ConfiguracionArcaService configuracionArca) {
        this.ventaRepo = ventaRepo;
        this.comprobanteRepo = comprobanteRepo;
        this.afipClient = afipClient;
        this.configuracionArca = configuracionArca;
    }

    @Transactional
    public Venta facturarConAfip(Long ventaId) {
        Venta venta = ventaRepo.findById(ventaId)
                .orElseThrow(() -> new IllegalArgumentException("Venta no encontrada"));
        if (venta.getCae() != null && !venta.getCae().isBlank()) return venta;
        if (venta.getEstado() != Venta.Estado.COMPLETADA) {
            throw new IllegalStateException("Solo se pueden facturar ventas completadas");
        }
        if (venta.getItems() == null || venta.getItems().isEmpty()) {
            throw new IllegalStateException("La venta no contiene productos");
        }

        ConfiguracionArcaService.Credenciales credenciales = configuracionArca.obtenerCredenciales();
        TipoComprobante tipo = determinarTipo(venta, credenciales.condicionFiscal());
        Documento documento = documentoReceptor(venta, tipo);
        TotalesArca totales = calcularTotales(venta, tipo);

        Long numero = afipClient.obtenerUltimoNumero(credenciales.puntoVenta(), tipo) + 1;
        LocalDate fechaComprobante = LocalDate.now();
        AfipFacturaRequest request = AfipFacturaRequest.builder()
                .puntoVenta(credenciales.puntoVenta())
                .tipoComprobante(tipo)
                .numeroComprobante(numero)
                .fechaComprobante(fechaComprobante)
                .tipoDocumento(documento.tipo())
                .numeroDocumento(documento.numero())
                .condicionIvaReceptorId(condicionIvaReceptor(venta))
                .importeNeto(totales.neto())
                .importeIva(totales.iva())
                .importeExento(totales.exento())
                .importeTotal(totales.total())
                .alicuotas(totales.alicuotas().entrySet().stream()
                        .map(e -> new AfipFacturaRequest.Alicuota(e.getKey(), e.getValue().base(), e.getValue().iva()))
                        .toList())
                .cliente(venta.getCliente())
                .build();

        AfipFacturaResponse response = afipClient.facturar(request);
        venta.setTipoComprobante(tipo);
        venta.setPuntoVenta(credenciales.puntoVenta());
        venta.setNumeroComprobante(response.getNumeroComprobante());
        venta.setCae(response.getCae());
        venta.setFechaVencimientoCae(response.getFechaVencimiento());
        venta.setFechaComprobante(fechaComprobante);
        venta.setEstado(Venta.Estado.FACTURADA);
        venta.setTotalNeto(totales.neto());
        venta.setTotalIva(totales.iva());
        venta.setTotal(totales.total());
        return ventaRepo.save(venta);
    }

    @Transactional
    public ComprobanteArca emitirNotaCredito(Long ventaId, boolean total,
                                             BigDecimal importe, String motivo) {
        return emitirAjuste(ventaId, true, total, importe, motivo);
    }

    @Transactional
    public ComprobanteArca emitirNotaDebito(Long ventaId, BigDecimal importe, String motivo) {
        return emitirAjuste(ventaId, false, false, importe, motivo);
    }

    @Transactional(readOnly = true)
    public List<ComprobanteArca> comprobantesDe(Long ventaId) {
        return comprobanteRepo.findByFacturaOrigenIdOrderByFechaComprobanteDescIdDesc(ventaId);
    }

    @Transactional(readOnly = true)
    public BigDecimal saldoDisponibleNotaCredito(Long ventaId) {
        Venta venta = ventaRepo.findById(ventaId)
                .orElseThrow(() -> new IllegalArgumentException("Venta no encontrada"));
        return saldoDisponibleNotaCredito(venta);
    }

    private ComprobanteArca emitirAjuste(Long ventaId, boolean credito, boolean total,
                                         BigDecimal importeSolicitado, String motivo) {
        Venta venta = ventaRepo.findById(ventaId)
                .orElseThrow(() -> new IllegalArgumentException("Venta no encontrada"));
        validarFacturaOrigen(venta);
        String motivoLimpio = motivo == null ? "" : motivo.trim();
        if (motivoLimpio.isBlank()) throw new IllegalArgumentException("Ingrese el motivo del comprobante");
        if (motivoLimpio.length() > 500) throw new IllegalArgumentException("El motivo no puede superar 500 caracteres");

        BigDecimal importe;
        if (credito && total) {
            importe = saldoDisponibleNotaCredito(venta);
            if (importe.signum() <= 0) {
                throw new IllegalStateException("La factura ya fue acreditada completamente");
            }
        } else {
            importe = validarImporte(importeSolicitado);
            if (credito) {
                BigDecimal disponible = saldoDisponibleNotaCredito(venta);
                if (importe.compareTo(disponible) > 0) {
                    throw new IllegalArgumentException("La nota de crédito supera el saldo disponible de $ "
                            + disponible.setScale(2, RoundingMode.HALF_UP));
                }
            }
        }

        TipoComprobante tipo = credito
                ? venta.getTipoComprobante().notaCreditoCorrespondiente()
                : venta.getTipoComprobante().notaDebitoCorrespondiente();
        TotalesArca base = calcularTotales(venta, venta.getTipoComprobante());
        TotalesArca totales = prorratear(base, importe, tipo);
        ConfiguracionArcaService.Credenciales credenciales = configuracionArca.obtenerCredenciales();
        Documento documento = documentoReceptor(venta, tipo);
        LocalDate fecha = LocalDate.now();
        Long numero = afipClient.obtenerUltimoNumero(credenciales.puntoVenta(), tipo) + 1;

        AfipFacturaRequest request = AfipFacturaRequest.builder()
                .puntoVenta(credenciales.puntoVenta())
                .tipoComprobante(tipo)
                .numeroComprobante(numero)
                .fechaComprobante(fecha)
                .tipoDocumento(documento.tipo())
                .numeroDocumento(documento.numero())
                .condicionIvaReceptorId(condicionIvaReceptor(venta))
                .importeNeto(totales.neto())
                .importeIva(totales.iva())
                .importeExento(totales.exento())
                .importeTotal(totales.total())
                .alicuotas(totales.alicuotas().entrySet().stream()
                        .map(e -> new AfipFacturaRequest.Alicuota(
                                e.getKey(), e.getValue().base(), e.getValue().iva()))
                        .toList())
                .comprobanteAsociado(new AfipFacturaRequest.ComprobanteAsociado(
                        venta.getTipoComprobante(), venta.getPuntoVenta(), venta.getNumeroComprobante(),
                        venta.getFechaComprobante()))
                .cliente(venta.getCliente())
                .build();

        AfipFacturaResponse response = afipClient.facturar(request);
        ComprobanteArca comprobante = new ComprobanteArca();
        comprobante.setFacturaOrigen(venta);
        comprobante.setTipoComprobante(tipo);
        comprobante.setPuntoVenta(credenciales.puntoVenta());
        comprobante.setNumeroComprobante(response.getNumeroComprobante());
        comprobante.setCae(response.getCae());
        comprobante.setFechaVencimientoCae(response.getFechaVencimiento());
        comprobante.setFechaComprobante(fecha);
        comprobante.setFechaCreacion(LocalDateTime.now());
        comprobante.setTotalNeto(totales.neto());
        comprobante.setTotalIva(totales.iva());
        comprobante.setTotalExento(totales.exento());
        comprobante.setTotal(totales.total());
        comprobante.setMotivo(motivoLimpio);
        return comprobanteRepo.save(comprobante);
    }

    private void validarFacturaOrigen(Venta venta) {
        if (venta.getCae() == null || venta.getCae().isBlank()
                || venta.getPuntoVenta() == null || venta.getNumeroComprobante() == null
                || venta.getTipoComprobante() == null || !venta.getTipoComprobante().isFactura()) {
            throw new IllegalStateException("La venta debe tener una factura autorizada por ARCA");
        }
    }

    private BigDecimal validarImporte(BigDecimal importe) {
        if (importe == null) throw new IllegalArgumentException("Ingrese el importe del comprobante");
        BigDecimal normalizado = importe.setScale(2, RoundingMode.HALF_UP);
        if (normalizado.signum() <= 0) throw new IllegalArgumentException("El importe debe ser mayor a cero");
        return normalizado;
    }

    private BigDecimal saldoDisponibleNotaCredito(Venta venta) {
        BigDecimal acreditado = comprobanteRepo
                .findByFacturaOrigenIdOrderByFechaComprobanteDescIdDesc(venta.getId()).stream()
                .filter(c -> c.getTipoComprobante() != null && c.getTipoComprobante().isNotaCredito())
                .map(ComprobanteArca::getTotal)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal saldo = venta.getTotal().subtract(acreditado).setScale(2, RoundingMode.HALF_UP);
        return saldo.signum() < 0 ? BigDecimal.ZERO.setScale(2) : saldo;
    }

    private TotalesArca prorratear(TotalesArca base, BigDecimal importe, TipoComprobante tipo) {
        BigDecimal total = importe.setScale(2, RoundingMode.HALF_UP);
        if ("C".equals(tipo.getLetra())) {
            return new TotalesArca(total, BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2),
                    total, Map.of());
        }
        if (base.total().signum() <= 0) throw new IllegalStateException("La factura no tiene un total válido");
        BigDecimal proporcion = total.divide(base.total(), 12, RoundingMode.HALF_UP);
        Map<Integer, AcumuladoIva> alicuotas = new LinkedHashMap<>();
        for (Map.Entry<Integer, AcumuladoIva> entry : base.alicuotas().entrySet()) {
            alicuotas.put(entry.getKey(), new AcumuladoIva(
                    escalar(entry.getValue().base(), proporcion),
                    escalar(entry.getValue().iva(), proporcion)));
        }
        BigDecimal exento = escalar(base.exento(), proporcion);
        BigDecimal neto = alicuotas.values().stream().map(AcumuladoIva::base)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal iva = alicuotas.values().stream().map(AcumuladoIva::iva)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal diferencia = total.subtract(neto).subtract(iva).subtract(exento)
                .setScale(2, RoundingMode.HALF_UP);
        if (diferencia.signum() != 0) {
            if (!alicuotas.isEmpty()) {
                Integer primera = alicuotas.keySet().iterator().next();
                AcumuladoIva valor = alicuotas.get(primera);
                alicuotas.put(primera, new AcumuladoIva(valor.base().add(diferencia), valor.iva()));
                neto = neto.add(diferencia);
            } else {
                exento = exento.add(diferencia);
            }
        }
        return new TotalesArca(neto, iva, exento, total, alicuotas);
    }

    private BigDecimal escalar(BigDecimal valor, BigDecimal proporcion) {
        return (valor == null ? BigDecimal.ZERO : valor)
                .multiply(proporcion).setScale(2, RoundingMode.HALF_UP);
    }

    public void probarConexion() { afipClient.probarConexion(); }

    private TipoComprobante determinarTipo(Venta venta, CondicionFiscalArca emisor) {
        if (emisor != CondicionFiscalArca.RESPONSABLE_INSCRIPTO) return TipoComprobante.FACTURA_C;
        return venta.getCliente() != null
                && venta.getCliente().getCondicionIva() != null
                && venta.getCliente().getCondicionIva().isReceptorFacturaA()
                ? TipoComprobante.FACTURA_A : TipoComprobante.FACTURA_B;
    }

    private Documento documentoReceptor(Venta venta, TipoComprobante tipo) {
        String valor = venta.getCliente() != null ? venta.getCliente().getDni()
                : venta.getClienteDocumentoExterno();
        String digitos = valor == null ? "" : valor.replaceAll("\\D", "");
        Documento documento;
        if (digitos.length() == 11) documento = new Documento(80, Long.parseLong(digitos));
        else if (digitos.length() == 7 || digitos.length() == 8) documento = new Documento(96, Long.parseLong(digitos));
        else documento = new Documento(99, 0L);
        if (tipo.discriminaIva() && documento.tipo() != 80) {
            throw new IllegalArgumentException("Para emitir Factura A el cliente debe tener su CUIT cargado en DNI/CUIT");
        }
        return documento;
    }

    private int condicionIvaReceptor(Venta venta) {
        CondicionIva condicion = venta.getCliente() == null
                ? CondicionIva.CONSUMIDOR_FINAL
                : venta.getCliente().getCondicionIva();
        if (condicion == null) condicion = CondicionIva.CONSUMIDOR_FINAL;
        return condicion.getCodigoArca();
    }

    private TotalesArca calcularTotales(Venta venta, TipoComprobante tipo) {
        BigDecimal total = venta.getItems().stream().map(VentaItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if ("C".equals(tipo.getLetra())) {
            return new TotalesArca(total, BigDecimal.ZERO, BigDecimal.ZERO, total, Map.of());
        }

        BigDecimal neto = BigDecimal.ZERO;
        BigDecimal iva = BigDecimal.ZERO;
        BigDecimal exento = BigDecimal.ZERO;
        Map<Integer, AcumuladoIva> alicuotas = new LinkedHashMap<>();
        for (VentaItem item : venta.getItems()) {
            BigDecimal tasa = item.getAlicuotaIva() == null ? BigDecimal.ZERO : item.getAlicuotaIva();
            if (tasa.signum() == 0) {
                exento = exento.add(item.getSubtotal());
                continue;
            }
            BigDecimal base = item.getNeto();
            BigDecimal montoIva = item.getIva();
            neto = neto.add(base);
            iva = iva.add(montoIva);
            int codigo = codigoAlicuota(tasa);
            AcumuladoIva anterior = alicuotas.getOrDefault(codigo,
                    new AcumuladoIva(BigDecimal.ZERO, BigDecimal.ZERO));
            alicuotas.put(codigo, new AcumuladoIva(anterior.base().add(base), anterior.iva().add(montoIva)));
        }
        return new TotalesArca(neto, iva, exento, total, alicuotas);
    }

    private int codigoAlicuota(BigDecimal tasa) {
        if (tasa.compareTo(new BigDecimal("21.00")) == 0) return 5;
        if (tasa.compareTo(new BigDecimal("10.50")) == 0) return 4;
        if (tasa.compareTo(new BigDecimal("27.00")) == 0) return 6;
        throw new IllegalArgumentException("ARCA no admite la alícuota de IVA " + tasa + "% configurada en un producto");
    }

    private record Documento(Integer tipo, Long numero) {}
    private record AcumuladoIva(BigDecimal base, BigDecimal iva) {}
    private record TotalesArca(BigDecimal neto, BigDecimal iva, BigDecimal exento,
                               BigDecimal total, Map<Integer, AcumuladoIva> alicuotas) {}
}

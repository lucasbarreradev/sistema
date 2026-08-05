package com.sistema.service;

import com.sistema.model.*;
import com.sistema.repository.VentaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AfipService {
    private final VentaRepository ventaRepo;
    private final AfipClient afipClient;
    private final ConfiguracionArcaService configuracionArca;

    public AfipService(VentaRepository ventaRepo, AfipClient afipClient,
                       ConfiguracionArcaService configuracionArca) {
        this.ventaRepo = ventaRepo;
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

    public void probarConexion() { afipClient.probarConexion(); }

    private TipoComprobante determinarTipo(Venta venta, CondicionFiscalArca emisor) {
        if (emisor != CondicionFiscalArca.RESPONSABLE_INSCRIPTO) return TipoComprobante.FACTURA_C;
        return venta.getCliente() != null
                && venta.getCliente().getCondicionIva() == CondicionIva.RESPONSABLE_INSCRIPTO
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
        if (tipo == TipoComprobante.FACTURA_A && documento.tipo() != 80) {
            throw new IllegalArgumentException("Para emitir Factura A el cliente debe tener su CUIT cargado en DNI/CUIT");
        }
        return documento;
    }

    private int condicionIvaReceptor(Venta venta) {
        CondicionIva condicion = venta.getCliente() == null
                ? CondicionIva.CONSUMIDOR_FINAL
                : venta.getCliente().getCondicionIva();
        if (condicion == null) condicion = CondicionIva.CONSUMIDOR_FINAL;
        return switch (condicion) {
            case RESPONSABLE_INSCRIPTO -> 1;
            case CONSUMIDOR_FINAL -> 5;
        };
    }

    private TotalesArca calcularTotales(Venta venta, TipoComprobante tipo) {
        BigDecimal total = venta.getItems().stream().map(VentaItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (tipo == TipoComprobante.FACTURA_C) {
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

package com.sistema.model;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AfipFacturaRequest {

    private Integer puntoVenta;
    private TipoComprobante tipoComprobante;
    private Long numeroComprobante;

    private BigDecimal importeNeto;
    private BigDecimal importeIva;
    private BigDecimal importeTotal;
    private BigDecimal importeExento;

    private Integer tipoDocumento;
    private Long numeroDocumento;
    private Integer condicionIvaReceptorId;
    private LocalDate fechaComprobante;
    private List<Alicuota> alicuotas;
    private ComprobanteAsociado comprobanteAsociado;

    private Cliente cliente;

    public record Alicuota(Integer codigo, BigDecimal baseImponible, BigDecimal importe) {}
    public record ComprobanteAsociado(TipoComprobante tipo, Integer puntoVenta,
                                      Long numero, LocalDate fecha) {}
}


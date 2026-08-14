package com.sistema.service;

import com.sistema.model.AfipFacturaRequest;
import com.sistema.model.TipoComprobante;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AfipClientTest {

    @Test
    void incluyeFacturaAsociadaEnSolicitudDeNotaDeCredito() {
        AfipClient cliente = new AfipClient(mock(ConfiguracionArcaService.class));
        AfipFacturaRequest request = AfipFacturaRequest.builder()
                .puntoVenta(4)
                .tipoComprobante(TipoComprobante.NOTA_CREDITO_B)
                .numeroComprobante(8L)
                .fechaComprobante(LocalDate.of(2026, 8, 13))
                .tipoDocumento(96)
                .numeroDocumento(30111222L)
                .condicionIvaReceptorId(5)
                .importeNeto(new BigDecimal("100.00"))
                .importeIva(new BigDecimal("21.00"))
                .importeExento(BigDecimal.ZERO)
                .importeTotal(new BigDecimal("121.00"))
                .alicuotas(List.of(new AfipFacturaRequest.Alicuota(
                        5, new BigDecimal("100.00"), new BigDecimal("21.00"))))
                .comprobanteAsociado(new AfipFacturaRequest.ComprobanteAsociado(
                        TipoComprobante.FACTURA_B, 4, 25L, LocalDate.of(2026, 8, 12)))
                .build();

        String xml = cliente.construirDetalle(request);

        assertTrue(xml.contains("<ar:CbtesAsoc><ar:CbteAsoc>"));
        assertTrue(xml.contains("<ar:Tipo>6</ar:Tipo>"));
        assertTrue(xml.contains("<ar:PtoVta>4</ar:PtoVta>"));
        assertTrue(xml.contains("<ar:Nro>25</ar:Nro>"));
        assertTrue(xml.contains("<ar:CbteFch>20260812</ar:CbteFch>"));
    }
}

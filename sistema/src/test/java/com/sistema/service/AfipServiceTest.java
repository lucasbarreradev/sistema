package com.sistema.service;

import com.sistema.model.*;
import com.sistema.repository.ComprobanteArcaRepository;
import com.sistema.repository.VentaRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AfipServiceTest {

    @Test
    void monotributistaEmiteFacturaCConTotalComoNeto() {
        VentaRepository ventas = mock(VentaRepository.class);
        ComprobanteArcaRepository comprobantes = mock(ComprobanteArcaRepository.class);
        AfipClient cliente = mock(AfipClient.class);
        ConfiguracionArcaService configuracion = mock(ConfiguracionArcaService.class);
        Venta venta = ventaConItem("121.00", "21.00");
        when(ventas.findById(1L)).thenReturn(Optional.of(venta));
        when(ventas.save(any())).thenAnswer(i -> i.getArgument(0));
        when(configuracion.obtenerCredenciales()).thenReturn(new ConfiguracionArcaService.Credenciales(
                "20267565393", 1, CondicionFiscalArca.MONOTRIBUTO, "cert", "key"));
        when(cliente.obtenerUltimoNumero(1, TipoComprobante.FACTURA_C)).thenReturn(4L);
        when(cliente.facturar(any())).thenReturn(new AfipFacturaResponse("12345678901234",
                LocalDate.now().plusDays(10), 5L));

        Venta facturada = new AfipService(ventas, comprobantes, cliente, configuracion).facturarConAfip(1L);

        assertEquals(TipoComprobante.FACTURA_C, facturada.getTipoComprobante());
        assertEquals(Venta.Estado.FACTURADA, facturada.getEstado());
        verify(cliente).facturar(argThat(r -> r.getImporteNeto().compareTo(new BigDecimal("121.00")) == 0
                && r.getImporteIva().signum() == 0 && r.getTipoDocumento() == 99
                && r.getCondicionIvaReceptorId() == 5));
    }

    @Test
    void responsableInscriptoEmiteFacturaAConIvaDiscriminado() {
        VentaRepository ventas = mock(VentaRepository.class);
        ComprobanteArcaRepository comprobantes = mock(ComprobanteArcaRepository.class);
        AfipClient cliente = mock(AfipClient.class);
        ConfiguracionArcaService configuracion = mock(ConfiguracionArcaService.class);
        Venta venta = ventaConItem("121.00", "21.00");
        Cliente receptor = new Cliente();
        receptor.setNombre("Cliente RI");
        receptor.setDni("30-71234567-1");
        receptor.setCondicionIva(CondicionIva.RESPONSABLE_INSCRIPTO);
        venta.setCliente(receptor);
        when(ventas.findById(1L)).thenReturn(Optional.of(venta));
        when(ventas.save(any())).thenAnswer(i -> i.getArgument(0));
        when(configuracion.obtenerCredenciales()).thenReturn(new ConfiguracionArcaService.Credenciales(
                "20267565393", 2, CondicionFiscalArca.RESPONSABLE_INSCRIPTO, "cert", "key"));
        when(cliente.obtenerUltimoNumero(2, TipoComprobante.FACTURA_A)).thenReturn(9L);
        when(cliente.facturar(any())).thenReturn(new AfipFacturaResponse("12345678901234",
                LocalDate.now().plusDays(10), 10L));

        new AfipService(ventas, comprobantes, cliente, configuracion).facturarConAfip(1L);

        verify(cliente).facturar(argThat(r -> r.getTipoComprobante() == TipoComprobante.FACTURA_A
                && r.getTipoDocumento() == 80
                && r.getCondicionIvaReceptorId() == 1
                && r.getImporteNeto().compareTo(new BigDecimal("100.00")) == 0
                && r.getImporteIva().compareTo(new BigDecimal("21.00")) == 0
                && r.getAlicuotas().size() == 1 && r.getAlicuotas().get(0).codigo() == 5));
    }

    @Test
    void responsableInscriptoFacturaAResponsableMonotributoConCodigoArcaSeis() {
        VentaRepository ventas = mock(VentaRepository.class);
        ComprobanteArcaRepository comprobantes = mock(ComprobanteArcaRepository.class);
        AfipClient cliente = mock(AfipClient.class);
        ConfiguracionArcaService configuracion = mock(ConfiguracionArcaService.class);
        Venta venta = ventaConItem("121.00", "21.00");
        Cliente receptor = new Cliente();
        receptor.setNombre("Cliente monotributista");
        receptor.setDni("27-12345678-2");
        receptor.setCondicionIva(CondicionIva.RESPONSABLE_MONOTRIBUTO);
        venta.setCliente(receptor);
        when(ventas.findById(1L)).thenReturn(Optional.of(venta));
        when(ventas.save(any())).thenAnswer(i -> i.getArgument(0));
        when(configuracion.obtenerCredenciales()).thenReturn(new ConfiguracionArcaService.Credenciales(
                "20267565393", 2, CondicionFiscalArca.RESPONSABLE_INSCRIPTO, "cert", "key"));
        when(cliente.obtenerUltimoNumero(2, TipoComprobante.FACTURA_A)).thenReturn(1L);
        when(cliente.facturar(any())).thenReturn(new AfipFacturaResponse(
                "12345678901234", LocalDate.now().plusDays(10), 2L));

        new AfipService(ventas, comprobantes, cliente, configuracion).facturarConAfip(1L);

        verify(cliente).facturar(argThat(r -> r.getTipoComprobante() == TipoComprobante.FACTURA_A
                && r.getCondicionIvaReceptorId() == 6));
    }

    @Test
    void responsableInscriptoFacturaBASujetoExentoConCodigoArcaCuatro() {
        VentaRepository ventas = mock(VentaRepository.class);
        ComprobanteArcaRepository comprobantes = mock(ComprobanteArcaRepository.class);
        AfipClient cliente = mock(AfipClient.class);
        ConfiguracionArcaService configuracion = mock(ConfiguracionArcaService.class);
        Venta venta = ventaConItem("121.00", "21.00");
        Cliente receptor = new Cliente();
        receptor.setNombre("Cliente exento");
        receptor.setDni("30-71234567-1");
        receptor.setCondicionIva(CondicionIva.IVA_SUJETO_EXENTO);
        venta.setCliente(receptor);
        when(ventas.findById(1L)).thenReturn(Optional.of(venta));
        when(ventas.save(any())).thenAnswer(i -> i.getArgument(0));
        when(configuracion.obtenerCredenciales()).thenReturn(new ConfiguracionArcaService.Credenciales(
                "20267565393", 2, CondicionFiscalArca.RESPONSABLE_INSCRIPTO, "cert", "key"));
        when(cliente.obtenerUltimoNumero(2, TipoComprobante.FACTURA_B)).thenReturn(1L);
        when(cliente.facturar(any())).thenReturn(new AfipFacturaResponse(
                "12345678901234", LocalDate.now().plusDays(10), 2L));

        new AfipService(ventas, comprobantes, cliente, configuracion).facturarConAfip(1L);

        verify(cliente).facturar(argThat(r -> r.getTipoComprobante() == TipoComprobante.FACTURA_B
                && r.getCondicionIvaReceptorId() == 4));
    }

    @Test
    void emiteNotaCreditoTotalBAsociadaALaFactura() {
        VentaRepository ventas = mock(VentaRepository.class);
        ComprobanteArcaRepository comprobantes = mock(ComprobanteArcaRepository.class);
        AfipClient cliente = mock(AfipClient.class);
        ConfiguracionArcaService configuracion = mock(ConfiguracionArcaService.class);
        Venta venta = facturaAutorizada(TipoComprobante.FACTURA_B);
        when(ventas.findById(1L)).thenReturn(Optional.of(venta));
        when(comprobantes.findByFacturaOrigenIdOrderByFechaComprobanteDescIdDesc(1L))
                .thenReturn(List.of());
        when(comprobantes.save(any())).thenAnswer(i -> i.getArgument(0));
        when(configuracion.obtenerCredenciales()).thenReturn(new ConfiguracionArcaService.Credenciales(
                "20267565393", 2, CondicionFiscalArca.RESPONSABLE_INSCRIPTO, "cert", "key"));
        when(cliente.obtenerUltimoNumero(2, TipoComprobante.NOTA_CREDITO_B)).thenReturn(10L);
        when(cliente.facturar(any())).thenReturn(new AfipFacturaResponse(
                "12345678901234", LocalDate.now().plusDays(10), 11L));

        ComprobanteArca nota = new AfipService(ventas, comprobantes, cliente, configuracion)
                .emitirNotaCredito(1L, true, null, "Devolución total");

        assertEquals(TipoComprobante.NOTA_CREDITO_B, nota.getTipoComprobante());
        assertEquals(new BigDecimal("121.00"), nota.getTotal());
        verify(cliente).facturar(argThat(r -> r.getTipoComprobante() == TipoComprobante.NOTA_CREDITO_B
                && r.getComprobanteAsociado() != null
                && r.getComprobanteAsociado().tipo() == TipoComprobante.FACTURA_B
                && r.getComprobanteAsociado().puntoVenta() == 2
                && r.getComprobanteAsociado().numero() == 25L));
    }

    @Test
    void emiteNotaDebitoParcialAConIvaProrrateado() {
        VentaRepository ventas = mock(VentaRepository.class);
        ComprobanteArcaRepository comprobantes = mock(ComprobanteArcaRepository.class);
        AfipClient cliente = mock(AfipClient.class);
        ConfiguracionArcaService configuracion = mock(ConfiguracionArcaService.class);
        Venta venta = facturaAutorizada(TipoComprobante.FACTURA_A);
        Cliente receptor = new Cliente();
        receptor.setDni("30-71234567-1");
        receptor.setCondicionIva(CondicionIva.RESPONSABLE_INSCRIPTO);
        venta.setCliente(receptor);
        when(ventas.findById(1L)).thenReturn(Optional.of(venta));
        when(comprobantes.save(any())).thenAnswer(i -> i.getArgument(0));
        when(configuracion.obtenerCredenciales()).thenReturn(new ConfiguracionArcaService.Credenciales(
                "20267565393", 2, CondicionFiscalArca.RESPONSABLE_INSCRIPTO, "cert", "key"));
        when(cliente.obtenerUltimoNumero(2, TipoComprobante.NOTA_DEBITO_A)).thenReturn(4L);
        when(cliente.facturar(any())).thenReturn(new AfipFacturaResponse(
                "12345678901234", LocalDate.now().plusDays(10), 5L));

        new AfipService(ventas, comprobantes, cliente, configuracion)
                .emitirNotaDebito(1L, new BigDecimal("60.50"), "Diferencia de precio");

        verify(cliente).facturar(argThat(r -> r.getTipoComprobante() == TipoComprobante.NOTA_DEBITO_A
                && r.getImporteTotal().compareTo(new BigDecimal("60.50")) == 0
                && r.getImporteNeto().compareTo(new BigDecimal("50.00")) == 0
                && r.getImporteIva().compareTo(new BigDecimal("10.50")) == 0
                && r.getComprobanteAsociado().numero() == 25L));
    }

    @Test
    void impideNotaCreditoQueSupereElSaldoDeLaFactura() {
        VentaRepository ventas = mock(VentaRepository.class);
        ComprobanteArcaRepository comprobantes = mock(ComprobanteArcaRepository.class);
        Venta venta = facturaAutorizada(TipoComprobante.FACTURA_C);
        ComprobanteArca anterior = new ComprobanteArca();
        anterior.setTipoComprobante(TipoComprobante.NOTA_CREDITO_C);
        anterior.setTotal(new BigDecimal("100.00"));
        when(ventas.findById(1L)).thenReturn(Optional.of(venta));
        when(comprobantes.findByFacturaOrigenIdOrderByFechaComprobanteDescIdDesc(1L))
                .thenReturn(List.of(anterior));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new AfipService(ventas, comprobantes, mock(AfipClient.class),
                        mock(ConfiguracionArcaService.class))
                        .emitirNotaCredito(1L, false, new BigDecimal("22.00"), "Ajuste"));

        assertTrue(error.getMessage().contains("saldo disponible"));
    }

    private Venta facturaAutorizada(TipoComprobante tipo) {
        Venta venta = ventaConItem("121.00", "21.00");
        venta.setEstado(Venta.Estado.FACTURADA);
        venta.setTipoComprobante(tipo);
        venta.setPuntoVenta(2);
        venta.setNumeroComprobante(25L);
        venta.setCae("12345678901234");
        venta.setFechaComprobante(LocalDate.now().minusDays(1));
        venta.setTotalNeto(new BigDecimal("100.00"));
        venta.setTotalIva(new BigDecimal("21.00"));
        return venta;
    }

    private Venta ventaConItem(String subtotal, String alicuota) {
        Venta venta = new Venta();
        venta.setId(1L);
        venta.setEstado(Venta.Estado.COMPLETADA);
        VentaItem item = new VentaItem();
        Producto producto = new Producto();
        producto.setDescripcion("Producto");
        item.setProducto(producto);
        item.setCantidad(1);
        item.setPrecioUnitario(new BigDecimal(subtotal));
        item.setSubtotal(new BigDecimal(subtotal));
        item.setAlicuotaIva(new BigDecimal(alicuota));
        venta.agregarItem(item);
        venta.setTotal(new BigDecimal(subtotal));
        return venta;
    }
}

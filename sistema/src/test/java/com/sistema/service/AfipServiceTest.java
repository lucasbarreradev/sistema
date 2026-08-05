package com.sistema.service;

import com.sistema.model.*;
import com.sistema.repository.VentaRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AfipServiceTest {

    @Test
    void monotributistaEmiteFacturaCConTotalComoNeto() {
        VentaRepository ventas = mock(VentaRepository.class);
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

        Venta facturada = new AfipService(ventas, cliente, configuracion).facturarConAfip(1L);

        assertEquals(TipoComprobante.FACTURA_C, facturada.getTipoComprobante());
        assertEquals(Venta.Estado.FACTURADA, facturada.getEstado());
        verify(cliente).facturar(argThat(r -> r.getImporteNeto().compareTo(new BigDecimal("121.00")) == 0
                && r.getImporteIva().signum() == 0 && r.getTipoDocumento() == 99
                && r.getCondicionIvaReceptorId() == 5));
    }

    @Test
    void responsableInscriptoEmiteFacturaAConIvaDiscriminado() {
        VentaRepository ventas = mock(VentaRepository.class);
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

        new AfipService(ventas, cliente, configuracion).facturarConAfip(1L);

        verify(cliente).facturar(argThat(r -> r.getTipoComprobante() == TipoComprobante.FACTURA_A
                && r.getTipoDocumento() == 80
                && r.getCondicionIvaReceptorId() == 1
                && r.getImporteNeto().compareTo(new BigDecimal("100.00")) == 0
                && r.getImporteIva().compareTo(new BigDecimal("21.00")) == 0
                && r.getAlicuotas().size() == 1 && r.getAlicuotas().get(0).codigo() == 5));
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

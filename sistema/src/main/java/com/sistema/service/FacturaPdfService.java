package com.sistema.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import com.sistema.model.*;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class FacturaPdfService {
    private static final Font TITULO = new Font(Font.FontFamily.HELVETICA, 16, Font.BOLD);
    private static final Font NORMAL = new Font(Font.FontFamily.HELVETICA, 9);
    private static final Font NEGRITA = new Font(Font.FontFamily.HELVETICA, 9, Font.BOLD);

    private final ConfiguracionDocumentoService datosEmpresa;
    private final ConfiguracionArcaService configuracionArca;
    private final ObjectMapper objectMapper;

    public FacturaPdfService(ConfiguracionDocumentoService datosEmpresa,
                             ConfiguracionArcaService configuracionArca,
                             ObjectMapper objectMapper) {
        this.datosEmpresa = datosEmpresa;
        this.configuracionArca = configuracionArca;
        this.objectMapper = objectMapper;
    }

    public void generar(Venta venta, HttpServletResponse response) {
        try {
            ConfiguracionDocumento empresa = datosEmpresa.obtener().orElseGet(ConfiguracionDocumento::new);
            ConfiguracionArcaService.Credenciales arca = configuracionArca.obtenerCredenciales();
            Document documento = new Document(PageSize.A4, 36, 36, 32, 32);
            PdfWriter.getInstance(documento, response.getOutputStream());
            documento.open();

            PdfPTable cabecera = new PdfPTable(new float[]{45, 10, 45});
            cabecera.setWidthPercentage(100);
            PdfPCell emisor = celda();
            emisor.addElement(new Paragraph(valor(empresa.getNombreEmpresa(), "Empresa"), TITULO));
            emisor.addElement(new Paragraph(valor(empresa.direccionCompleta(), ""), NORMAL));
            emisor.addElement(new Paragraph("CUIT: " + arca.cuit(), NEGRITA));
            emisor.addElement(new Paragraph("Condición fiscal: " + arca.condicionFiscal().getDescripcion(), NORMAL));
            cabecera.addCell(emisor);

            PdfPCell letra = celda();
            letra.setHorizontalAlignment(Element.ALIGN_CENTER);
            Paragraph l = new Paragraph(letra(venta.getTipoComprobante()), new Font(Font.FontFamily.HELVETICA, 25, Font.BOLD));
            l.setAlignment(Element.ALIGN_CENTER);
            letra.addElement(l);
            letra.addElement(centrado("Código " + codigoComprobante(venta.getTipoComprobante()), NORMAL));
            cabecera.addCell(letra);

            PdfPCell comprobante = celda();
            comprobante.addElement(new Paragraph(nombreComprobante(venta.getTipoComprobante()), TITULO));
            comprobante.addElement(new Paragraph("Punto de venta: " + String.format("%05d", venta.getPuntoVenta()), NORMAL));
            comprobante.addElement(new Paragraph("Comprobante: " + String.format("%08d", venta.getNumeroComprobante()), NORMAL));
            LocalDate fechaComprobante = venta.getFechaComprobante() == null
                    ? venta.getFechaVenta().toLocalDate() : venta.getFechaComprobante();
            comprobante.addElement(new Paragraph("Fecha: " + fechaComprobante.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), NORMAL));
            cabecera.addCell(comprobante);
            documento.add(cabecera);

            Paragraph prueba = new Paragraph("COMPROBANTE EMITIDO EN HOMOLOGACIÓN - SIN VALIDEZ FISCAL",
                    new Font(Font.FontFamily.HELVETICA, 11, Font.BOLD, BaseColor.RED));
            prueba.setAlignment(Element.ALIGN_CENTER);
            prueba.setSpacingBefore(10);
            prueba.setSpacingAfter(10);
            documento.add(prueba);

            PdfPTable receptor = new PdfPTable(1);
            receptor.setWidthPercentage(100);
            PdfPCell rc = celda();
            rc.addElement(new Paragraph("Cliente: " + venta.getClienteDescripcion(), NEGRITA));
            String documentoCliente = venta.getCliente() != null ? venta.getCliente().getDni() : venta.getClienteDocumentoExterno();
            if (documentoCliente != null && !documentoCliente.isBlank()) rc.addElement(new Paragraph("DNI/CUIT: " + documentoCliente, NORMAL));
            rc.addElement(new Paragraph("Origen: " + venta.getOrigenDescripcion()
                    + (venta.getOrdenExternaId() == null ? "" : " · Orden: " + venta.getOrdenExternaId()), NORMAL));
            receptor.addCell(rc);
            documento.add(receptor);

            PdfPTable items = new PdfPTable(new float[]{55, 10, 17, 18});
            items.setWidthPercentage(100);
            items.setSpacingBefore(12);
            encabezado(items, "Producto"); encabezado(items, "Cant.");
            encabezado(items, "Precio unit."); encabezado(items, "Subtotal");
            for (VentaItem item : venta.getItems()) {
                items.addCell(celdaTexto(item.getDescripcionProducto(), Element.ALIGN_LEFT));
                items.addCell(celdaTexto(String.valueOf(item.getCantidad()), Element.ALIGN_CENTER));
                items.addCell(celdaTexto("$ " + monto(item.getPrecioUnitario()), Element.ALIGN_RIGHT));
                items.addCell(celdaTexto("$ " + monto(item.getSubtotal()), Element.ALIGN_RIGHT));
            }
            documento.add(items);

            PdfPTable totales = new PdfPTable(new float[]{75, 25});
            totales.setWidthPercentage(100);
            totales.setSpacingBefore(8);
            if (venta.getTipoComprobante() == TipoComprobante.FACTURA_A) {
                total(totales, "Neto gravado", venta.getTotalNeto());
                total(totales, "IVA", venta.getTotalIva());
            }
            total(totales, "TOTAL", venta.getTotal());
            documento.add(totales);

            PdfPTable autorizacion = new PdfPTable(new float[]{25, 75});
            autorizacion.setWidthPercentage(100);
            autorizacion.setSpacingBefore(14);
            Image qr = Image.getInstance(generarQr(venta, arca));
            qr.scaleToFit(110, 110);
            PdfPCell qrCell = celda(); qrCell.addElement(qr); autorizacion.addCell(qrCell);
            PdfPCell cae = celda();
            cae.addElement(new Paragraph("CAE: " + venta.getCae(), NEGRITA));
            cae.addElement(new Paragraph("Vencimiento CAE: "
                    + venta.getFechaVencimientoCae().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), NORMAL));
            cae.addElement(new Paragraph("Este documento fue autorizado en el ambiente de homologación de ARCA.", NORMAL));
            autorizacion.addCell(cae);
            documento.add(autorizacion);
            documento.close();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el PDF del comprobante", e);
        }
    }

    private byte[] generarQr(Venta venta, ConfiguracionArcaService.Credenciales arca) throws Exception {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("ver", 1);
        LocalDate fecha = venta.getFechaComprobante() == null
                ? venta.getFechaVenta().toLocalDate() : venta.getFechaComprobante();
        datos.put("fecha", fecha.toString());
        datos.put("cuit", Long.parseLong(arca.cuit()));
        datos.put("ptoVta", venta.getPuntoVenta());
        datos.put("tipoCmp", codigoComprobante(venta.getTipoComprobante()));
        datos.put("nroCmp", venta.getNumeroComprobante());
        datos.put("importe", venta.getTotal().setScale(2, RoundingMode.HALF_UP));
        datos.put("moneda", "PES");
        datos.put("ctz", 1);
        DocumentoReceptor receptor = documentoReceptor(venta);
        if (receptor.numero() > 0) {
            datos.put("tipoDocRec", receptor.tipo());
            datos.put("nroDocRec", receptor.numero());
        }
        datos.put("tipoCodAut", "E");
        datos.put("codAut", Long.parseLong(venta.getCae()));
        String json = objectMapper.writeValueAsString(datos);
        String base64 = Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        String url = "https://www.arca.gob.ar/fe/qr/?p=" + base64;
        BitMatrix matriz = new QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, 260, 260);
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(matriz, "PNG", salida);
        return salida.toByteArray();
    }

    private DocumentoReceptor documentoReceptor(Venta venta) {
        String valor = venta.getCliente() != null ? venta.getCliente().getDni() : venta.getClienteDocumentoExterno();
        String digitos = valor == null ? "" : valor.replaceAll("\\D", "");
        if (digitos.length() == 11) return new DocumentoReceptor(80, Long.parseLong(digitos));
        if (digitos.length() == 7 || digitos.length() == 8) return new DocumentoReceptor(96, Long.parseLong(digitos));
        return new DocumentoReceptor(99, 0L);
    }

    private PdfPCell celda() { PdfPCell c = new PdfPCell(); c.setPadding(8); return c; }
    private PdfPCell celdaTexto(String texto, int alineacion) {
        PdfPCell c = new PdfPCell(new Phrase(texto, NORMAL)); c.setPadding(5); c.setHorizontalAlignment(alineacion); return c;
    }
    private void encabezado(PdfPTable tabla, String texto) {
        PdfPCell c = new PdfPCell(new Phrase(texto, NEGRITA)); c.setPadding(6); c.setBackgroundColor(new BaseColor(235,235,235)); tabla.addCell(c);
    }
    private void total(PdfPTable tabla, String etiqueta, BigDecimal valor) {
        PdfPCell e = celdaTexto(etiqueta, Element.ALIGN_RIGHT); e.setBorder(Rectangle.NO_BORDER); tabla.addCell(e);
        PdfPCell v = celdaTexto("$ " + monto(valor), Element.ALIGN_RIGHT); v.setBorder(Rectangle.NO_BORDER); tabla.addCell(v);
    }
    private Paragraph centrado(String texto, Font font) { Paragraph p = new Paragraph(texto, font); p.setAlignment(Element.ALIGN_CENTER); return p; }
    private String monto(BigDecimal valor) { return (valor == null ? BigDecimal.ZERO : valor).setScale(2, RoundingMode.HALF_UP).toPlainString(); }
    private String valor(String valor, String defecto) { return valor == null || valor.isBlank() ? defecto : valor; }
    private String letra(TipoComprobante tipo) { return tipo == TipoComprobante.FACTURA_A ? "A" : tipo == TipoComprobante.FACTURA_B ? "B" : "C"; }
    private String nombreComprobante(TipoComprobante tipo) { return "FACTURA " + letra(tipo); }
    private int codigoComprobante(TipoComprobante tipo) {
        return switch (tipo) { case FACTURA_A -> 1; case FACTURA_B -> 6; case FACTURA_C -> 11;
            case NOTA_CREDITO_A -> 3; case NOTA_CREDITO_B -> 8; };
    }
    private record DocumentoReceptor(Integer tipo, Long numero) {}
}

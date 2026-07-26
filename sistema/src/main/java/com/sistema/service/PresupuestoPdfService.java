package com.sistema.service;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;
import com.sistema.model.CondicionIva;
import com.sistema.model.DetallePresupuesto;
import com.sistema.model.Presupuesto;
import com.sistema.model.PresupuestoFooter;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Service
public class PresupuestoPdfService {

    private static final DecimalFormat DF = new DecimalFormat("#,##0.00");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    public void generarPdf(Presupuesto p, OutputStream out) {
        Document document = new Document(PageSize.A4, 36, 36, 15, 80);

        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            writer.setPageEvent(new PresupuestoFooter());

            document.open();

            agregarHeader(document, p);
            agregarDatosCliente(document, p);
            agregarCajaInfo(document, p);
            agregarFormaPago(document, p);
            agregarTablaItems(document, p);
            agregarTotales(document, p);

        } catch (Exception e) {
            throw new RuntimeException("Error generando PDF", e);
        } finally {
            document.close();
        }
    }

    // ==========================================
// HEADER: Título + Logo + Empresa
// ==========================================
    private void agregarHeader(Document document, Presupuesto p) throws Exception {
        Font titulo = FontFactory.getFont(FontFactory.HELVETICA, 16, Font.BOLD); // ← más chico
        Font empresaFont = FontFactory.getFont(FontFactory.HELVETICA, 8, Font.NORMAL, BaseColor.GRAY); // ← más chico

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new int[]{50, 50});

        // TÍTULO
        PdfPCell tituloCell = new PdfPCell(new Phrase("Presupuesto", titulo));
        tituloCell.setBorder(Rectangle.NO_BORDER);
        tituloCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        tituloCell.setPadding(0); // ← sin padding
        table.addCell(tituloCell);

        // LOGO
        try {
            Image logo = Image.getInstance(getClass().getResource("/static/img/LOGO.jpg"));
            logo.scaleToFit(340, 140);

            PdfPCell logoCell = new PdfPCell(logo);
            logoCell.setBorder(Rectangle.NO_BORDER);
            logoCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            logoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            logoCell.setPadding(0);
            table.addCell(logoCell);
        } catch (Exception e) {
            PdfPCell empty = new PdfPCell();
            empty.setBorder(Rectangle.NO_BORDER);
            table.addCell(empty);
        }

        document.add(table);

        // DATOS EMPRESA
        Paragraph empresa = new Paragraph(
                "MOBEZA ELECTRICIDAD · Acceso Norte S/N · 2681 Etruria, Argentina",
                empresaFont
        );
        empresa.setSpacingAfter(4);
        document.add(empresa);
    }

    // ==========================================
// DATOS CLIENTE (PARA)
// ==========================================
    private void agregarDatosCliente(Document document, Presupuesto p) throws Exception {
        Font bold = FontFactory.getFont(FontFactory.HELVETICA, 9, Font.BOLD);
        Font normal = FontFactory.getFont(FontFactory.HELVETICA, 9);

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new int[]{50, 50});
        table.setSpacingBefore(2);

        // COLUMNA IZQUIERDA: Cliente
        PdfPCell leftCell = new PdfPCell();
        leftCell.setBorder(Rectangle.NO_BORDER);
        leftCell.setPadding(2);

        Paragraph para = new Paragraph();
        para.add(new Chunk("PARA: ", bold));

        String clienteNombre = p.getCliente() != null
                ? p.getCliente().getNombre() + " " + p.getCliente().getApellido()
                + (p.getCliente().getDireccion() != null
                ? " · " + p.getCliente().getDireccion()
                : "")
                : "Consumidor Final";

        para.add(new Chunk(clienteNombre + "\n", normal));

        // ← AGREGAR DNI
        if (p.getCliente() != null && p.getCliente().getDni() != null
                && !p.getCliente().getDni().isEmpty()) {
            para.add(new Chunk("CUIT: ", bold));
            para.add(new Chunk(p.getCliente().getDni() + "\n", normal));
        }

        leftCell.addElement(para);
        table.addCell(leftCell);

        // COLUMNA DERECHA: Info presupuesto
        PdfPCell rightCell = new PdfPCell();
        rightCell.setBorder(Rectangle.NO_BORDER);
        rightCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        rightCell.setPadding(2);

        String validoHasta = p.getFechaValidez() != null
                ? p.getFechaValidez().format(DATE_FMT)
                : p.getFecha().plusDays(30).format(DATE_FMT);

        Paragraph info = new Paragraph();
        info.setAlignment(Element.ALIGN_RIGHT);
        info.add(new Chunk("N°: ", normal));
        info.add(new Chunk(p.getCodigo() + "   ", bold));
        info.add(new Chunk("Emisión: ", normal));
        info.add(new Chunk(p.getFecha().format(DATE_FMT) + "   ", bold));
        info.add(new Chunk("Válido hasta: ", normal));
        info.add(new Chunk(validoHasta + "\n", bold));

        rightCell.addElement(info);
        table.addCell(rightCell);

        document.add(table);
        document.add(new Paragraph(" ", FontFactory.getFont(FontFactory.HELVETICA, 4)));
    }

    // ==========================================
// CAJA AMARILLA
// ==========================================
    private void agregarCajaInfo(Document document, Presupuesto p) throws Exception {
        Font white = FontFactory.getFont(FontFactory.HELVETICA, 8, Font.BOLD, BaseColor.WHITE);   // ← más chico
        Font whiteSmall = FontFactory.getFont(FontFactory.HELVETICA, 7, Font.NORMAL, BaseColor.WHITE); // ← más chico

        BaseColor amarillo = new BaseColor(218, 198, 125);

        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setWidths(new int[]{25, 25, 25, 25});
        table.setSpacingBefore(4);
        table.setSpacingAfter(8);

        // Presupuesto n°
        PdfPCell cell1 = new PdfPCell();
        cell1.setBackgroundColor(amarillo);
        cell1.setBorder(Rectangle.NO_BORDER);
        cell1.setPadding(5);
        Paragraph p1 = new Paragraph();
        p1.add(new Chunk("Presupuesto n°:\n", whiteSmall));
        p1.add(new Chunk(p.getCodigo(), white));
        cell1.addElement(p1);
        table.addCell(cell1);

        // Fecha emisión
        PdfPCell cell2 = new PdfPCell();
        cell2.setBackgroundColor(amarillo);
        cell2.setBorder(Rectangle.NO_BORDER);
        cell2.setPadding(5);
        Paragraph p2 = new Paragraph();
        p2.add(new Chunk("Fecha de emisión:\n", whiteSmall));
        p2.add(new Chunk(p.getFecha().format(DATE_FMT), white));
        cell2.addElement(p2);
        table.addCell(cell2);

        // Válido hasta
        PdfPCell cell3 = new PdfPCell();
        cell3.setBackgroundColor(amarillo);
        cell3.setBorder(Rectangle.NO_BORDER);
        cell3.setPadding(5);
        Paragraph p3 = new Paragraph();
        String validoHasta = p.getFechaValidez() != null
                ? p.getFechaValidez().format(DATE_FMT)
                : p.getFecha().plusDays(30).format(DATE_FMT);
        p3.add(new Chunk("Válido hasta:\n", whiteSmall));
        p3.add(new Chunk(validoHasta, white));
        cell3.addElement(p3);
        table.addCell(cell3);

        // Total a pagar
        PdfPCell cell4 = new PdfPCell();
        cell4.setBackgroundColor(amarillo);
        cell4.setBorder(Rectangle.NO_BORDER);
        cell4.setPadding(5);
        Paragraph p4 = new Paragraph();
        p4.add(new Chunk("Total a pagar:\n", whiteSmall));
        p4.add(new Chunk(simbolo(p) + DF.format(convertir(p.getTotal(), p)), white));
        cell4.addElement(p4);
        table.addCell(cell4);

        document.add(table);
    }

    // ==========================================
// FORMA DE PAGO
// ==========================================
    private void agregarFormaPago(Document document, Presupuesto p) throws Exception {
        Font bold = FontFactory.getFont(FontFactory.HELVETICA, 9, Font.BOLD);
        Font normal = FontFactory.getFont(FontFactory.HELVETICA, 9);

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setSpacingBefore(2);
        table.setSpacingAfter(8);
        table.setWidths(new int[]{25, 75});

        PdfPCell label = new PdfPCell(new Phrase("Forma de pago:", bold));
        label.setBorder(Rectangle.NO_BORDER);
        label.setPadding(2);
        table.addCell(label);

        String formaPago = p.getFormaPago() != null
                ? p.getFormaPago().toString() : "No especificada";

        PdfPCell value = new PdfPCell(new Phrase(formaPago, normal));
        value.setBorder(Rectangle.NO_BORDER);
        value.setPadding(2);
        table.addCell(value);

        // ← AGREGAR TIPO DE CAMBIO SI ES USD
        if (p.getMoneda() == Presupuesto.Moneda.USD
                && p.getNotaTipoCambio() != null
                && !p.getNotaTipoCambio().isBlank()) {

            PdfPCell labelTC = new PdfPCell(new Phrase("Nota:", bold));
            labelTC.setBorder(Rectangle.NO_BORDER);
            labelTC.setPadding(2);
            table.addCell(labelTC);

            PdfPCell valueTC = new PdfPCell(new Phrase(p.getNotaTipoCambio(), normal));
            valueTC.setBorder(Rectangle.NO_BORDER);
            valueTC.setPadding(2);
            table.addCell(valueTC);
        }

        document.add(table);
    }


    // ==========================================
    // TABLA DE ITEMS CON IVA
    // ==========================================
    private void agregarTablaItems(Document document, Presupuesto p) throws Exception {

        Font header = FontFactory.getFont(FontFactory.HELVETICA, 9, Font.BOLD);
        Font normal = FontFactory.getFont(FontFactory.HELVETICA, 9);

        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setWidths(new int[]{45, 12, 18, 12, 13});

        // Headers
        table.addCell(celdaHeader("Descripción", header));
        table.addCell(celdaHeader("Cant.", header));
        table.addCell(celdaHeader("Precio Unit. (" + (p.getMoneda() == Presupuesto.Moneda.USD ? "USD" : "ARS") + ")", header));
        table.addCell(celdaHeader("IVA (" + (p.getMoneda() == Presupuesto.Moneda.USD ? "USD" : "ARS") + ")", header));
        table.addCell(celdaHeader("Importe (" + (p.getMoneda() == Presupuesto.Moneda.USD ? "USD" : "ARS") + ")", header));

        boolean esConsumidorFinal = p.getCliente() == null ||
                p.getCliente().getCondicionIva() == CondicionIva.CONSUMIDOR_FINAL;

        for (DetallePresupuesto d : p.getDetalles()) {

            BigDecimal subtotalConIva = d.getSubtotal();
            BigDecimal cantidad = BigDecimal.valueOf(d.getCantidad());

            BigDecimal alicuotaIva = d.getAlicuotaIva() != null
                    ? d.getAlicuotaIva()
                    : BigDecimal.ZERO;

            BigDecimal ivaRate = alicuotaIva
                    .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);

            // 🔹 Neto e IVA DESDE subtotal
            BigDecimal netoItem = subtotalConIva.divide(
                    BigDecimal.ONE.add(ivaRate),
                    2,
                    RoundingMode.HALF_UP
            );

            BigDecimal ivaItem = subtotalConIva.subtract(netoItem);

            BigDecimal precioUnitarioNeto = netoItem.divide(
                    cantidad,
                    2,
                    RoundingMode.HALF_UP
            );

            // IVA total por línea (no unitario)
            BigDecimal ivaLinea = ivaItem;

            // Consumidor final: no discriminamos IVA
            if (esConsumidorFinal) {
                precioUnitarioNeto = subtotalConIva.divide(
                        cantidad,
                        2,
                        RoundingMode.HALF_UP
                );
                ivaLinea = BigDecimal.ZERO;
            }

            // ---------------- CELDAS ----------------

            // Descripción
            PdfPCell descCell = new PdfPCell(
                    new Phrase(d.getDescripcionProducto(), normal)
            );
            descCell.setPadding(6);
            descCell.setBorderColor(BaseColor.LIGHT_GRAY);
            table.addCell(descCell);

            // Cantidad
            table.addCell(celdaNormal(cantidad.toString(), normal));

            // Precio unitario (neto o final según condición IVA)
            table.addCell(celdaNormal(DF.format(convertir(precioUnitarioNeto, p)), normal));

            // IVA por línea
            table.addCell(celdaNormal(DF.format(convertir(ivaLinea, p)), normal));

            // Importe total (subtotal con IVA y descuento)
            table.addCell(celdaNormal(DF.format(convertir(subtotalConIva, p)), normal));
        }

        document.add(table);
    }




    // ==========================================
    // TOTALES CON IVA
    // ==========================================
    private void agregarTotales(Document document, Presupuesto p) throws Exception {
        Font normal = FontFactory.getFont(FontFactory.HELVETICA, 10);
        Font bold = FontFactory.getFont(FontFactory.HELVETICA, 11, Font.BOLD);

        boolean esConsumidorFinal = p.getCliente() == null ||
                p.getCliente().getCondicionIva() == CondicionIva.CONSUMIDOR_FINAL;

        BigDecimal totalNeto = BigDecimal.ZERO;
        Map<BigDecimal, BigDecimal> ivasMap = new HashMap<>(); // IVA acumulado por alícuota

        for (DetallePresupuesto d : p.getDetalles()) {

            BigDecimal subtotalConIva = d.getSubtotal();
            BigDecimal alicuotaIva = d.getAlicuotaIva() != null
                    ? d.getAlicuotaIva()
                    : BigDecimal.ZERO;

            BigDecimal ivaRate = alicuotaIva
                    .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);

            BigDecimal netoItem = subtotalConIva.divide(
                    BigDecimal.ONE.add(ivaRate),
                    2,
                    RoundingMode.HALF_UP
            );

            BigDecimal ivaItem = subtotalConIva.subtract(netoItem);

            // Consumidor final: no discriminamos IVA
            if (esConsumidorFinal) {
                netoItem = subtotalConIva;
                ivaItem = BigDecimal.ZERO;
            }

            totalNeto = totalNeto.add(netoItem);

            if (alicuotaIva.compareTo(BigDecimal.ZERO) > 0) {
                ivasMap.merge(alicuotaIva, ivaItem, BigDecimal::add);
            }
        }



        BigDecimal totalIva = ivasMap.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalFinal = totalNeto.add(totalIva);

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(50);
        table.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.setSpacingBefore(15);

        if (esConsumidorFinal) {

            String labelMoneda = "Total (" +
                    (p.getMoneda() == Presupuesto.Moneda.USD ? "USD" : "ARS")
                    + "):";

            PdfPCell labelTotal = new PdfPCell(new Phrase(labelMoneda, bold));
            labelTotal.setBorder(Rectangle.NO_BORDER);
            labelTotal.setHorizontalAlignment(Element.ALIGN_RIGHT);
            labelTotal.setPadding(4);
            table.addCell(labelTotal);

            PdfPCell valorTotal = new PdfPCell(
                    new Phrase(simbolo(p) + DF.format(convertir(totalFinal, p)), bold)
            );
            valorTotal.setBorder(Rectangle.NO_BORDER);
            valorTotal.setHorizontalAlignment(Element.ALIGN_RIGHT);
            valorTotal.setPadding(4);
            table.addCell(valorTotal);
        }

         else {
            // Mostrar neto
            PdfPCell labelNeto = new PdfPCell(new Phrase("Total neto", normal));
            labelNeto.setBorder(Rectangle.NO_BORDER);
            labelNeto.setHorizontalAlignment(Element.ALIGN_RIGHT);
            labelNeto.setPadding(4);
            table.addCell(labelNeto);

            PdfPCell valorNeto = new PdfPCell(new Phrase(simbolo(p) + DF.format(convertir(totalNeto, p)), normal));
            valorNeto.setBorder(Rectangle.NO_BORDER);
            valorNeto.setHorizontalAlignment(Element.ALIGN_RIGHT);
            valorNeto.setPadding(4);
            table.addCell(valorNeto);

            // Mostrar IVA discriminado por alícuota
            for (Map.Entry<BigDecimal, BigDecimal> entry : ivasMap.entrySet()) {
                BigDecimal alicuota = entry.getKey();
                BigDecimal iva = entry.getValue();

                if (alicuota.compareTo(BigDecimal.ZERO) > 0 && iva.compareTo(BigDecimal.ZERO) > 0) {
                    PdfPCell labelIva = new PdfPCell(new Phrase("IVA " + DF.format(alicuota) + " %", normal));
                    labelIva.setBorder(Rectangle.NO_BORDER);
                    labelIva.setHorizontalAlignment(Element.ALIGN_RIGHT);
                    labelIva.setPadding(4);
                    table.addCell(labelIva);

                    PdfPCell valorIva = new PdfPCell(new Phrase(simbolo(p) + DF.format(convertir(iva, p)), normal));
                    valorIva.setBorder(Rectangle.NO_BORDER);
                    valorIva.setHorizontalAlignment(Element.ALIGN_RIGHT);
                    valorIva.setPadding(4);
                    table.addCell(valorIva);
                }
            }

            // Total final
            String labelMoneda = "Total (" +
                    (p.getMoneda() == Presupuesto.Moneda.USD ? "USD" : "ARS")
                    + "):";

            PdfPCell labelTotal = new PdfPCell(new Phrase(labelMoneda, bold));
            labelTotal.setBorder(Rectangle.NO_BORDER);
            labelTotal.setHorizontalAlignment(Element.ALIGN_RIGHT);
            labelTotal.setPadding(4);
            table.addCell(labelTotal);

            PdfPCell valorTotal = new PdfPCell(new Phrase(simbolo(p) + DF.format(convertir(totalFinal, p)), bold));
            valorTotal.setBorder(Rectangle.NO_BORDER);
            valorTotal.setHorizontalAlignment(Element.ALIGN_RIGHT);
            valorTotal.setPadding(4);
            table.addCell(valorTotal);
        }

        document.add(table);
    }



    // ==========================================
    // HELPERS
    // ==========================================
    private PdfPCell celdaHeader(String texto, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(texto, font));
        cell.setBackgroundColor(BaseColor.LIGHT_GRAY);
        cell.setPadding(6);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setBorderColor(BaseColor.GRAY);
        return cell;
    }

    private PdfPCell celdaNormal(String texto, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(texto, font));
        cell.setPadding(6);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setBorderColor(BaseColor.LIGHT_GRAY);
        return cell;
    }

    private String simbolo(Presupuesto p) {
        return p.getMoneda() == Presupuesto.Moneda.USD ? "U$D " : "$ ";
    }

    private BigDecimal convertir(BigDecimal monto, Presupuesto p) {
        if (p.getMoneda() == Presupuesto.Moneda.USD
                && p.getTipoCambio() != null
                && p.getTipoCambio().compareTo(BigDecimal.ZERO) > 0) {
            return monto.divide(p.getTipoCambio(), 2, RoundingMode.HALF_UP);
        }
        return monto;
    }
}


package com.sistema.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import com.sistema.model.*;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.OutputStream;
import java.time.format.DateTimeFormatter;

@Service
public class RemitoImpresionService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    public void generarRemitoPdf(Remito remito, OutputStream out) throws DocumentException {

        // Tamaño A4 horizontal para que sea más ancho
        Document document = new Document(PageSize.A4);
        PdfWriter.getInstance(document, out);
        document.open();

        // Fuentes
        Font fontTitle = new Font(Font.HELVETICA, 14, Font.BOLD);
        Font fontBold = new Font(Font.HELVETICA, 10, Font.BOLD);
        Font font = new Font(Font.HELVETICA, 9);
        Font fontSmall = new Font(Font.HELVETICA, 8);
        Font fontTiny = new Font(Font.HELVETICA, 7);

        // ==========================================
        // HEADER PRINCIPAL
        // ==========================================

        PdfPTable headerTable = new PdfPTable(3);
        headerTable.setWidthPercentage(100);
        headerTable.setWidths(new float[]{2.5f, 0.5f, 2.5f});

        PdfPCell leftCell = new PdfPCell();
        leftCell.setBorder(Rectangle.BOX);
        leftCell.setPadding(6);

// 🔹 TABLA INTERNA (logo + datos)
        PdfPTable innerTable = new PdfPTable(2);
        innerTable.setWidthPercentage(100);
        innerTable.setWidths(new float[]{1.5f, 3f});

// LOGO
        PdfPCell logoCell = new PdfPCell();
        logoCell.setBorder(Rectangle.NO_BORDER);

        try {
            Image logo = Image.getInstance(getClass().getResource("/static/img/LOGO.jpg"));
            logo.scaleToFit(100, 70);
            logoCell.addElement(logo);
        } catch (Exception e) {
            logoCell.addElement(new Paragraph(" "));
        }

        innerTable.addCell(logoCell);

// DATOS EMPRESA
        PdfPCell dataCell = new PdfPCell();
        dataCell.setBorder(Rectangle.NO_BORDER);

        dataCell.addElement(new Paragraph("MOBEZA ELECTRICIDAD", fontBold));
        dataCell.addElement(new Paragraph("Acceso Norte, S/N", font));
        dataCell.addElement(new Paragraph("CP 2681 Etruria, Argentina", font));
        dataCell.addElement(new Paragraph("Tel: (03534) 082798", fontSmall));
        dataCell.addElement(new Paragraph("Email: nerypelaye@gmail.com", fontSmall));

        innerTable.addCell(dataCell);

// agregar tabla a la celda
        leftCell.addElement(innerTable);

// agregar al header
        headerTable.addCell(leftCell);

        // COLUMNA CENTRO - Letra R
        PdfPCell centerCell = new PdfPCell();
        centerCell.setBorder(Rectangle.BOX);
        centerCell.setPadding(5);
        centerCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        centerCell.setHorizontalAlignment(Element.ALIGN_CENTER);

        Paragraph letra = new Paragraph("R", new Font(Font.HELVETICA, 36, Font.BOLD));
        letra.setAlignment(Element.ALIGN_CENTER);
        centerCell.addElement(letra);

        Paragraph docR = new Paragraph("COD. 91", fontTiny);
        docR.setAlignment(Element.ALIGN_CENTER);
        centerCell.addElement(docR);

        headerTable.addCell(centerCell);

        // COLUMNA DERECHA - Datos del remito
        PdfPCell rightCell = new PdfPCell();
        rightCell.setBorder(Rectangle.BOX);
        rightCell.setPadding(8);

        Paragraph tituloRemito = new Paragraph("REMITO", fontTitle);
        tituloRemito.setAlignment(Element.ALIGN_CENTER);
        rightCell.addElement(tituloRemito);

        Paragraph subtitulo = new Paragraph("DOCUMENTO NO VÁLIDO", fontSmall);
        subtitulo.setAlignment(Element.ALIGN_CENTER);
        rightCell.addElement(subtitulo);

        Paragraph subtitulo2 = new Paragraph("COMO FACTURA", fontSmall);
        subtitulo2.setAlignment(Element.ALIGN_CENTER);
        rightCell.addElement(subtitulo2);

        rightCell.addElement(new Paragraph(" ", fontTiny));

        Paragraph numero = new Paragraph("Nº " + remito.getCodigo(), fontBold);
        numero.setAlignment(Element.ALIGN_CENTER);
        rightCell.addElement(numero);

        rightCell.addElement(new Paragraph(" ", fontSmall));

        headerTable.addCell(rightCell);

        document.add(headerTable);
        document.add(new Paragraph(" ", fontTiny));

        // ==========================================
        // DATOS DEL CLIENTE Y FECHA
        // ==========================================
        PdfPTable clienteTable = new PdfPTable(2);
        clienteTable.setWidthPercentage(100);
        clienteTable.setWidths(new float[]{3f, 1.5f});

        // Datos del cliente
        PdfPCell clienteCell = new PdfPCell();
        clienteCell.setBorder(Rectangle.BOX);
        clienteCell.setPadding(5);

        String nombreCliente = "__________________";
        if (remito.getCliente() != null) {
            nombreCliente = remito.getCliente().getNombre() + " " + remito.getCliente().getApellido();
        }

        clienteCell.addElement(new Paragraph("Sr/es: " + nombreCliente, font));

        String domicilio = "__________________";
        if (remito.getDireccionEntrega() != null && !remito.getDireccionEntrega().isEmpty()) {
            domicilio = remito.getDireccionEntrega();
        } else if (remito.getCliente() != null && remito.getCliente().getDireccion() != null) {
            domicilio = remito.getCliente().getDireccion();
        }

        clienteCell.addElement(new Paragraph("Domicilio: " + domicilio, font));

        clienteTable.addCell(clienteCell);

        // Fecha
        PdfPCell fechaCell = new PdfPCell();
        fechaCell.setBorder(Rectangle.BOX);
        fechaCell.setPadding(5);

        String fecha = remito.getFechaEmision().format(DATE_FORMATTER);
        fechaCell.addElement(new Paragraph("Fecha: " + fecha, font));

        clienteTable.addCell(fechaCell);

        document.add(clienteTable);

        // ==========================================
        // CONDICIONES IVA Y CONDICIONES DE VENTA
        // ==========================================
        PdfPTable condicionesTable = new PdfPTable(4);
        condicionesTable.setWidthPercentage(100);
        condicionesTable.setWidths(new float[]{1.5f, 1f, 1f, 2f});

        // IVA
        PdfPCell ivaLabelCell = new PdfPCell(new Phrase("IVA", font));
        ivaLabelCell.setBorder(Rectangle.BOX);
        ivaLabelCell.setPadding(5);
        condicionesTable.addCell(ivaLabelCell);

        String condicionIva = "Consumidor Final";
        if (remito.getCliente() != null && remito.getCliente().getCondicionIva() != null) {
            condicionIva = switch (remito.getCliente().getCondicionIva()) {
                case RESPONSABLE_INSCRIPTO -> "Responsable Inscripto";
                case CONSUMIDOR_FINAL -> "Consumidor Final";
                default -> "Consumidor Final";
            };
        }

        PdfPCell ivaValueCell = new PdfPCell(new Phrase(condicionIva, font));
        ivaValueCell.setBorder(Rectangle.BOX);
        ivaValueCell.setPadding(5);
        ivaValueCell.setColspan(3);
        condicionesTable.addCell(ivaValueCell);

        // CUIT del cliente
        PdfPCell cuitLabelCell = new PdfPCell(new Phrase("CUIT", font));
        cuitLabelCell.setBorder(Rectangle.BOX);
        cuitLabelCell.setPadding(5);
        condicionesTable.addCell(cuitLabelCell);

        String cuitCliente = "_________";
        if (remito.getCliente() != null && remito.getCliente().getDni() != null) {
            cuitCliente = remito.getCliente().getDni();
        }

        PdfPCell cuitValueCell = new PdfPCell(new Phrase(cuitCliente, font));
        cuitValueCell.setBorder(Rectangle.BOX);
        cuitValueCell.setPadding(5);
        cuitValueCell.setColspan(3);
        condicionesTable.addCell(cuitValueCell);

        // Condiciones de venta
        PdfPCell condVentaCell = new PdfPCell(new Phrase("Condiciones de venta", font));
        condVentaCell.setBorder(Rectangle.BOX);
        condVentaCell.setPadding(5);
        condicionesTable.addCell(condVentaCell);

        String contado = "Contado [ ]";
        String ctaCte = "Cta. Cte. [ ]";
        String tarjeta = "Tarjeta [ ]";

        if (remito.getVenta() != null && remito.getVenta().getFormaPago() != null) {

            if (remito.getVenta().getFormaPago() == FormaPago.CONTADO) {
                contado = "Contado [X]";
            }
            else if (remito.getVenta().getFormaPago() == FormaPago.TARJETA) {
                tarjeta = "Tarjeta [X]";
            }
            else {
                ctaCte = "Cta. Cte. [X]";
            }
        }
        PdfPCell contadoCell = new PdfPCell(new Phrase(contado, font));
        contadoCell.setBorder(Rectangle.BOX);
        contadoCell.setPadding(5);
        condicionesTable.addCell(contadoCell);

        PdfPCell tarjetaCell = new PdfPCell(new Phrase(tarjeta, font));
        tarjetaCell.setBorder(Rectangle.BOX);
        tarjetaCell.setPadding(5);
        condicionesTable.addCell(tarjetaCell);

        PdfPCell ctaCteCell = new PdfPCell(new Phrase(ctaCte, font));
        ctaCteCell.setBorder(Rectangle.BOX);
        ctaCteCell.setPadding(5);
        condicionesTable.addCell(ctaCteCell);

        document.add(condicionesTable);

        // ==========================================
        // TABLA DE ITEMS
        // ==========================================
        PdfPTable itemsTable = new PdfPTable(2);
        itemsTable.setWidthPercentage(100);
        itemsTable.setWidths(new float[]{1f, 7f});
        itemsTable.setSpacingBefore(5);

        // Header
        PdfPCell cantidadHeader = new PdfPCell(new Phrase("CANTIDAD", fontBold));
        cantidadHeader.setBorder(Rectangle.BOX);
        cantidadHeader.setPadding(5);
        cantidadHeader.setBackgroundColor(new Color(220, 220, 220));
        cantidadHeader.setHorizontalAlignment(Element.ALIGN_CENTER);
        itemsTable.addCell(cantidadHeader);

        PdfPCell descripcionHeader = new PdfPCell(new Phrase("DESCRIPCIÓN", fontBold));
        descripcionHeader.setBorder(Rectangle.BOX);
        descripcionHeader.setPadding(5);
        descripcionHeader.setBackgroundColor(new Color(220, 220, 220));
        itemsTable.addCell(descripcionHeader);

        // Items del remito
        for (RemitoItem item : remito.getItems()) {
            // Cantidad
            PdfPCell cantCell = new PdfPCell(new Phrase(item.getCantidad().toString(), font));
            cantCell.setBorder(Rectangle.BOX);
            cantCell.setPadding(5);
            cantCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cantCell.setMinimumHeight(25);
            itemsTable.addCell(cantCell);

            // Descripción
            String descripcion = item.getDescripcionProducto();

            PdfPCell descCell = new PdfPCell(new Phrase(descripcion, font));
            descCell.setBorder(Rectangle.BOX);
            descCell.setPadding(5);
            descCell.setMinimumHeight(25);
            itemsTable.addCell(descCell);
        }

        // Líneas vacías para completar el remito (mínimo 10 líneas)
        int lineasVacias = Math.max(0, 10 - remito.getItems().size());
        for (int i = 0; i < lineasVacias; i++) {
            PdfPCell emptyCell1 = new PdfPCell(new Phrase(" ", font));
            emptyCell1.setBorder(Rectangle.BOX);
            emptyCell1.setPadding(5);
            emptyCell1.setMinimumHeight(25);
            itemsTable.addCell(emptyCell1);

            PdfPCell emptyCell2 = new PdfPCell(new Phrase(" ", font));
            emptyCell2.setBorder(Rectangle.BOX);
            emptyCell2.setPadding(5);
            emptyCell2.setMinimumHeight(25);
            itemsTable.addCell(emptyCell2);
        }

        document.add(itemsTable);

        // ==========================================
        // OBSERVACIONES
        // ==========================================
        document.add(new Paragraph(" ", fontSmall));

        PdfPTable obsTable = new PdfPTable(1);
        obsTable.setWidthPercentage(100);

        String observaciones = remito.getObservaciones() != null ?
                remito.getObservaciones() : "";

        PdfPCell obsCell = new PdfPCell(new Phrase("Observaciones: " + observaciones, font));
        obsCell.setBorder(Rectangle.BOX);
        obsCell.setPadding(8);
        obsCell.setMinimumHeight(40);
        obsTable.addCell(obsCell);

        document.add(obsTable);

        // ==========================================
        // FIRMAS
        // ==========================================
        document.add(new Paragraph(" ", font));

        PdfPTable firmasTable = new PdfPTable(2);
        firmasTable.setWidthPercentage(100);
        firmasTable.setWidths(new float[]{1f, 1f});
        firmasTable.setSpacingBefore(20);

        // Firma izquierda
        PdfPCell firmaIzq = new PdfPCell();
        firmaIzq.setBorder(Rectangle.NO_BORDER);
        firmaIzq.setMinimumHeight(60);
        Paragraph pIzq = new Paragraph("_________", font);
        pIzq.setAlignment(Element.ALIGN_CENTER);
        firmaIzq.addElement(pIzq);
        Paragraph labelIzq = new Paragraph("Firma y aclaración\nQUIEN ENTREGA", fontSmall);
        labelIzq.setAlignment(Element.ALIGN_CENTER);
        firmaIzq.addElement(labelIzq);
        firmasTable.addCell(firmaIzq);

        // Firma derecha
        PdfPCell firmaDer = new PdfPCell();
        firmaDer.setBorder(Rectangle.NO_BORDER);
        firmaDer.setMinimumHeight(60);
        Paragraph pDer = new Paragraph("_________", font);
        pDer.setAlignment(Element.ALIGN_CENTER);
        firmaDer.addElement(pDer);
        Paragraph labelDer = new Paragraph("Firma y aclaración\nQUIEN RECIBE", fontSmall);
        labelDer.setAlignment(Element.ALIGN_CENTER);
        firmaDer.addElement(labelDer);
        firmasTable.addCell(firmaDer);

        document.add(firmasTable);

        document.close();
    }
}

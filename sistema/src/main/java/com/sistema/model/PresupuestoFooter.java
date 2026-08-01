package com.sistema.model;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;

public class PresupuestoFooter extends PdfPageEventHelper {
    private final ConfiguracionDocumento configuracion;

    public PresupuestoFooter(ConfiguracionDocumento configuracion) {
        this.configuracion = configuracion;
    }

    @Override
    public void onEndPage(PdfWriter writer, Document document) {
        Font bold = new Font(Font.FontFamily.HELVETICA, 8, Font.BOLD);
        Font normal = new Font(Font.FontFamily.HELVETICA, 8);
        PdfContentByte contenido = writer.getDirectContent();
        float bordeInferior = document.getPageSize().getBottom();
        float parteSuperiorFooter = bordeInferior + 62;

        contenido.setLineWidth(0.5f);
        contenido.moveTo(document.left(), parteSuperiorFooter + 6);
        contenido.lineTo(document.right(), parteSuperiorFooter + 6);
        contenido.stroke();

        PdfPTable footer = new PdfPTable(3);
        footer.setTotalWidth(document.right() - document.left());
        try {
            footer.setWidths(new int[]{33, 33, 34});
            footer.addCell(celda(configuracion.getNombreContacto(),
                    Element.ALIGN_LEFT, bold));
            footer.addCell(celda(configuracion.getTelefono(),
                    Element.ALIGN_CENTER, bold));
            footer.addCell(celda(configuracion.getEmail(),
                    Element.ALIGN_RIGHT, bold));
            PdfPCell empresa = celda(datosEmpresa(), Element.ALIGN_LEFT, normal);
            empresa.setColspan(3);
            footer.addCell(empresa);
            footer.writeSelectedRows(0, -1, document.left(),
                    parteSuperiorFooter, contenido);
        } catch (DocumentException e) {
            throw new RuntimeException(e);
        }
    }

    private PdfPCell celda(String valor, int alineacion, Font fuente) {
        PdfPCell celda = new PdfPCell();
        celda.setBorder(Rectangle.NO_BORDER);
        celda.setPaddingTop(2);
        celda.setPaddingBottom(2);
        Paragraph texto = new Paragraph(valor == null ? "" : valor, fuente);
        texto.setLeading(9);
        texto.setAlignment(alineacion);
        celda.addElement(texto);
        return celda;
    }

    private String datosEmpresa() {
        StringBuilder datos = new StringBuilder(configuracion.getNombreEmpresa());
        agregarDato(datos, configuracion.getDireccion());
        agregarDato(datos, configuracion.ubicacionCompleta());
        if (configuracion.getCuit() != null && !configuracion.getCuit().isBlank()) {
            agregarDato(datos, "CUIT: " + configuracion.getCuit());
        }
        return datos.toString();
    }

    private void agregarDato(StringBuilder destino, String valor) {
        if (valor != null && !valor.isBlank()) {
            destino.append(" · ").append(valor);
        }
    }
}

package com.sistema.model;

public enum CondicionIva {

    RESPONSABLE_INSCRIPTO(1, "IVA Responsable Inscripto", true),
    IVA_SUJETO_EXENTO(4, "IVA Sujeto Exento", false),
    CONSUMIDOR_FINAL(5, "Consumidor Final", false),
    RESPONSABLE_MONOTRIBUTO(6, "Responsable Monotributo", true),
    SUJETO_NO_CATEGORIZADO(7, "Sujeto No Categorizado", false),
    PROVEEDOR_DEL_EXTERIOR(8, "Proveedor del Exterior", false),
    CLIENTE_DEL_EXTERIOR(9, "Cliente del Exterior", false),
    IVA_LIBERADO_LEY_19640(10, "IVA Liberado - Ley N.° 19.640", false),
    MONOTRIBUTISTA_SOCIAL(13, "Monotributista Social", true),
    IVA_NO_ALCANZADO(15, "IVA No Alcanzado", false),
    MONOTRIBUTO_TRABAJADOR_INDEPENDIENTE_PROMOVIDO(
            16, "Monotributo Trabajador Independiente Promovido", true);

    private final int codigoArca;
    private final String descripcion;
    private final boolean receptorFacturaA;

    CondicionIva(int codigoArca, String descripcion, boolean receptorFacturaA) {
        this.codigoArca = codigoArca;
        this.descripcion = descripcion;
        this.receptorFacturaA = receptorFacturaA;
    }

    public int getCodigoArca() {
        return codigoArca;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public boolean isReceptorFacturaA() {
        return receptorFacturaA;
    }
}


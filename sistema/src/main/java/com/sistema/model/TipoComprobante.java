package com.sistema.model;

public enum TipoComprobante {
    FACTURA_A(1, "Factura A", "A"),
    NOTA_DEBITO_A(2, "Nota de débito A", "A"),
    NOTA_CREDITO_A(3, "Nota de crédito A", "A"),
    FACTURA_B(6, "Factura B", "B"),
    NOTA_DEBITO_B(7, "Nota de débito B", "B"),
    NOTA_CREDITO_B(8, "Nota de crédito B", "B"),
    FACTURA_C(11, "Factura C", "C"),
    NOTA_DEBITO_C(12, "Nota de débito C", "C"),
    NOTA_CREDITO_C(13, "Nota de crédito C", "C");

    private final int codigoArca;
    private final String descripcion;
    private final String letra;

    TipoComprobante(int codigoArca, String descripcion, String letra) {
        this.codigoArca = codigoArca;
        this.descripcion = descripcion;
        this.letra = letra;
    }

    public int getCodigoArca() { return codigoArca; }
    public String getDescripcion() { return descripcion; }
    public String getLetra() { return letra; }

    public boolean isFactura() {
        return this == FACTURA_A || this == FACTURA_B || this == FACTURA_C;
    }

    public boolean isNotaCredito() {
        return this == NOTA_CREDITO_A || this == NOTA_CREDITO_B || this == NOTA_CREDITO_C;
    }

    public boolean isNotaDebito() {
        return this == NOTA_DEBITO_A || this == NOTA_DEBITO_B || this == NOTA_DEBITO_C;
    }

    public boolean discriminaIva() { return "A".equals(letra); }

    public TipoComprobante notaCreditoCorrespondiente() {
        return switch (this) {
            case FACTURA_A -> NOTA_CREDITO_A;
            case FACTURA_B -> NOTA_CREDITO_B;
            case FACTURA_C -> NOTA_CREDITO_C;
            default -> throw new IllegalStateException("El comprobante asociado debe ser una factura");
        };
    }

    public TipoComprobante notaDebitoCorrespondiente() {
        return switch (this) {
            case FACTURA_A -> NOTA_DEBITO_A;
            case FACTURA_B -> NOTA_DEBITO_B;
            case FACTURA_C -> NOTA_DEBITO_C;
            default -> throw new IllegalStateException("El comprobante asociado debe ser una factura");
        };
    }
}


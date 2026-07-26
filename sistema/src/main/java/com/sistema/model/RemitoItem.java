package com.sistema.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "remito_item")
@Getter @Setter @AllArgsConstructor @NoArgsConstructor
public class RemitoItem extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "remito_id", nullable = false)
    private Remito remito;

    @ManyToOne
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "producto_variante_id")
    private ProductoVariante variante;

    @Transient
    public String getDescripcionProducto() {
        return producto.getDescripcion() + (variante == null ? "" : " - " + variante.getNombreMostrar());
    }

    @Column(name = "cantidad", nullable = false)
    private Integer cantidad;

    @Column(name = "precio_unitario", precision = 10, scale = 2)
    private BigDecimal precioUnitario = BigDecimal.ZERO;

    @Column(name = "subtotal", precision = 10, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    // ==========================================
    // MÉTODOS
    // ==========================================

    public void calcularSubtotal() {
        if (precioUnitario != null && cantidad != null) {
            subtotal = precioUnitario.multiply(new BigDecimal(cantidad));
        } else {
            subtotal = BigDecimal.ZERO;
        }
    }
}

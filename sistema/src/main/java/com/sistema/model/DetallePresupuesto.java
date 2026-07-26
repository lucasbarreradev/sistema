package com.sistema.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Entity
@Getter @Setter
@ToString(exclude = {"presupuesto", "producto"})
@Table(name = "presupuesto_detalle")
public class DetallePresupuesto extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer cantidad;

    @ManyToOne
    @JoinColumn(name = "presupuesto_id", nullable = false)
    @JsonIgnore
    private Presupuesto presupuesto;

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

    @Column(name = "descuento_pct")
    private BigDecimal descuentoPct = BigDecimal.ZERO;

    // ✅ SUBTOTAL FINAL YA CALCULADO
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal precioUnitario; // CON IVA

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal alicuotaIva; // 21.00 / 10.50 / 0.00

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal subtotal; // CON IVA

    public DetallePresupuesto() {
    }

    @Transient
    public BigDecimal getNeto() {

        BigDecimal divisor = BigDecimal.ONE.add(
                alicuotaIva.divide(BigDecimal.valueOf(100))
        );

        return subtotal.divide(divisor, 2, RoundingMode.HALF_UP);
    }

    @Transient
    public BigDecimal getIva() {
        return subtotal.subtract(getNeto());
    }

    public void calcularSubtotal() {

        BigDecimal precio = this.precioUnitario;
        BigDecimal cantidadBD = BigDecimal.valueOf(this.cantidad);

        BigDecimal bruto = precio.multiply(cantidadBD);

        BigDecimal descuentoPct = this.descuentoPct != null
                ? this.descuentoPct
                : BigDecimal.ZERO;

        BigDecimal descuentoMonto = bruto
                .multiply(descuentoPct)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        this.subtotal = bruto.subtract(descuentoMonto);
    }


}


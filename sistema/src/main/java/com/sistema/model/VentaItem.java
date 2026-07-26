package com.sistema.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.math.RoundingMode;


@Entity
@Table(name = "venta_item")
@Getter
@Setter
@ToString(exclude = {"venta", "producto"})
public class VentaItem extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Relación con la venta padre
    @ManyToOne
    @JoinColumn(name = "venta_id", nullable = false)
    @JsonIgnore
    private Venta venta;

    // Producto vendido
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "producto_variante_id")
    private ProductoVariante variante;

    @Transient
    public String getDescripcionProducto() {
        return producto.getDescripcion() + (variante == null ? "" : " - " + variante.getNombreMostrar());
    }

    // Cantidad vendida
    @Column(name = "cantidad", nullable = false)
    private Integer cantidad;

    @Column(name = "costo_unitario")
    private BigDecimal costoUnitario;

    @Column(name = "descuento_pct")
    private BigDecimal descuentoPct = BigDecimal.ZERO;

    // ✅ SUBTOTAL FINAL YA CALCULADO
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal precioUnitario; // CON IVA

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal alicuotaIva; // 21.00 / 10.50 / 0.00

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal subtotal; // CON IVA


    // ==========================================
    // Constructores
    // ==========================================
    public VentaItem() {
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

        BigDecimal descuento = this.descuentoPct != null
                ? this.descuentoPct
                : BigDecimal.ZERO;

        BigDecimal descuentoMonto = bruto
                .multiply(descuento)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        this.subtotal = bruto.subtract(descuentoMonto);
    }



    @Transient
    public BigDecimal getMargen() {
        return precioUnitario
                .subtract(costoUnitario)
                .multiply(BigDecimal.valueOf(cantidad));
    }

    public BigDecimal getGanancia() {

        BigDecimal ingreso = subtotal;

        BigDecimal costo = (costoUnitario != null ? costoUnitario : BigDecimal.ZERO)
                .multiply(BigDecimal.valueOf(cantidad));

        return ingreso.subtract(costo);
    }





}

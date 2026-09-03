package com.sistema.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "producto_variante", uniqueConstraints = {
        @UniqueConstraint(name = "uk_producto_variante_sku", columnNames = {"tenant_id", "sku"}),
        @UniqueConstraint(name = "uk_producto_variante_barra", columnNames = {"tenant_id", "codigo_barras"}),
        @UniqueConstraint(name = "uk_producto_variante_ml", columnNames = {"tenant_id", "producto_id", "mercado_libre_variation_id"})
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@ToString(exclude = {"producto", "fotoContenido"})
public class ProductoVariante extends TenantAwareEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id", nullable = false)
    @JsonIgnore
    private Producto producto;

    @Column(nullable = false)
    private String sku;
    private String nombre;
    private String talle;
    private String color;
    private String codigoBarras;
    private Integer stock = 0;
    private BigDecimal precioCompra;
    private BigDecimal precioContado;
    private BigDecimal precioTarjeta;
    private BigDecimal precioCuentaCorriente;
    private String mercadoLibreVariationId;
    private String mercadoLibreItemId;
    private String mercadoLibreProductNumber;
    private String mercadoLibreGtin;
    @Lob
    @Column(columnDefinition = "TEXT")
    private String mercadoLibreAtributosJson;
    @Lob
    @Column(columnDefinition = "TEXT")
    private String wooCommerceAtributosJson;
    @Lob
    @Column(columnDefinition = "TEXT")
    private String tiendaNubeAtributosJson;
    private String wooCommerceVariationId;
    private String tiendaNubeVariationId;
    private String fotoNombre;
    private String fotoTipoContenido;
    @Column(length = 2000)
    private String fotoUrlExterna;
    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(columnDefinition = "MEDIUMBLOB")
    @JsonIgnore
    private byte[] fotoContenido;

    public boolean tieneFoto() {
        return tieneFotoLocal() || (fotoUrlExterna != null && !fotoUrlExterna.isBlank());
    }

    public boolean tieneFotoLocal() {
        return fotoContenido != null && fotoContenido.length > 0;
    }

    public boolean isTieneFoto() {
        return tieneFoto();
    }

    public String getNombreMostrar() {
        if (nombre != null && !nombre.isBlank() && !esNombreGenerico(nombre)) return nombre;
        String combinacion = String.join(" / ",
                talle == null ? "" : talle.trim(), color == null ? "" : color.trim()).replaceAll("(^ / | / $)", "");
        if (!combinacion.isBlank()) return combinacion;
        if (producto != null && producto.getDescripcion() != null
                && !producto.getDescripcion().isBlank()) {
            return producto.getDescripcion();
        }
        return sku;
    }

    private boolean esNombreGenerico(String valor) {
        return "Presentación única".equalsIgnoreCase(valor.trim())
                || "Presentacion unica".equalsIgnoreCase(valor.trim());
    }

    public BigDecimal precio(FormaPago formaPago) {
        BigDecimal propio = switch (formaPago) {
            case CONTADO -> precioContado;
            case TARJETA -> precioTarjeta;
            case CUENTA_CORRIENTE -> precioCuentaCorriente;
        };
        if (propio != null) {
            return propio;
        }
        if (formaPago != FormaPago.CONTADO && precioContado != null) {
            return precioContado;
        }
        return producto.getPrecioSegunFormaPago(formaPago);
    }
}

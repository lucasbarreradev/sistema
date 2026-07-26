package com.sistema.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "uk_producto_tenant_sku", columnNames = {"tenant_id", "sku"}))
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class Producto extends TenantAwareEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String sku;
    @ManyToOne
    @JoinColumn(name="proveedor_id")
    @JsonIgnore
    private Proveedor proveedor;
    private String descripcion;
    private Integer cantidad;
    private Boolean usaVariantes = false;
    private BigDecimal precioCompra;
    private BigDecimal precioContado;
    private BigDecimal precioTarjeta;
    private BigDecimal precioCuentaCorriente;
    @Enumerated(EnumType.STRING)
    private TipoIva tipoIva;
    private String mercadoLibreId;
    private String mercadoLibreFamilyId;
    private String mercadoLibreCategoriaId;
    @Column(length = 100)
    private String mercadoLibreGuiaTallesId;
    @Column(length = 100)
    private String mercadoLibreGuiaTallesFilaId;
    @Column(length = 100)
    private String mercadoLibreGenero;
    private Long mercadoLibreOfficialStoreId;
    @Column(length = 500)
    private String mercadoLibreMarca;
    @Column(length = 500)
    private String mercadoLibreModelo;
    @Column(length = 100)
    private String mercadoLibreTipoPrenda;
    @Column(length = 100)
    private String mercadoLibreGtin;
    @Column(length = 500)
    private String mercadoLibreGarantiaTipo;
    @Column(length = 500)
    private String mercadoLibreGarantiaTiempo;
    @Column(length = 500)
    private String mercadoLibreVideoId;
    private Boolean mercadoLibreEnvioGratis;
    private Boolean mercadoLibreRetiroPersonal;
    @Column(length = 50)
    private String mercadoLibreModoEnvio;
    @Column(length = 50)
    private String mercadoLibreCondicion;
    @Column(length = 50)
    private String mercadoLibreEstado;
    private Integer mercadoLibreTiempoDisponibilidad;
    @Column(length = 100)
    private String mercadoLibreListingTypeId;
    @Column(length = 500)
    private String mercadoLibreConfiguracionCuotas;
    @Column(length = 500)
    private String mercadoLibreCargoVenta;
    @Column(length = 500)
    private String mercadoLibreCostoFinanciacion;
    @Lob
    @Column(columnDefinition = "TEXT")
    private String mercadoLibreDescripcion;
    @Lob
    @Column(columnDefinition = "TEXT")
    private String mercadoLibreAtributosJson;
    @Lob
    @Column(columnDefinition = "TEXT")
    private String fotosUrlsExternas;
    private String fotoNombre;
    private String fotoTipoContenido;
    @Column(length = 2000)
    private String fotoUrlExterna;
    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(columnDefinition = "MEDIUMBLOB")
    @ToString.Exclude
    @JsonIgnore
    private byte[] fotoContenido;
    @OneToMany(mappedBy = "producto", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    @ToString.Exclude
    private List<ProductoVariante> variantes = new ArrayList<>();

    public boolean tieneVariantes() {
        return variantes != null && !variantes.isEmpty();
    }

    public int getStockTotal() {
        return tieneVariantes() ? variantes.stream().mapToInt(v -> Optional.ofNullable(v.getStock()).orElse(0)).sum()
                : Optional.ofNullable(cantidad).orElse(0);
    }

    public boolean tieneFoto() {
        return tieneFotoLocal() || (fotoUrlExterna != null && !fotoUrlExterna.isBlank());
    }

    public boolean tieneFotoLocal() {
        return fotoContenido != null && fotoContenido.length > 0;
    }

    public BigDecimal getPrecioSegunFormaPago(FormaPago formaPago) {

        if (formaPago == null) {
            throw new IllegalArgumentException("Forma de pago no puede ser null");
        }

        switch (formaPago) {
            case CONTADO:
                return this.precioContado;

            case TARJETA:
                return this.precioTarjeta;

            case CUENTA_CORRIENTE:
                return this.precioCuentaCorriente;

            default:
                throw new IllegalArgumentException("Forma de pago inválida");
        }
    }


}

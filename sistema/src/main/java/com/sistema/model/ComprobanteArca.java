package com.sistema.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "comprobante_arca", uniqueConstraints =
        @UniqueConstraint(name = "uk_comprobante_arca_numero",
                columnNames = {"tenant_id", "punto_venta", "tipo_comprobante", "numero_comprobante"}))
@Getter
@Setter
@NoArgsConstructor
public class ComprobanteArca extends TenantAwareEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "factura_origen_id", nullable = false)
    private Venta facturaOrigen;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "tipo_comprobante", nullable = false, length = 40)
    private TipoComprobante tipoComprobante;

    @Column(name = "punto_venta", nullable = false)
    private Integer puntoVenta;

    @Column(name = "numero_comprobante", nullable = false)
    private Long numeroComprobante;

    @Column(nullable = false, length = 20)
    private String cae;

    @Column(name = "fecha_vencimiento_cae", nullable = false)
    private LocalDate fechaVencimientoCae;

    @Column(name = "fecha_comprobante", nullable = false)
    private LocalDate fechaComprobante;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal totalNeto = BigDecimal.ZERO;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal totalIva = BigDecimal.ZERO;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal totalExento = BigDecimal.ZERO;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(nullable = false, length = 500)
    private String motivo;

    @Transient
    public String getNumeroFormateado() {
        return String.format("%05d-%08d", puntoVenta, numeroComprobante);
    }
}

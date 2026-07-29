package com.sistema.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "producto_catalogo_canal",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_catalogo_tenant_canal_externo",
                columnNames = {"tenant_id", "canal", "id_externo"}),
        indexes = @Index(
                name = "idx_catalogo_tenant_canal_descripcion",
                columnList = "tenant_id,canal,descripcion"))
@Getter
@Setter
@NoArgsConstructor
public class ProductoCatalogoCanal extends TenantAwareEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CanalVenta canal;

    @Column(name = "id_externo", nullable = false, length = 150)
    private String idExterno;

    @Column(length = 255)
    private String sku;

    @Column(length = 700)
    private String descripcion;

    private Integer stock;

    @Column(precision = 19, scale = 2)
    private BigDecimal precio;

    @Lob
    @Column(name = "foto_url", columnDefinition = "TEXT")
    private String fotoUrl;

    private Integer variantes;

    @Lob
    @Column(name = "producto_json", nullable = false, columnDefinition = "LONGTEXT")
    private String productoJson;

    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn;
}

package com.sistema.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_precio_tenant_producto_forma", columnNames = {"tenant_id", "producto_id", "formaPago"})
        }
)
@Getter @Setter @ToString @AllArgsConstructor @NoArgsConstructor
public class PrecioProducto extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id")
    private Producto producto;

    @Enumerated(EnumType.STRING)
    private FormaPago formaPago;

    @Column(nullable = false)
    private BigDecimal precio;
}


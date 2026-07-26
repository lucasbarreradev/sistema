package com.sistema.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @ToString
public class MovimientoInventario extends TenantAwareEntity {
    public enum Tipo {
        ENTRADA,
        SALIDA,
        AJUSTE,
        DEVOLUCION
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @ManyToOne
    @JoinColumn(name = "producto_variante_id")
    private ProductoVariante variante;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false)
    private Tipo tipo;

    @Column(name = "cantidad", nullable = false)
    private Integer cantidad;

    // Stock del producto ANTES de este movimiento
    // Útil para auditoría y reportes
    @Column(name = "stock_previo")
    private Integer stockPrevio;

    // Stock del producto DESPUÉS de este movimiento
    @Column(name = "stock_posterior")
    private Integer stockPosterior;

    @Column(name = "fecha_movimiento", nullable = false)
    private LocalDateTime fechaMovimiento;
}

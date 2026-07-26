package com.sistema.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(
        name = "uk_orden_canal_procesada",
        columnNames = {"tenant_id", "canal", "orden_id"}))
@Getter
@Setter
@NoArgsConstructor
public class OrdenCanalProcesada extends TenantAwareEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CanalVenta canal;

    @Column(name = "orden_id", nullable = false, length = 120)
    private String ordenId;

    @Column(nullable = false)
    private LocalDateTime procesadaEn;
}

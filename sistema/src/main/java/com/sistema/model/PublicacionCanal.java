package com.sistema.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "uk_publicacion_tenant_producto_canal",
        columnNames = {"tenant_id", "producto_id", "canal"}))
@Getter
@Setter
@NoArgsConstructor
public class PublicacionCanal extends TenantAwareEntity {
    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CanalVenta canal;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, columnDefinition = "varchar(30)")
    private EstadoPublicacion estado;
    private String idExterno;
    private LocalDateTime fechaActualizacion;
    @Column(length = 2000)
    private String ultimoError;

    @Transient
    public String getFechaActualizacionFormateada() {
        return fechaActualizacion == null ? "" : fechaActualizacion.format(FORMATO_FECHA);
    }
}

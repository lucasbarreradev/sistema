package com.sistema.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "tenant", uniqueConstraints = @UniqueConstraint(name = "uk_tenant_codigo", columnNames = "codigo"))
@Getter
@Setter
@NoArgsConstructor
public class Tenant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 80)
    private String codigo;
    @Column(nullable = false, length = 150)
    private String nombre;
    @Column(nullable = false)
    private Boolean activo = true;
    @Column(nullable = false)
    private LocalDateTime creadoEn = LocalDateTime.now();
}

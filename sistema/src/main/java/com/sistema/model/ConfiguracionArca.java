package com.sistema.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "configuracion_arca", uniqueConstraints =
        @UniqueConstraint(name = "uk_configuracion_arca_tenant", columnNames = "tenant_id"))
@Getter
@Setter
@NoArgsConstructor
public class ConfiguracionArca extends TenantAwareEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 11)
    private String cuit;

    @Column(name = "punto_venta", nullable = false)
    private Integer puntoVenta;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AmbienteArca ambiente;

    @Enumerated(EnumType.STRING)
    @Column(name = "condicion_fiscal", nullable = false, length = 30)
    private CondicionFiscalArca condicionFiscal;

    @Lob
    @Column(name = "certificado_cifrado", nullable = false, columnDefinition = "LONGTEXT")
    private String certificadoCifrado;

    @Lob
    @Column(name = "clave_privada_cifrada", nullable = false, columnDefinition = "LONGTEXT")
    private String clavePrivadaCifrada;

    @Column(name = "certificado_titular", length = 300)
    private String certificadoTitular;

    @Column(name = "certificado_vence_en")
    private Instant certificadoVenceEn;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;
}

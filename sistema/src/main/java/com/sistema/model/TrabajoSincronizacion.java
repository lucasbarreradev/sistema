package com.sistema.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.stream.Collectors;

@Entity
@Table(name = "trabajo_sincronizacion",
        indexes = @Index(name = "idx_trabajo_sinc_tenant_creado", columnList = "tenant_id,creado_en"))
@Getter
@Setter
@NoArgsConstructor
public class TrabajoSincronizacion extends TenantAwareEntity {
    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CanalVenta origen;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_trabajo", length = 40)
    private TipoTrabajoSincronizacion tipoTrabajo = TipoTrabajoSincronizacion.SINCRONIZACION_CANALES;

    @Column(nullable = false, length = 150)
    private String destinos;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private EstadoTrabajoSincronizacion estado;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;

    @Column(name = "iniciado_en")
    private LocalDateTime iniciadoEn;

    @Column(name = "finalizado_en")
    private LocalDateTime finalizadoEn;

    @Column(name = "cancelacion_solicitada", nullable = false)
    private boolean cancelacionSolicitada;

    @Column(length = 1000)
    private String resumen;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String detalle;

    @Version
    private Long version;

    @Transient
    public String getOrigenDescripcion() {
        return origen == null ? "" : origen.getDescripcion();
    }

    @Transient
    public String getFlujoDescripcion() {
        if (tipoTrabajo == TipoTrabajoSincronizacion.PUBLICACION_SELECCIONADA) {
            return "Sistema \u2192 " + getDestinosDescripcion();
        }
        if (tipoTrabajo == TipoTrabajoSincronizacion.SINCRONIZACION_SELECCIONADA) {
            return getOrigenDescripcion() + " \u2192 Sistema \u2192 " + getDestinosDescripcion();
        }
        if (tipoTrabajo == TipoTrabajoSincronizacion.IMPORTACION_COMPLETA
                || tipoTrabajo == TipoTrabajoSincronizacion.IMPORTACION_FILTRADA
                || tipoTrabajo == TipoTrabajoSincronizacion.PREPARACION_IMPORTACION
                || tipoTrabajo == TipoTrabajoSincronizacion.IMPORTACION_SELECCIONADA) {
            return getOrigenDescripcion() + " \u2192 Sistema";
        }
        return getOrigenDescripcion() + " \u2192 " + getDestinosDescripcion();
    }

    @Transient
    public String getDestinosDescripcion() {
        if (destinos == null || destinos.isBlank()) return "";
        return Arrays.stream(destinos.split(","))
                .map(String::trim)
                .filter(valor -> !valor.isBlank())
                .map(valor -> {
                    try {
                        return CanalVenta.valueOf(valor).getDescripcion();
                    } catch (IllegalArgumentException e) {
                        return valor;
                    }
                })
                .collect(Collectors.joining(", "));
    }

    @Transient
    public String getCreadoEnFormateado() {
        return formatear(creadoEn);
    }

    @Transient
    public String getIniciadoEnFormateado() {
        return formatear(iniciadoEn);
    }

    @Transient
    public String getFinalizadoEnFormateado() {
        return formatear(finalizadoEn);
    }

    @Transient
    public boolean isActivo() {
        return estado != null && estado.estaActivo();
    }

    private String formatear(LocalDateTime fecha) {
        return fecha == null ? "" : fecha.format(FORMATO_FECHA);
    }
}

package com.sistema.dto;

import com.sistema.model.CanalVenta;
import com.sistema.model.EstadoPublicacion;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class PublicacionCanalListadoDto {
    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final Long id;
    private final String productoDescripcion;
    private final CanalVenta canal;
    private final EstadoPublicacion estado;
    private final String idExterno;
    private final LocalDateTime fechaActualizacion;
    private final String ultimoError;

    public PublicacionCanalListadoDto(Long id, String productoDescripcion, CanalVenta canal,
                                     EstadoPublicacion estado, String idExterno,
                                     LocalDateTime fechaActualizacion, String ultimoError) {
        this.id = id;
        this.productoDescripcion = productoDescripcion;
        this.canal = canal;
        this.estado = estado;
        this.idExterno = idExterno;
        this.fechaActualizacion = fechaActualizacion;
        this.ultimoError = ultimoError;
    }

    public Long getId() { return id; }
    public String getProductoDescripcion() { return productoDescripcion; }
    public CanalVenta getCanal() { return canal; }
    public EstadoPublicacion getEstado() { return estado; }
    public String getIdExterno() { return idExterno; }
    public LocalDateTime getFechaActualizacion() { return fechaActualizacion; }
    public String getUltimoError() { return ultimoError; }
    public String getFechaActualizacionFormateada() {
        return fechaActualizacion == null ? "" : fechaActualizacion.format(FORMATO_FECHA);
    }
}

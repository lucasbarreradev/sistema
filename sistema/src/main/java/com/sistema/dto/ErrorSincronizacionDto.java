package com.sistema.dto;

import java.util.List;

public class ErrorSincronizacionDto {
    private final String referencia;
    private final String canal;
    private final String mensaje;
    private final List<String> correcciones;
    private final Long productoId;

    public ErrorSincronizacionDto(String referencia, String canal, String mensaje,
                                  List<String> correcciones, Long productoId) {
        this.referencia = referencia;
        this.canal = canal;
        this.mensaje = mensaje;
        this.correcciones = correcciones;
        this.productoId = productoId;
    }

    public String getReferencia() { return referencia; }
    public String getCanal() { return canal; }
    public String getMensaje() { return mensaje; }
    public List<String> getCorrecciones() { return correcciones; }
    public Long getProductoId() { return productoId; }
}

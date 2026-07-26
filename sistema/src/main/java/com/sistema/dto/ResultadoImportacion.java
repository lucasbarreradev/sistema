package com.sistema.dto;

import java.util.ArrayList;
import java.util.List;

public class ResultadoImportacion {
    private int creados;
    private int actualizados;
    private final List<String> errores = new ArrayList<>();

    public int getCreados() { return creados; }
    public int getActualizados() { return actualizados; }
    public List<String> getErrores() { return errores; }
    public void creado() { creados++; }
    public void actualizado() { actualizados++; }
    public void error(String error) { errores.add(error); }

    public String resumen() {
        return creados + " productos creados y " + actualizados + " actualizados";
    }
}

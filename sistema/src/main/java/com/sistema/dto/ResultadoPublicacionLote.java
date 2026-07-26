package com.sistema.dto;

import java.util.ArrayList;
import java.util.List;

public class ResultadoPublicacionLote {
    private int exitosas;
    private final List<String> errores = new ArrayList<>();
    public int getExitosas() { return exitosas; }
    public List<String> getErrores() { return errores; }
    public void exito() { exitosas++; }
    public void error(String mensaje) { errores.add(mensaje); }
}

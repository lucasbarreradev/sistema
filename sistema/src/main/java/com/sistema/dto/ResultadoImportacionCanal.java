package com.sistema.dto;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ResultadoImportacionCanal {
    private int creados;
    private int actualizados;
    private final Set<Long> productoIds = new LinkedHashSet<>();
    private final List<String> errores = new ArrayList<>();
    public int getCreados() { return creados; }
    public int getActualizados() { return actualizados; }
    public List<Long> getProductoIds() { return new ArrayList<>(productoIds); }
    public List<String> getErrores() { return errores; }
    public void creado(Long id) { creados++; productoIds.add(id); }
    public void actualizado(Long id) { actualizados++; productoIds.add(id); }
    public void error(String error) { errores.add(error); }
    public String resumen() { return creados + " productos creados y " + actualizados + " actualizados"; }
}

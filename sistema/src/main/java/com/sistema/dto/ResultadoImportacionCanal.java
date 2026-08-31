package com.sistema.dto;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ResultadoImportacionCanal {
    private int recibidos;
    private int creados;
    private int actualizados;
    private final Set<Long> productoIds = new LinkedHashSet<>();
    private final List<String> errores = new ArrayList<>();
    public int getRecibidos() { return recibidos; }
    public int getCreados() { return creados; }
    public int getActualizados() { return actualizados; }
    public List<Long> getProductoIds() { return new ArrayList<>(productoIds); }
    public List<String> getErrores() { return errores; }
    public void recibidos(int cantidad) { recibidos = Math.max(cantidad, 0); }
    public void creado(Long id) { creados++; productoIds.add(id); }
    public void actualizado(Long id) { actualizados++; productoIds.add(id); }
    public void error(String error) { errores.add(error); }
    public String resumen() {
        int procesados = creados + actualizados;
        int total = recibidos > 0 ? recibidos : procesados + errores.size();
        return total + " publicaciones recibidas: " + creados + " productos creados y "
                + actualizados + " actualizados; " + productoIds.size()
                + " productos locales únicos";
    }
}

package com.sistema.service;

import com.sistema.dto.ResultadoImportacionCanal;
import com.sistema.dto.ResultadoPublicacionLote;
import com.sistema.model.CanalVenta;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.function.BooleanSupplier;

@Service
public class SincronizacionCanalesService {
    private final ImportacionCanalService importacionCanalService;
    private final PublicacionService publicacionService;

    public SincronizacionCanalesService(ImportacionCanalService importacionCanalService, PublicacionService publicacionService) {
        this.importacionCanalService = importacionCanalService;
        this.publicacionService = publicacionService;
    }

    public Resultado sincronizar(CanalVenta origen, Collection<CanalVenta> destinos) {
        return sincronizar(origen, destinos, () -> false);
    }

    public Resultado sincronizar(CanalVenta origen, Collection<CanalVenta> destinos,
                                  BooleanSupplier cancelacionSolicitada) {
        List<CanalVenta> destinosValidos = destinos.stream().filter(c -> c != origen).distinct().toList();
        if (destinosValidos.isEmpty()) throw new IllegalArgumentException("Seleccione al menos un destino diferente del origen");
        List<com.sistema.dto.ProductoCanalImportado> productos = importacionCanalService
                .obtenerProductos(origen, false, cancelacionSolicitada);
        ResultadoImportacionCanal importacion = cancelacionSolicitada.getAsBoolean()
                ? new ResultadoImportacionCanal()
                : importacionCanalService.importar(origen, productos, cancelacionSolicitada);
        ResultadoPublicacionLote publicacion = cancelacionSolicitada.getAsBoolean()
                ? new ResultadoPublicacionLote()
                : publicacionService.publicar(importacion.getProductoIds(), destinosValidos, cancelacionSolicitada);
        return new Resultado(importacion, publicacion);
    }

    public record Resultado(ResultadoImportacionCanal importacion, ResultadoPublicacionLote publicacion) {}
}

package com.sistema.dto;

import java.math.BigDecimal;
import java.util.Map;
import java.util.List;

public record ProductoCanalImportado(
        String idExterno,
        String sku,
        String descripcion,
        Integer cantidad,
        BigDecimal precio,
        String fotoUrl,
        String mercadoLibreCategoriaId,
        Map<String, Object> datosCanal,
        List<VarianteCanalImportada> variantes) {
}

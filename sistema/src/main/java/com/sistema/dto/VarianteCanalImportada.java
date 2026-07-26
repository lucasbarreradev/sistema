package com.sistema.dto;

import java.math.BigDecimal;
import java.util.Map;

public record VarianteCanalImportada(String idExterno, String sku, String nombre, String talle, String color,
                                     Integer stock, BigDecimal precio, String codigoBarras, String productNumber,
                                     String gtin, Map<String, String> atributos, String fotoUrl,
                                     boolean itemMercadoLibre) {
}

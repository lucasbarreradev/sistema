package com.sistema.service.canal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sistema.model.CanalVenta;
import com.sistema.model.ProductoVariante;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

final class AtributosVarianteHelper {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AtributosVarianteHelper() {}

    static Map<String, String> obtener(ProductoVariante variante) {
        return obtener(variante, CanalVenta.MERCADO_LIBRE);
    }

    static Map<String, String> obtener(
            ProductoVariante variante, CanalVenta canal) {
        Map<String, String> atributos = new LinkedHashMap<>();
        if (variante.getTalle() != null && !variante.getTalle().isBlank()) atributos.put("SIZE", variante.getTalle());
        if (variante.getColor() != null && !variante.getColor().isBlank()) atributos.put("COLOR", variante.getColor());
        agregarJson(atributos, variante.getMercadoLibreAtributosJson(), variante);
        if (canal != CanalVenta.MERCADO_LIBRE) {
            String jsonCanal = canal == CanalVenta.WOOCOMMERCE
                    ? variante.getWooCommerceAtributosJson()
                    : variante.getTiendaNubeAtributosJson();
            agregarJson(atributos, jsonCanal, variante);
        }
        return atributos;
    }

    private static void agregarJson(Map<String, String> atributos, String json,
                                    ProductoVariante variante) {
        if (json == null || json.isBlank()) return;
        try {
            atributos.putAll(MAPPER.readValue(
                    json, new TypeReference<LinkedHashMap<String, String>>() {}));
        } catch (Exception e) {
            throw new IllegalArgumentException("Los atributos de la variante "
                    + variante.getSku() + " no son válidos", e);
        }
    }

    static String nombre(String id) {
        return switch (id) {
            case "SIZE" -> "Talle";
            case "COLOR" -> "Color";
            case "MAIN_COLOR" -> "Color principal";
            case "FILTRABLE_SIZE" -> "Equivalencias";
            case "BRAND" -> "Marca";
            case "MODEL" -> "Modelo";
            case "MATERIAL" -> "Material";
            case "FABRIC_DESIGN" -> "Diseño de tela";
            case "FUNCTIONS" -> "Funciones";
            case "TIRE_WIDTH", "SECTION_WIDTH" -> "Ancho";
            case "ASPECT_RATIO" -> "Perfil";
            case "RIM_DIAMETER" -> "Rodado";
            case "DIAMETER" -> "Diámetro";
            case "INTERNAL_MEMORY", "STORAGE_CAPACITY" -> "Capacidad";
            case "RAM" -> "Memoria RAM";
            case "ALPHANUMERIC_MODELS" -> "Modelos alfanuméricos";
            case "DISPLAY_SIZE" -> "Tamaño de pantalla";
            case "PROCESSOR_MODEL" -> "Modelo del procesador";
            case "REAR_CAMERAS_RESOLUTION" -> "Resolución de las cámaras traseras";
            case "REAR_CAMERA_APERTURE" -> "Apertura de la cámara trasera";
            case "WITH_HAND_RECOGNITION" -> "Con reconocimiento de manos";
            default -> {
                String texto = id.replace('_', ' ').toLowerCase(Locale.ROOT);
                yield texto.isBlank() ? id : Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
            }
        };
    }
}

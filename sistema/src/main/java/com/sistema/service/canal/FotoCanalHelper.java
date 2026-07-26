package com.sistema.service.canal;

import com.sistema.model.Producto;
import com.sistema.model.ProductoVariante;

final class FotoCanalHelper {
    private FotoCanalHelper() {}

    static String resolverUrl(Producto producto, String publicBaseUrl) {
        if (producto == null || !producto.tieneFoto()) return null;
        if (producto.tieneFotoLocal() && publicBaseUrl != null && !publicBaseUrl.isBlank()) {
            return limpiarUrl(publicBaseUrl) + "/productos/" + producto.getId() + "/foto/producto-"
                    + producto.getId() + "." + extension(producto.getFotoTipoContenido());
        }
        return producto.getFotoUrlExterna();
    }

    static String resolverUrl(ProductoVariante variante, String publicBaseUrl) {
        if (variante == null) return null;
        if (variante.tieneFotoLocal() && publicBaseUrl != null && !publicBaseUrl.isBlank()) {
            return limpiarUrl(publicBaseUrl) + "/productos/" + variante.getProducto().getId()
                    + "/variantes/" + variante.getId() + "/foto/variante-" + variante.getId()
                    + "." + extension(variante.getFotoTipoContenido());
        }
        if (variante.getFotoUrlExterna() != null && !variante.getFotoUrlExterna().isBlank()) {
            return variante.getFotoUrlExterna().trim();
        }
        if (variante.getProducto().tieneFotoLocal() && publicBaseUrl != null && !publicBaseUrl.isBlank()) {
            return limpiarUrl(publicBaseUrl) + "/productos/" + variante.getProducto().getId()
                    + "/variantes/" + variante.getId() + "/foto/variante-" + variante.getId()
                    + "." + extension(variante.getProducto().getFotoTipoContenido());
        }
        return null;
    }

    private static String extension(String contentType) {
        if (contentType == null) return "jpg";
        return switch (contentType.toLowerCase()) {
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            case "image/gif" -> "gif";
            default -> "jpg";
        };
    }

    private static String limpiarUrl(String value) {
        return value.replaceAll("/+$", "");
    }
}

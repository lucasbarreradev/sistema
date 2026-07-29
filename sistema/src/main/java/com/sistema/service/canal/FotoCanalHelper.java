package com.sistema.service.canal;

import com.sistema.model.Producto;
import com.sistema.model.ProductoVariante;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class FotoCanalHelper {
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
        return resolverUrl(variante.getProducto(), publicBaseUrl);
    }

    static String resolverUrlWooCommerce(Producto producto, String publicBaseUrl) {
        if (producto == null || !producto.tieneFoto()) return null;
        if (publicBaseUrl == null || publicBaseUrl.isBlank()) {
            return resolverUrl(producto, publicBaseUrl);
        }
        return limpiarUrl(publicBaseUrl) + "/productos/" + producto.getId()
                + "/foto/woocommerce.jpg";
    }

    static String resolverUrlWooCommerce(ProductoVariante variante, String publicBaseUrl) {
        if (variante == null) return null;
        if (!variante.tieneFoto()) {
            return resolverUrlWooCommerce(variante.getProducto(), publicBaseUrl);
        }
        if (publicBaseUrl == null || publicBaseUrl.isBlank()) {
            return resolverUrl(variante, publicBaseUrl);
        }
        return limpiarUrl(publicBaseUrl) + "/productos/" + variante.getProducto().getId()
                + "/variantes/" + variante.getId() + "/foto/woocommerce.jpg";
    }

    static List<String> resolverUrlsAdicionalesWooCommerce(
            Producto producto, String publicBaseUrl) {
        List<String> originales = urlsAdicionales(producto);
        if (originales.isEmpty()) return List.of();
        if (publicBaseUrl == null || publicBaseUrl.isBlank()) return originales;
        List<String> urls = new ArrayList<>();
        for (int indice = 0; indice < originales.size(); indice++) {
            urls.add(limpiarUrl(publicBaseUrl) + "/productos/" + producto.getId()
                    + "/fotos/" + indice + "/woocommerce.jpg");
        }
        return urls;
    }

    public static List<String> urlsAdicionales(Producto producto) {
        if (producto == null || producto.getFotosUrlsExternas() == null
                || producto.getFotosUrlsExternas().isBlank()) {
            return List.of();
        }
        Set<String> urls = new LinkedHashSet<>();
        producto.getFotosUrlsExternas().lines().forEach(valor -> {
            String url = valor == null ? "" : valor.trim();
            if (url.isBlank()) return;
            try {
                URI uri = URI.create(url);
                if ("http".equalsIgnoreCase(uri.getScheme())
                        || "https".equalsIgnoreCase(uri.getScheme())) {
                    urls.add(uri.toString());
                }
            } catch (IllegalArgumentException ignored) {
                // Las entradas inválidas no se envían a los canales.
            }
        });
        return List.copyOf(urls);
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

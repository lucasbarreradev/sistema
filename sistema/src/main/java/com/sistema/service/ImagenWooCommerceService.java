package com.sistema.service;

import org.springframework.stereotype.Service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Iterator;

@Service
public class ImagenWooCommerceService {
    static final int TAMANO_LIENZO = 1000;
    static final int TAMANO_MAXIMO_CONTENIDO = 700;
    private static final int MAXIMO_BYTES = 10 * 1024 * 1024;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15))
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    public byte[] normalizarDesdeUrl(String url) {
        URI uri = validarUrl(url);
        URI uriCompatible = convertirWebpMercadoLibreAJpeg(uri);
        HttpRequest request = HttpRequest.newBuilder(uriCompatible)
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "SistemaStock/1.0")
                .GET()
                .build();
        try {
            HttpResponse<byte[]> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalArgumentException(
                        "No se pudo descargar la foto: HTTP " + response.statusCode());
            }
            if (response.body() == null || response.body().length == 0
                    || response.body().length > MAXIMO_BYTES) {
                throw new IllegalArgumentException("La foto remota está vacía o supera 10 MB");
            }
            return normalizar(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Se interrumpió la preparación de la foto", e);
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException) throw (IllegalArgumentException) e;
            throw new IllegalArgumentException("No se pudo preparar la foto para WooCommerce", e);
        }
    }

    public byte[] normalizar(byte[] contenido) {
        if (contenido == null || contenido.length == 0 || contenido.length > MAXIMO_BYTES) {
            throw new IllegalArgumentException("La foto está vacía o supera 10 MB");
        }
        try {
            BufferedImage original = ImageIO.read(new ByteArrayInputStream(contenido));
            if (original == null || original.getWidth() <= 0 || original.getHeight() <= 0) {
                throw new IllegalArgumentException("El formato de la foto no se puede normalizar");
            }

            double escala = Math.min(
                    (double) TAMANO_MAXIMO_CONTENIDO / original.getWidth(),
                    (double) TAMANO_MAXIMO_CONTENIDO / original.getHeight());
            int ancho = Math.max(1, (int) Math.round(original.getWidth() * escala));
            int alto = Math.max(1, (int) Math.round(original.getHeight() * escala));
            int x = (TAMANO_LIENZO - ancho) / 2;
            int y = (TAMANO_LIENZO - alto) / 2;

            BufferedImage resultado =
                    new BufferedImage(TAMANO_LIENZO, TAMANO_LIENZO, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = resultado.createGraphics();
            try {
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, TAMANO_LIENZO, TAMANO_LIENZO);
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                graphics.setRenderingHint(RenderingHints.KEY_RENDERING,
                        RenderingHints.VALUE_RENDER_QUALITY);
                graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                graphics.drawImage(original, x, y, ancho, alto, null);
            } finally {
                graphics.dispose();
            }
            return escribirJpeg(resultado);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("No se pudo normalizar la foto", e);
        }
    }

    private byte[] escribirJpeg(BufferedImage imagen) throws Exception {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) throw new IllegalStateException("No hay un codificador JPEG disponible");
        ImageWriter writer = writers.next();
        try (ByteArrayOutputStream salida = new ByteArrayOutputStream();
             ImageOutputStream imagenSalida = ImageIO.createImageOutputStream(salida)) {
            writer.setOutput(imagenSalida);
            ImageWriteParam parametros = writer.getDefaultWriteParam();
            if (parametros.canWriteCompressed()) {
                parametros.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                parametros.setCompressionQuality(0.92f);
            }
            writer.write(null, new IIOImage(imagen, null, null), parametros);
            imagenSalida.flush();
            return salida.toByteArray();
        } finally {
            writer.dispose();
        }
    }

    private URI validarUrl(String url) {
        try {
            URI uri = URI.create(url == null ? "" : url.trim());
            if (!("https".equalsIgnoreCase(uri.getScheme())
                    || "http".equalsIgnoreCase(uri.getScheme()))) {
                throw new IllegalArgumentException("La URL de la foto no es HTTP o HTTPS");
            }
            return uri;
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException) throw (IllegalArgumentException) e;
            throw new IllegalArgumentException("La URL de la foto no es válida", e);
        }
    }

    private URI convertirWebpMercadoLibreAJpeg(URI uri) {
        String host = uri.getHost();
        String texto = uri.toString();
        if (host != null && host.toLowerCase().endsWith("mlstatic.com")
                && texto.toLowerCase().endsWith(".webp")) {
            return URI.create(texto.substring(0, texto.length() - 5) + ".jpg");
        }
        return uri;
    }
}

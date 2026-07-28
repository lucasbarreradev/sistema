package com.sistema.service;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImagenWooCommerceServiceTest {

    @Test
    void centraLaFotoCompletaEnUnLienzoCuadradoConMargenBlanco() throws Exception {
        BufferedImage original = new BufferedImage(500, 276, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = original.createGraphics();
        graphics.setColor(Color.BLACK);
        graphics.fillRect(0, 0, original.getWidth(), original.getHeight());
        graphics.dispose();
        ByteArrayOutputStream entrada = new ByteArrayOutputStream();
        ImageIO.write(original, "png", entrada);

        byte[] resultado = new ImagenWooCommerceService().normalizar(entrada.toByteArray());
        BufferedImage normalizada = ImageIO.read(new ByteArrayInputStream(resultado));

        assertNotNull(normalizada);
        assertEquals(1000, normalizada.getWidth());
        assertEquals(1000, normalizada.getHeight());
        Color margen = new Color(normalizada.getRGB(50, 500));
        Color centro = new Color(normalizada.getRGB(500, 500));
        assertTrue(margen.getRed() > 245 && margen.getGreen() > 245 && margen.getBlue() > 245);
        assertTrue(centro.getRed() < 10 && centro.getGreen() < 10 && centro.getBlue() < 10);
    }
}

package com.sistema.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sistema.model.CanalVenta;
import org.springframework.web.client.RestClientResponseException;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Convierte respuestas técnicas de los canales en textos aptos para mostrar al usuario. */
public final class MensajeErrorIntegracion {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int LARGO_MAXIMO = 1900;
    private static final Pattern ATRIBUTO_ENTRE_COMILLAS =
            Pattern.compile("[\\\"“]([^\\\"”]+)[\\\"”]");

    private MensajeErrorIntegracion() {
    }

    public static String paraUsuario(CanalVenta canal, Throwable error) {
        if (error == null) return "Ocurrió un error inesperado al comunicarse con el canal.";
        return paraUsuario(canal, detalleTecnico(error));
    }

    public static String paraUsuario(CanalVenta canal, String detalle) {
        String texto = limpiar(detalle);
        if (texto == null) return "Ocurrió un error inesperado al comunicarse con el canal.";
        String normalizado = normalizar(texto);
        String nombreCanal = canal == null ? "El canal de venta" : canal.getDescripcion();

        if (normalizado.contains("address_pending")) {
            return "Mercado Libre no permite publicar porque la dirección de la cuenta vendedora "
                    + "está pendiente de completar o validar. Ingresá a Mercado Libre, completá "
                    + "la dirección en los datos de la cuenta y volvé a intentar.";
        }
        if (normalizado.contains("seller.unable_to_list")
                || normalizado.contains("user is unable to list")) {
            return "Mercado Libre no permite publicar con esta cuenta en este momento. Revisá "
                    + "las validaciones pendientes de la cuenta vendedora y volvé a intentar.";
        }
        if (normalizado.contains("item.user_product.repeated.conflict")
                || normalizado.contains("repeated user-product")) {
            return "Mercado Libre detectó que este producto ya existe en la cuenta. Revisá la "
                    + "publicación duplicada indicada por Mercado Libre y volvé a sincronizar.";
        }
        if (normalizado.contains("product_invalid_sku")
                || normalizado.contains("sku no valido o duplicado")) {
            return "El SKU ya está siendo usado por otro producto o variante en WooCommerce. "
                    + "Asigná un SKU único y volvé a intentar.";
        }
        if (normalizado.contains("status:under_review")
                || normalizado.contains("item.price.not_modifiable")
                || normalizado.contains("field_not_updatable")
                || normalizado.contains("not modifiable")) {
            return "La publicación está siendo revisada por Mercado Libre y por ahora no permite "
                    + "modificar precio, stock ni atributos. Volvé a intentar cuando termine la revisión.";
        }
        if (normalizado.contains("invalid_product_identifier")
                || (normalizado.contains("gtin") && normalizado.contains("already"))) {
            return "El código universal (GTIN) no es válido para este producto o ya está asociado "
                    + "a otro producto. Revisá el GTIN o indicá el motivo por el que no posee uno.";
        }
        if (normalizado.contains("category not found")) {
            return "La categoría guardada ya no existe en Mercado Libre. Elegí o detectá nuevamente "
                    + "la categoría del producto.";
        }
        if (normalizado.contains("kvsapi_error")
                || normalizado.contains("kvsclient")
                || normalizado.contains("conflict.error")) {
            return "Mercado Libre tuvo un conflicto temporal al procesar el producto. Esperá unos "
                    + "minutos y volvé a intentar.";
        }

        JsonNode cuerpo = extraerJson(texto);
        if (cuerpo != null) {
            String desdeJson = interpretarJson(canal, cuerpo, normalizado);
            if (desdeJson != null) return limitar(desdeJson);
            return limitar(nombreCanal + " rechazó la operación. Revisá la configuración del "
                    + "producto o de la cuenta y volvé a intentar.");
        }

        if (normalizado.contains("429 too many requests") || normalizado.contains("too_many_requests")) {
            return nombreCanal + " recibió demasiadas solicitudes. Esperá unos minutos y volvé a intentar.";
        }
        if (normalizado.contains("401 unauthorized") || normalizado.contains("invalid_token")) {
            return "La conexión con " + nombreCanal + " venció o dejó de ser válida. Volvé a conectar la cuenta.";
        }
        if (normalizado.contains("403 forbidden")) {
            return nombreCanal + " rechazó la operación por falta de permisos. Revisá la cuenta conectada y sus permisos.";
        }
        if (normalizado.contains("500 internal server error")
                || normalizado.contains("502 bad gateway")
                || normalizado.contains("503 service unavailable")) {
            return nombreCanal + " tuvo un error temporal. Esperá unos minutos y volvé a intentar.";
        }
        if (texto.indexOf('{') >= 0 || texto.indexOf('}') >= 0) {
            return nombreCanal + " devolvió un error técnico al procesar la operación. "
                    + "Revisá los datos y volvé a intentar; el detalle completo quedó registrado en la consola.";
        }
        return limitar(texto);
    }

    public static String detalleTecnico(Throwable error) {
        Throwable actual = error;
        String mensaje = null;
        while (actual != null) {
            if (actual instanceof RestClientResponseException respuesta) {
                String cuerpo = limpiar(respuesta.getResponseBodyAsString());
                if (cuerpo != null) {
                    return respuesta.getStatusCode().value() + " "
                            + respuesta.getStatusText() + ": " + cuerpo;
                }
            }
            if (limpiar(actual.getMessage()) != null) mensaje = actual.getMessage();
            if (actual.getCause() == null || actual.getCause() == actual) break;
            actual = actual.getCause();
        }
        return mensaje == null ? error.getClass().getSimpleName() : mensaje;
    }

    private static String interpretarJson(CanalVenta canal, JsonNode cuerpo, String normalizado) {
        int estado = cuerpo.path("status").asInt(0);
        String error = cuerpo.path("error").asText("");
        String mensaje = cuerpo.path("message").asText("");
        JsonNode causas = cuerpo.path("cause");

        if (estado == 429) return nombre(canal) + " recibió demasiadas solicitudes. Esperá unos minutos y volvé a intentar.";
        if (estado == 401) return "La conexión con " + nombre(canal) + " venció o dejó de ser válida. Volvé a conectar la cuenta.";
        if (estado >= 500) return nombre(canal) + " tuvo un error temporal. Esperá unos minutos y volvé a intentar.";

        Set<String> atributos = new LinkedHashSet<>();
        List<String> errores = new ArrayList<>();
        if (causas.isArray()) {
            for (JsonNode causa : causas) {
                if (causa.isTextual()) continue;
                String codigo = causa.path("code").asText("");
                String textoCausa = causa.path("message").asText("");
                String tipo = causa.path("type").asText("");
                String combinado = normalizar(codigo + " " + textoCausa);
                if (combinado.contains("required") || combinado.contains("obligatori")
                        || combinado.contains("missing")) {
                    String atributo = extraerAtributo(textoCausa, codigo);
                    if (atributo != null) atributos.add(atributo);
                } else if ("error".equalsIgnoreCase(tipo) && !textoCausa.isBlank()) {
                    errores.add(traducirCausa(codigo, textoCausa));
                }
            }
        }
        if (!atributos.isEmpty()) {
            return "Falta completar " + (atributos.size() == 1 ? "el atributo obligatorio: "
                    : "los atributos obligatorios: ") + String.join(", ", atributos) + ".";
        }
        if (!errores.isEmpty()) return String.join(" ", errores.stream().distinct().toList());
        if (estado == 403) return nombre(canal) + " rechazó la operación por una validación pendiente o falta de permisos en la cuenta.";
        if (estado == 409) return nombre(canal) + " tuvo un conflicto temporal al procesar el producto. Volvé a intentar en unos minutos.";
        if (normalizado.contains("validation_error") || "validation_error".equals(error)) {
            return nombre(canal) + " rechazó el producto porque hay datos que no cumplen los requisitos de la categoría. Revisá los campos obligatorios.";
        }
        if (!mensaje.isBlank() && esMensajeComprensible(mensaje)) return mensaje;
        return null;
    }

    private static String traducirCausa(String codigo, String mensaje) {
        String normalizado = normalizar(codigo + " " + mensaje);
        if (normalizado.contains("number_invalid_format")) {
            return "Uno de los atributos numéricos tiene un formato o una unidad no válida.";
        }
        if (normalizado.contains("mandatory_free_shipping")) {
            return "Mercado Libre exige envío gratis para este producto.";
        }
        if (normalizado.contains("shipping") && normalizado.contains("mode")) {
            return "La modalidad de envío elegida no está disponible para esta cuenta o producto.";
        }
        if (esMensajeComprensible(mensaje)) return mensaje.endsWith(".") ? mensaje : mensaje + ".";
        return "Mercado Libre rechazó uno de los datos enviados para el producto.";
    }

    private static String extraerAtributo(String mensaje, String codigo) {
        Matcher matcher = ATRIBUTO_ENTRE_COMILLAS.matcher(mensaje == null ? "" : mensaje);
        if (matcher.find()) return matcher.group(1).trim();
        String mayuscula = ((codigo == null ? "" : codigo) + " "
                + (mensaje == null ? "" : mensaje)).toUpperCase(Locale.ROOT);
        if (mayuscula.contains("BRAND")) return "Marca";
        if (mayuscula.contains("MODEL")) return "Modelo";
        if (mayuscula.contains("GTIN")) return "GTIN";
        return null;
    }

    private static JsonNode extraerJson(String texto) {
        int inicio = texto.indexOf('{');
        int fin = texto.lastIndexOf('}');
        if (inicio < 0 || fin <= inicio) return null;
        try {
            return JSON.readTree(texto.substring(inicio, fin + 1));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean esMensajeComprensible(String mensaje) {
        if (mensaje == null || mensaje.isBlank()) return false;
        String normalizado = normalizar(mensaje);
        return !normalizado.matches("[a-z0-9_.:-]+")
                && !normalizado.contains("exception")
                && !normalizado.contains("kvsapi");
    }

    private static String nombre(CanalVenta canal) {
        return canal == null ? "El canal de venta" : canal.getDescripcion();
    }

    private static String limpiar(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }

    private static String limitar(String texto) {
        return texto.length() <= LARGO_MAXIMO ? texto : texto.substring(0, LARGO_MAXIMO);
    }

    private static String normalizar(String texto) {
        String sinTildes = Normalizer.normalize(texto == null ? "" : texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return sinTildes.toLowerCase(Locale.ROOT);
    }
}

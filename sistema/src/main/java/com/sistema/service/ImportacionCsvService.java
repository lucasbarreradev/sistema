package com.sistema.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sistema.dto.ResultadoImportacion;
import com.sistema.model.Producto;
import com.sistema.model.ProductoVariante;
import com.sistema.model.TipoIva;
import com.sistema.repository.ProductoRepository;
import com.sistema.repository.ProductoVarianteRepository;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;

@Service
public class ImportacionCsvService {
    private static final String ATRIBUTO_VARIANTE = "__atributo_variante__";
    private static final String FAMILY_ID = "__family_id__";
    private static final String ITEM_ID_VARIANTE = "__item_id_variante__";
    private static final Set<String> COLUMNAS_TITULO = Set.of("titulo", "title", "descripcion", "nombre");
    private static final Set<String> COLUMNAS_ID = Set.of("id", "item id", "numero de publicacion", "id de publicacion");
    private static final Set<String> COLUMNAS_DE_VARIANTE = Set.of("variation id", "numero de variante", "product number",
            "numero de producto", "sku", "seller sku", "codigo sku", "quantity", "stock", "stock en tu deposito",
            "variations", "variantes", "size variation-column", "talle", "size", "color variation-column", "color",
            "gtin", "codigo universal de producto");

    private final ProductoRepository productoRepository;
    private final ProductoService productoService;
    private final ProductoVarianteRepository varianteRepository;
    private final ProductoVarianteService varianteService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ImportacionCsvService(ProductoRepository productoRepository, ProductoService productoService,
                                 ProductoVarianteRepository varianteRepository, ProductoVarianteService varianteService) {
        this.productoRepository = productoRepository;
        this.productoService = productoService;
        this.varianteRepository = varianteRepository;
        this.varianteService = varianteService;
    }

    public synchronized ResultadoImportacion importarMercadoLibre(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new IllegalArgumentException("Seleccione un archivo CSV o Excel");
        }
        String nombre = Optional.ofNullable(archivo.getOriginalFilename()).orElse("").toLowerCase(Locale.ROOT);
        if (nombre.endsWith(".xlsx") || nombre.endsWith(".xls")) return importarExcel(archivo);
        if (nombre.endsWith(".csv") || "text/csv".equalsIgnoreCase(archivo.getContentType())) return importarCsv(archivo);
        throw new IllegalArgumentException("El archivo debe ser Excel (.xlsx o .xls) o CSV");
    }

    private ResultadoImportacion importarCsv(MultipartFile archivo) {
        ResultadoImportacion resultado = new ResultadoImportacion();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(archivo.getInputStream(), StandardCharsets.UTF_8))) {
            String encabezado = reader.readLine();
            if (encabezado == null) throw new IllegalArgumentException("El CSV está vacío");
            encabezado = encabezado.replace("\uFEFF", "");
            char separador = detectarSeparador(encabezado);
            List<String> nombres = parsearLinea(encabezado, separador).stream().map(this::normalizar).toList();
            validarEncabezados(nombres);
            String linea;
            int numero = 1;
            while ((linea = reader.readLine()) != null) {
                numero++;
                if (linea.isBlank()) continue;
                try {
                    importarFila(mapearFila(nombres, parsearLinea(linea, separador)), resultado);
                } catch (Exception e) {
                    resultado.error("Fila " + numero + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("No se pudo leer el CSV", e);
        }
        return resultado;
    }

    private ResultadoImportacion importarExcel(MultipartFile archivo) {
        ResultadoImportacion resultado = new ResultadoImportacion();
        List<FilaExcel> filas = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(archivo.getInputStream())) {
            DataFormatter formatter = new DataFormatter(Locale.forLanguageTag("es-AR"));
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            for (Sheet hoja : workbook) {
                int filaEncabezado = buscarFilaEncabezado(hoja, formatter, evaluator);
                if (filaEncabezado < 0) continue;
                List<String> encabezadosOriginales = leerFila(hoja.getRow(filaEncabezado), formatter, evaluator);
                List<String> encabezados = encabezadosOriginales.stream().map(this::normalizar).toList();
                Map<String, String> columnasVariantes = detectarColumnasVariantes(encabezadosOriginales,
                        leerFila(hoja.getRow(filaEncabezado + 1), formatter, evaluator));
                for (int numero = filaEncabezado + 1; numero <= hoja.getLastRowNum(); numero++) {
                    Row row = hoja.getRow(numero);
                    if (row == null) continue;
                    Map<String, String> fila = mapearFila(encabezados, leerFila(row, formatter, evaluator));
                    String id = valor(fila, "item id", "id", "numero de publicacion", "id de publicacion");
                    if (!id.matches("(?i)^ML[A-Z]\\d+$")) continue;
                    String variationId = normalizarVariationId(valor(fila, "variation id", "numero de variante"));
                    String familyId = normalizarIdentificador(valor(fila, "family id", "agrupador de variantes"));
                    agregarAtributosVariantes(fila, columnasVariantes);
                    filas.add(new FilaExcel(id, familyId, variationId, fila));
                }
            }
        } catch (IOException | EncryptedDocumentException e) {
            throw new IllegalArgumentException("No se pudo leer el archivo Excel", e);
        }
        Map<String, Long> integrantesFamilia = filas.stream().filter(f -> !f.familyId().isBlank())
                .collect(java.util.stream.Collectors.groupingBy(FilaExcel::familyId,
                        java.util.stream.Collectors.mapping(FilaExcel::itemId,
                                java.util.stream.Collectors.collectingAndThen(java.util.stream.Collectors.toSet(), s -> (long) s.size()))));
        Map<String, PublicacionExcel> publicaciones = new LinkedHashMap<>();
        for (FilaExcel dato : filas) {
            boolean familiaMultiple = dato.variationId().isBlank() && !dato.familyId().isBlank()
                    && integrantesFamilia.getOrDefault(dato.familyId(), 0L) > 1;
            String clave = familiaMultiple ? "FAMILY:" + dato.familyId() : "ITEM:" + dato.itemId();
            PublicacionExcel publicacion = publicaciones.computeIfAbsent(clave, k -> new PublicacionExcel());
            if (familiaMultiple) {
                dato.fila().put(FAMILY_ID, dato.familyId());
                dato.fila().put(ITEM_ID_VARIANTE, dato.itemId());
                fusionarDatosGenerales(publicacion.general, dato.fila());
                publicacion.variantes.putIfAbsent("ITEM:" + dato.itemId(), dato.fila());
            } else if (dato.variationId().isBlank()) {
                fusionarSinPisar(publicacion.general, dato.fila());
            } else {
                fusionarDatosGenerales(publicacion.general, dato.fila());
                publicacion.variantes.putIfAbsent(dato.variationId(), dato.fila());
            }
        }
        if (publicaciones.isEmpty()) {
            throw new IllegalArgumentException("No se encontraron publicaciones en las hojas del Excel");
        }
        for (Map.Entry<String, PublicacionExcel> entry : publicaciones.entrySet()) {
            try {
                Producto producto = importarFila(entry.getValue().general, resultado);
                for (Map.Entry<String, Map<String, String>> variante : entry.getValue().variantes.entrySet()) {
                    importarVariante(producto, variante.getKey(), variante.getValue());
                }
            } catch (Exception e) {
                resultado.error("Publicación " + entry.getKey() + ": " + e.getMessage());
            }
        }
        return resultado;
    }

    private Map<String, String> detectarColumnasVariantes(List<String> encabezados, List<String> tipos) {
        Map<String, String> columnas = new LinkedHashMap<>();
        for (int i = 0; i < encabezados.size(); i++) {
            String encabezado = normalizar(encabezados.get(i));
            String tipo = i < tipos.size() ? normalizar(tipos.get(i)) : "";
            if (!"variation".equals(tipo) && !encabezado.endsWith(" variation-column")) continue;
            String id = encabezado.replaceFirst(" variation-column$", "")
                    .replace(' ', '_').toUpperCase(Locale.ROOT);
            if (!id.isBlank()) columnas.put(encabezado, id);
        }
        return columnas;
    }

    private void agregarAtributosVariantes(Map<String, String> fila, Map<String, String> columnas) {
        columnas.forEach((columna, id) -> {
            String valor = fila.getOrDefault(columna, "").trim();
            if (!valor.isBlank()) fila.put(ATRIBUTO_VARIANTE + id, valor);
        });
    }

    private int buscarFilaEncabezado(Sheet hoja, DataFormatter formatter, FormulaEvaluator evaluator) {
        int ultima = Math.min(hoja.getLastRowNum(), 50);
        for (int numero = hoja.getFirstRowNum(); numero <= ultima; numero++) {
            Row row = hoja.getRow(numero);
            if (row == null) continue;
            Set<String> valores = new HashSet<>();
            for (String valor : leerFila(row, formatter, evaluator)) valores.add(normalizar(valor));
            boolean tieneTitulo = valores.stream().anyMatch(COLUMNAS_TITULO::contains);
            boolean tieneId = valores.stream().anyMatch(COLUMNAS_ID::contains);
            if (tieneTitulo && tieneId) return numero;
        }
        return -1;
    }

    private List<String> leerFila(Row row, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (row == null || row.getLastCellNum() < 0) return List.of();
        List<String> valores = new ArrayList<>();
        for (int columna = 0; columna < row.getLastCellNum(); columna++) {
            Cell cell = row.getCell(columna, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            valores.add(cell == null ? "" : formatter.formatCellValue(cell, evaluator).trim());
        }
        return valores;
    }

    private void fusionarSinPisar(Map<String, String> destino, Map<String, String> origen) {
        origen.forEach((clave, valor) -> {
            if (valor != null && !valor.isBlank() && destino.getOrDefault(clave, "").isBlank()) destino.put(clave, valor);
        });
    }

    private void fusionarDatosGenerales(Map<String, String> destino, Map<String, String> origen) {
        origen.forEach((clave, valor) -> {
            if (!COLUMNAS_DE_VARIANTE.contains(clave) && valor != null && !valor.isBlank()
                    && destino.getOrDefault(clave, "").isBlank()) destino.put(clave, valor);
        });
    }

    private Map<String, String> mapearFila(List<String> headers, List<String> values) {
        Map<String, String> fila = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            if (!headers.get(i).isBlank()) fila.put(headers.get(i), i < values.size() ? values.get(i).trim() : "");
        }
        return fila;
    }

    private Producto importarFila(Map<String, String> fila, ResultadoImportacion resultado) {
        String titulo = valor(fila, "titulo", "title", "descripcion", "nombre");
        if (titulo.isBlank()) throw new IllegalArgumentException("falta el título/descripción");
        String mlId = valor(fila, "numero de publicacion", "id de publicacion", "item id", "id");
        String familyId = fila.getOrDefault(FAMILY_ID, "");
        String sku = valor(fila, "sku", "seller sku", "codigo sku");
        Optional<Producto> encontrado = !sku.isBlank()
                ? productoRepository.findBySkuIgnoreCase(sku) : Optional.empty();
        if (encontrado.isEmpty() && !familyId.isBlank()) encontrado = productoRepository.findByMercadoLibreFamilyId(familyId);
        if (encontrado.isEmpty() && !mlId.isBlank()) encontrado = productoRepository.findByMercadoLibreId(mlId);
        Producto producto = encontrado.orElseGet(Producto::new);
        producto.setDescripcion(titulo);
        if (!sku.isBlank()) producto.setSku(sku);
        if (!mlId.isBlank()) producto.setMercadoLibreId(mlId);
        if (!familyId.isBlank()) producto.setMercadoLibreFamilyId(familyId);

        String categoria = valor(fila, "id de categoria", "categoria id", "category id");
        if (!categoria.isBlank()) producto.setMercadoLibreCategoriaId(categoria);
        String cantidad = valor(fila, "stock", "cantidad disponible", "cantidad", "quantity", "stock en tu deposito");
        if (!cantidad.isBlank()) producto.setCantidad(entero(cantidad, 0));
        else if (producto.getCantidad() == null) producto.setCantidad(0);

        BigDecimal precio = decimal(valor(fila, "precio", "price", "precio de venta"));
        if (precio != null) {
            producto.setPrecioContado(precio);
            producto.setPrecioTarjeta(precio);
            producto.setPrecioCuentaCorriente(precio);
        }
        String descripcion = valor(fila, "description", "descripcion de la publicacion");
        if (!descripcion.isBlank()) producto.setMercadoLibreDescripcion(descripcion);
        asignarSiTieneTexto(producto::setMercadoLibreMarca, valor(fila, "brand", "marca"));
        asignarSiTieneTexto(producto::setMercadoLibreModelo, valor(fila, "model", "modelo"));
        String gtin = valor(fila, "gtin", "codigo universal de producto");
        if (gtin.matches("\\d{8,14}")) producto.setMercadoLibreGtin(gtin);
        asignarSiTieneTexto(producto::setMercadoLibreGarantiaTipo,
                valor(fila, "warranty type", "tipo de garantia"));
        String garantiaTiempo = valor(fila, "warranty time", "tiempo de garantia");
        String garantiaUnidad = valor(fila, "warranty time unit", "duracion de la garantia");
        if (!garantiaTiempo.isBlank() && !"0".equals(limpiarNumero(garantiaTiempo))) {
            producto.setMercadoLibreGarantiaTiempo((garantiaTiempo + " " + garantiaUnidad).trim());
        }
        String envio = normalizar(valor(fila, "shipping method", "forma de entrega"));
        if (!envio.isBlank()) {
            producto.setMercadoLibreEnvioGratis(envio.contains("gratis"));
            if (envio.contains("mercado envios")) producto.setMercadoLibreModoEnvio("me2");
            else if (envio.contains("acordar") || envio.contains("no especificado")) producto.setMercadoLibreModoEnvio("not_specified");
        }
        asignarBooleano(producto::setMercadoLibreRetiroPersonal,
                valor(fila, "local pickup", "retiro en persona"));
        asignarSiTieneTexto(producto::setMercadoLibreCondicion,
                mapearCondicion(valor(fila, "condition", "condicion")));
        asignarSiTieneTexto(producto::setMercadoLibreEstado,
                mapearEstado(valor(fila, "status", "estado")));
        String disponibilidad = valor(fila, "manufacturing time", "tiempo de disponibilidad del producto (dias)",
                "tiempo de disponibilidad");
        if (!disponibilidad.isBlank() && !normalizar(disponibilidad).contains("no especificado")) {
            Integer dias = enteroNullable(disponibilidad);
            if (dias != null && dias >= 0) producto.setMercadoLibreTiempoDisponibilidad(dias);
        }
        String tipoPublicacion = valor(fila, "listing type id", "listing type");
        if (tipoPublicacion.matches("(?i)^(gold_pro|gold_special|free)$")) {
            producto.setMercadoLibreListingTypeId(tipoPublicacion.toLowerCase(Locale.ROOT));
        }
        asignarSiTieneTexto(producto::setMercadoLibreConfiguracionCuotas,
                valor(fila, "listing type v3", "cuotas"));
        asignarSiTieneTexto(producto::setMercadoLibreCargoVenta,
                valor(fila, "fee per sale marketplace v2", "cargos por vender"));
        asignarSiTieneTexto(producto::setMercadoLibreCostoFinanciacion,
                valor(fila, "cost of financing marketplace", "costo por ofrecer cuotas"));

        if (producto.getTipoIva() == null) producto.setTipoIva(TipoIva.IVA_21);
        productoService.saveProducto(producto);
        if (encontrado.isPresent()) resultado.actualizado(); else resultado.creado();
        return producto;
    }

    private void importarVariante(Producto producto, String variationId, Map<String, String> fila) {
        String itemId = fila.getOrDefault(ITEM_ID_VARIANTE, "");
        Optional<ProductoVariante> encontrada = !itemId.isBlank()
                ? varianteRepository.findByProductoIdAndMercadoLibreItemId(producto.getId(), itemId)
                : varianteRepository.findByProductoIdAndMercadoLibreVariationId(producto.getId(), variationId);
        String productNumber = valor(fila, "product number", "numero de producto");
        if (encontrada.isEmpty() && !productNumber.isBlank()) {
            encontrada = varianteRepository.findByProductoIdAndMercadoLibreProductNumber(producto.getId(), productNumber);
        }
        ProductoVariante variante = encontrada.orElseGet(ProductoVariante::new);
        if (!itemId.isBlank()) variante.setMercadoLibreItemId(itemId);
        else variante.setMercadoLibreVariationId(variationId);
        asignarSiTieneTexto(variante::setMercadoLibreProductNumber, productNumber);
        asignarSiTieneTexto(variante::setNombre,
                valor(fila, "variations", "variantes"));
        asignarSiTieneTexto(variante::setTalle,
                valor(fila, "size variation-column", "talle", "size"));
        asignarSiTieneTexto(variante::setColor,
                valor(fila, "color variation-column", "color", "main color", "color principal"));
        Map<String, String> atributos = atributosVariante(fila);
        if (!atributos.isEmpty()) {
            variante.setNombre(String.join(" / ", atributos.values()));
            try {
                variante.setMercadoLibreAtributosJson(objectMapper.writeValueAsString(atributos));
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("No se pudieron guardar los atributos de la variante", e);
            }
        }
        String sku = valor(fila, "sku", "seller sku", "codigo sku");
        if (!sku.isBlank()) variante.setSku(sku);
        String cantidad = valor(fila, "quantity", "stock", "stock en tu deposito", "cantidad");
        if (!cantidad.isBlank()) variante.setStock(entero(cantidad, 0));
        BigDecimal precio = decimal(valor(fila, "price", "precio"));
        if (precio != null) {
            variante.setPrecioContado(precio);
            variante.setPrecioTarjeta(precio);
            variante.setPrecioCuentaCorriente(precio);
        }
        String gtin = valor(fila, "gtin", "codigo universal de producto");
        if (gtin.matches("\\d{8,14}")) variante.setMercadoLibreGtin(gtin);
        varianteService.guardarImportada(producto, variante);
    }

    private Map<String, String> atributosVariante(Map<String, String> fila) {
        Map<String, String> atributos = new LinkedHashMap<>();
        fila.forEach((clave, valor) -> {
            if (clave.startsWith(ATRIBUTO_VARIANTE) && valor != null && !valor.isBlank()) {
                atributos.put(clave.substring(ATRIBUTO_VARIANTE.length()), valor.trim());
            }
        });
        return atributos;
    }

    private static class PublicacionExcel {
        private final Map<String, String> general = new LinkedHashMap<>();
        private final Map<String, Map<String, String>> variantes = new LinkedHashMap<>();
    }

    private record FilaExcel(String itemId, String familyId, String variationId, Map<String, String> fila) {}

    private void asignarSiTieneTexto(java.util.function.Consumer<String> setter, String valor) {
        if (valor != null && !valor.isBlank()) setter.accept(valor);
    }

    private void asignarBooleano(java.util.function.Consumer<Boolean> setter, String valor) {
        if (valor == null || valor.isBlank()) return;
        String normalizado = normalizar(valor);
        if (Set.of("si", "acepta", "true", "1").contains(normalizado)) setter.accept(true);
        else if (Set.of("no", "no acepta", "false", "0").contains(normalizado)) setter.accept(false);
    }

    private String mapearCondicion(String valor) {
        return switch (normalizar(valor)) {
            case "nuevo", "new" -> "new";
            case "usado", "used" -> "used";
            case "reacondicionado", "refurbished" -> "refurbished";
            default -> "";
        };
    }

    private String mapearEstado(String valor) {
        return switch (normalizar(valor)) {
            case "activa", "activo", "active" -> "active";
            case "pausada", "pausado", "inactiva", "inactivo", "paused" -> "paused";
            case "cerrada", "cerrado", "finalizada", "finalizado", "closed" -> "closed";
            default -> "";
        };
    }

    private Integer enteroNullable(String valor) {
        String limpio = limpiarNumero(valor);
        if (limpio.isBlank()) return null;
        try { return new BigDecimal(limpio).intValue(); }
        catch (NumberFormatException e) { return null; }
    }

    private void validarEncabezados(List<String> headers) {
        boolean titulo = headers.stream().anyMatch(COLUMNAS_TITULO::contains);
        if (!titulo) throw new IllegalArgumentException("No se encontró una columna Título, Descripción o Nombre");
    }

    private String valor(Map<String, String> fila, String... aliases) {
        for (String alias : aliases) {
            String value = fila.get(alias);
            if (value != null && !value.isBlank()) return value;
        }
        return "";
    }

    private String normalizar(String value) {
        String normal = Normalizer.normalize(value.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        return normal.replaceAll("\\p{M}", "").replace('_', ' ').replaceAll("\\s+", " ");
    }

    private String normalizarVariationId(String value) {
        if (value == null || value.isBlank()) return "";
        String normalizado = normalizar(value);
        if (Set.of("-", "n/a", "na", "null", "no aplica", "sin variante", "sin variacion")
                .contains(normalizado)) return "";
        return value.trim();
    }

    private String normalizarIdentificador(String value) {
        if (value == null || value.isBlank()) return "";
        String normalizado = normalizar(value);
        return Set.of("-", "n/a", "na", "null", "no aplica").contains(normalizado) ? "" : value.trim();
    }

    private char detectarSeparador(String line) {
        return contar(line, ';') > contar(line, ',') ? ';' : ',';
    }

    private long contar(String line, char c) { return line.chars().filter(x -> x == c).count(); }

    static List<String> parsearLinea(String line, char separador) {
        List<String> values = new ArrayList<>();
        StringBuilder actual = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') { actual.append('"'); i++; }
                else quoted = !quoted;
            } else if (c == separador && !quoted) { values.add(actual.toString()); actual.setLength(0); }
            else actual.append(c);
        }
        values.add(actual.toString());
        return values;
    }

    private Integer entero(String value, int defecto) {
        if (value.isBlank()) return defecto;
        try { return new BigDecimal(limpiarNumero(value)).intValue(); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("cantidad inválida: " + value); }
    }

    private BigDecimal decimal(String value) {
        if (value.isBlank()) return null;
        try { return new BigDecimal(limpiarNumero(value)); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("precio inválido: " + value); }
    }

    private String limpiarNumero(String value) {
        String limpio = value.replaceAll("[^0-9,.-]", "");
        if (limpio.contains(",") && limpio.contains(".")) limpio = limpio.lastIndexOf(',') > limpio.lastIndexOf('.')
                ? limpio.replace(".", "").replace(',', '.') : limpio.replace(",", "");
        else if (limpio.contains(",")) limpio = limpio.replace(',', '.');
        return limpio;
    }
}

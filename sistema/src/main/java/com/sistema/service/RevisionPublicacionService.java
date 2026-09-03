package com.sistema.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sistema.dto.AtributoVarianteMl;
import com.sistema.dto.RevisionProductoPublicacionDto;
import com.sistema.model.CanalVenta;
import com.sistema.model.Producto;
import com.sistema.model.ProductoVariante;
import com.sistema.repository.ProductoRepository;
import com.sistema.repository.ProductoVarianteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
public class RevisionPublicacionService {
    private static final long TIEMPO_MAXIMO_CONSULTAS_ML_MS = 15_000;
    private final ProductoRepository productoRepository;
    private final ProductoVarianteRepository varianteRepository;
    private final MercadoLibreAtributosVarianteService atributosMercadoLibre;
    private final ObjectMapper objectMapper;
    private final Map<Long, RevisionPreparada> revisionesPreparadas =
            new ConcurrentHashMap<>();

    public RevisionPublicacionService(
            ProductoRepository productoRepository,
            ProductoVarianteRepository varianteRepository,
            MercadoLibreAtributosVarianteService atributosMercadoLibre,
            ObjectMapper objectMapper) {
        this.productoRepository = productoRepository;
        this.varianteRepository = varianteRepository;
        this.atributosMercadoLibre = atributosMercadoLibre;
        this.objectMapper = objectMapper;
    }

    public List<RevisionProductoPublicacionDto> revisar(
            Collection<Long> productoIds, Collection<CanalVenta> canales) {
        return revisar(productoIds, canales,
                System.nanoTime() + TIEMPO_MAXIMO_CONSULTAS_ML_MS * 1_000_000L,
                () -> false, false);
    }

    public int prepararEnSegundoPlano(
            Long trabajoId, Collection<Long> productoIds,
            Collection<CanalVenta> canales,
            BooleanSupplier cancelacionSolicitada) {
        if (trabajoId == null) throw new IllegalArgumentException(
                "Falta identificar el trabajo de preparación");
        BooleanSupplier cancelacion = cancelacionSolicitada == null
                ? () -> false : cancelacionSolicitada;
        List<RevisionProductoPublicacionDto> resultado = revisar(
                productoIds, canales, Long.MAX_VALUE, cancelacion, true);
        if (cancelacion.getAsBoolean()) {
            revisionesPreparadas.remove(trabajoId);
        } else {
            limpiarRevisionesPreparadasVencidas();
            revisionesPreparadas.put(trabajoId,
                    new RevisionPreparada(List.copyOf(resultado), Instant.now()));
        }
        return resultado.size();
    }

    public Optional<List<RevisionProductoPublicacionDto>> consumirRevisionPreparada(
            Long trabajoId) {
        if (trabajoId == null) return Optional.empty();
        RevisionPreparada preparada = revisionesPreparadas.remove(trabajoId);
        return preparada == null ? Optional.empty()
                : Optional.of(preparada.productos());
    }

    private void limpiarRevisionesPreparadasVencidas() {
        Instant limite = Instant.now().minus(2, ChronoUnit.HOURS);
        revisionesPreparadas.entrySet().removeIf(
                entrada -> entrada.getValue().creadaEn().isBefore(limite));
    }

    private List<RevisionProductoPublicacionDto> revisar(
            Collection<Long> productoIds, Collection<CanalVenta> canales,
            long limiteNanos, BooleanSupplier cancelacionSolicitada,
            boolean redetectarCategorias) {
        if (productoIds == null) return List.of();
        boolean revisarMercadoLibre = canales != null
                && canales.contains(CanalVenta.MERCADO_LIBRE);
        List<Long> ids = productoIds.stream().filter(Objects::nonNull)
                .distinct().toList();
        if (ids.isEmpty()) return List.of();
        Map<Long, Producto> productosPorId = productoRepository.findAllById(ids)
                .stream().collect(Collectors.toMap(
                        Producto::getId, Function.identity(), (a, b) -> a,
                        LinkedHashMap::new));
        Map<Long, List<ProductoVariante>> variantesPorProducto = varianteRepository
                .findByProductoIdInOrderByProductoIdAscNombreAsc(ids).stream()
                .collect(Collectors.groupingBy(
                        variante -> variante.getProducto().getId(),
                        LinkedHashMap::new, Collectors.toList()));
        ConsultasMercadoLibre consultas = new ConsultasMercadoLibre(limiteNanos);
        List<RevisionProductoPublicacionDto> resultado = new ArrayList<>();
        for (Long id : ids) {
            if (cancelacionSolicitada.getAsBoolean()) break;
            Producto producto = productosPorId.get(id);
            if (producto != null) {
                resultado.add(revisar(producto,
                        variantesPorProducto.getOrDefault(id, List.of()),
                        revisarMercadoLibre, consultas, redetectarCategorias));
            }
        }
        return resultado;
    }

    private RevisionProductoPublicacionDto revisar(
            Producto producto,
            List<ProductoVariante> variantes,
            boolean revisarMercadoLibre,
            ConsultasMercadoLibre consultas,
            boolean redetectarCategorias) {
        LinkedHashSet<String> faltantes = new LinkedHashSet<>();
        LinkedHashSet<String> atributosFaltantes = new LinkedHashSet<>();
        LinkedHashSet<String> atributosObligatorios = new LinkedHashSet<>();
        List<AtributoVarianteMl> atributosGenerales = new ArrayList<>();
        List<AtributoVarianteMl> atributosDeVariante = new ArrayList<>();
        Map<String, String> valoresGenerales = valoresAtributosProducto(producto);
        Map<Long, Map<String, String>> valoresPorVariante = new LinkedHashMap<>();
        variantes.stream().filter(v -> v.getId() != null).forEach(v ->
                valoresPorVariante.put(v.getId(), valoresAtributosVariante(v)));

        if (!tieneTexto(producto.getDescripcion())) faltantes.add("Título");
        int stockTotal = variantes.isEmpty()
                ? Optional.ofNullable(producto.getCantidad()).orElse(0)
                : variantes.stream().mapToInt(v -> Optional.ofNullable(v.getStock()).orElse(0)).sum();
        if (stockTotal <= 0) faltantes.add("Stock mayor a cero");
        if (variantes.isEmpty()) {
            if (!precioPositivo(producto.getPrecioContado())) faltantes.add("Precio de contado");
        } else {
            boolean faltaPrecio = variantes.stream().anyMatch(variante ->
                    !precioPositivo(variante.getPrecioContado())
                            && !precioPositivo(producto.getPrecioContado()));
            if (faltaPrecio) faltantes.add("Precio de contado en todas las variantes");
        }

        if (revisarMercadoLibre) revisarMercadoLibre(
                producto, variantes, faltantes, atributosFaltantes,
                atributosObligatorios, atributosGenerales,
                atributosDeVariante, consultas, redetectarCategorias);
        return new RevisionProductoPublicacionDto(
                producto, variantes, List.copyOf(faltantes),
                List.copyOf(atributosFaltantes),
                List.copyOf(atributosObligatorios),
                List.copyOf(atributosGenerales),
                List.copyOf(atributosDeVariante),
                Map.copyOf(valoresGenerales),
                valoresPorVariante.entrySet().stream().collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entrada -> Map.copyOf(entrada.getValue()),
                        (a, b) -> a, LinkedHashMap::new)));
    }

    private void revisarMercadoLibre(
            Producto producto,
            List<ProductoVariante> variantes,
            Set<String> faltantes,
            Set<String> atributosFaltantes,
            Set<String> atributosObligatorios,
            List<AtributoVarianteMl> atributosGenerales,
            List<AtributoVarianteMl> atributosDeVariante,
            ConsultasMercadoLibre consultas,
            boolean redetectarCategorias) {
        if (redetectarCategorias) {
            redetectarCategoriaGuardada(producto, consultas);
        }
        if (!tieneTexto(producto.getMercadoLibreCategoriaId())) {
            ConsultaCategoria categoria = consultas.predecir(producto);
            if (categoria.error() != null) {
                faltantes.add("Categoría de Mercado Libre");
                faltantes.add("No se pudo detectar la categoría: "
                        + mensaje(categoria.error()));
                return;
            }
            if (tieneTexto(categoria.categoriaId())) {
                producto.setMercadoLibreCategoriaId(categoria.categoriaId());
                productoRepository.save(producto);
            } else {
                faltantes.add("Categoría de Mercado Libre");
                return;
            }
        }
        try {
            var resultado = obtenerAtributosConRecuperacion(producto, consultas);
            boolean tieneFoto = producto.tieneFoto()
                    || variantes.stream().anyMatch(ProductoVariante::tieneFoto);
            if (!tieneFoto) faltantes.add("Foto");
            if (resultado == null) return;
            Set<String> atributosProducto = atributosProducto(producto);
            List<Set<String>> atributosVariantes = variantes.stream()
                    .map(this::atributosVariante).toList();
            for (AtributoVarianteMl atributo : resultado.atributos()) {
                if (atributo.permiteVariar()) atributosDeVariante.add(atributo);
                else atributosGenerales.add(atributo);
                if (!atributo.obligatorio()) continue;
                atributosObligatorios.add(atributo.nombre());
                String id = atributo.id();
                boolean presenteProducto = atributosProducto.contains(id);
                boolean presenteVariantes = !atributosVariantes.isEmpty()
                        && atributosVariantes.stream().allMatch(ids -> ids.contains(id));
                if ("GTIN".equals(id) && atributosProducto.contains("EMPTY_GTIN_REASON")) {
                    presenteProducto = true;
                }
                if (!presenteProducto && !presenteVariantes) {
                    atributosFaltantes.add(atributo.nombre());
                }
            }
            if (!atributosFaltantes.isEmpty()) {
                faltantes.add("Atributos: " + String.join(", ", atributosFaltantes));
            }
        } catch (RuntimeException e) {
            if (!tieneTexto(producto.getMercadoLibreCategoriaId())) {
                faltantes.add("Categoría de Mercado Libre");
            }
            faltantes.add("Revisar categoría: " + mensaje(e));
        }
    }

    private void redetectarCategoriaGuardada(
            Producto producto, ConsultasMercadoLibre consultas) {
        ConsultaCategoria categoria = consultas.predecir(producto);
        if (categoria.error() != null || !tieneTexto(categoria.categoriaId())) return;
        String actual = limpiar(producto.getMercadoLibreCategoriaId());
        if (categoria.categoriaId().equalsIgnoreCase(
                actual == null ? "" : actual)) return;
        ConsultaAtributos validacion = consultas.obtener(categoria.categoriaId());
        if (validacion.error() != null || validacion.resultado() == null) return;
        producto.setMercadoLibreCategoriaId(categoria.categoriaId());
        productoRepository.save(producto);
    }

    private MercadoLibreAtributosVarianteService.Resultado
            obtenerAtributosConRecuperacion(
                    Producto producto, ConsultasMercadoLibre consultas) {
        String categoriaOriginal = producto.getMercadoLibreCategoriaId();
        ConsultaAtributos consulta = consultas.obtener(categoriaOriginal);
        if (consulta.error() == null) return consulta.resultado();
        if (!esCategoriaNoEncontrada(consulta.error())) throw consulta.error();

        ConsultaCategoria reemplazo = consultas.predecirPorTitulo(producto);
        if (reemplazo.error() != null) throw new IllegalArgumentException(
                "La categoría " + categoriaOriginal
                        + " no existe y no se pudo detectar una categoría actual: "
                        + mensaje(reemplazo.error()), reemplazo.error());
        if (!tieneTexto(reemplazo.categoriaId())
                || categoriaOriginal.equalsIgnoreCase(reemplazo.categoriaId())) {
            throw new IllegalArgumentException(
                    "La categoría " + categoriaOriginal
                            + " no existe en Mercado Libre. Ingrese otra categoría.");
        }

        ConsultaAtributos consultaReemplazo = consultas.obtener(
                reemplazo.categoriaId());
        if (consultaReemplazo.error() != null) {
            throw new IllegalArgumentException(
                    "La categoría " + categoriaOriginal
                            + " no existe y Mercado Libre tampoco aceptó la categoría "
                            + reemplazo.categoriaId() + " detectada por el título: "
                            + mensaje(consultaReemplazo.error()),
                    consultaReemplazo.error());
        }
        producto.setMercadoLibreCategoriaId(reemplazo.categoriaId());
        productoRepository.save(producto);
        return consultaReemplazo.resultado();
    }

    private boolean esCategoriaNoEncontrada(Throwable error) {
        Throwable actual = error;
        while (actual != null) {
            if (actual instanceof RestClientResponseException respuesta
                    && respuesta.getStatusCode().value() == 404) return true;
            String texto = actual.getMessage();
            if (texto != null && texto.contains("Category not found")) return true;
            actual = actual.getCause();
        }
        return false;
    }

    private final class ConsultasMercadoLibre {
        private final long limiteNanos;
        private final Map<String, ConsultaAtributos> porCategoria =
                new LinkedHashMap<>();
        private final Map<String, ConsultaCategoria> predicciones =
                new LinkedHashMap<>();

        private ConsultasMercadoLibre(long limiteNanos) {
            this.limiteNanos = limiteNanos;
        }

        private ConsultaAtributos obtener(String categoria) {
            ConsultaAtributos existente = porCategoria.get(categoria);
            if (existente != null) return existente;
            ConsultaAtributos nueva;
            if (System.nanoTime() >= limiteNanos) {
                nueva = new ConsultaAtributos(null, new IllegalStateException(
                        "se alcanzó el tiempo máximo de consulta; abra el producto para validar sus atributos"));
            } else {
                try {
                    nueva = new ConsultaAtributos(
                            atributosMercadoLibre.obtenerPorCategoria(categoria), null);
                } catch (RuntimeException e) {
                    nueva = new ConsultaAtributos(null, e);
                }
            }
            porCategoria.put(categoria, nueva);
            return nueva;
        }

        private ConsultaCategoria predecir(Producto producto) {
            String origen = limpiar(producto.getCategoriaOrigen());
            String titulo = limpiar(producto.getDescripcion());
            ConsultaCategoria porTitulo = predecir(
                    titulo, "titulo:" + normalizarClave(titulo));
            if (porTitulo.error() != null || tieneTexto(porTitulo.categoriaId())
                    || origen == null || normalizarClave(origen)
                            .equals(normalizarClave(titulo))) {
                return porTitulo;
            }

            String tituloYOrigen = titulo == null ? origen : titulo + " " + origen;
            ConsultaCategoria combinada = predecir(
                    tituloYOrigen, "combinada:" + normalizarClave(tituloYOrigen));
            if (combinada.error() != null || tieneTexto(combinada.categoriaId())) {
                return combinada;
            }
            return predecir(origen, "origen:" + normalizarClave(origen));
        }

        private ConsultaCategoria predecirPorTitulo(Producto producto) {
            String titulo = limpiar(producto.getDescripcion());
            return predecir(titulo, "titulo:" + normalizarClave(titulo));
        }

        private ConsultaCategoria predecir(String consulta, String clave) {
            ConsultaCategoria existente = predicciones.get(clave);
            if (existente != null) return existente;
            ConsultaCategoria nueva;
            if (consulta == null) {
                nueva = new ConsultaCategoria("", null);
            } else if (System.nanoTime() >= limiteNanos) {
                nueva = new ConsultaCategoria("", new IllegalStateException(
                        "se alcanzó el tiempo máximo de consulta"));
            } else {
                try {
                    nueva = new ConsultaCategoria(
                            atributosMercadoLibre.predecirCategoria(consulta), null);
                } catch (RuntimeException e) {
                    nueva = new ConsultaCategoria("", e);
                }
            }
            predicciones.put(clave, nueva);
            return nueva;
        }

        private String normalizarClave(String valor) {
            if (valor == null) return "";
            return java.text.Normalizer.normalize(
                            valor, java.text.Normalizer.Form.NFD)
                    .replaceAll("\\p{M}", "")
                    .replaceAll("[^A-Za-z0-9]+", " ")
                    .trim().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private record ConsultaAtributos(
            MercadoLibreAtributosVarianteService.Resultado resultado,
            RuntimeException error) {
    }

    private record ConsultaCategoria(String categoriaId, RuntimeException error) {
    }

    private record RevisionPreparada(
            List<RevisionProductoPublicacionDto> productos, Instant creadaEn) {
    }

    private Set<String> atributosProducto(Producto producto) {
        Set<String> ids = new LinkedHashSet<>();
        ids.add("SELLER_SKU");
        ids.add("ITEM_CONDITION");
        agregarSiTiene(ids, "BRAND", producto.getMercadoLibreMarca());
        agregarSiTiene(ids, "MODEL", producto.getMercadoLibreModelo());
        agregarSiTiene(ids, "GTIN", producto.getMercadoLibreGtin());
        agregarSiTiene(ids, "GENDER", producto.getMercadoLibreGenero());
        agregarSiTiene(ids, "GARMENT_TYPE", producto.getMercadoLibreTipoPrenda());
        String json = producto.getMercadoLibreAtributosJson();
        if (!tieneTexto(json)) return ids;
        try {
            List<Map<String, Object>> atributos = objectMapper.readValue(
                    json, new TypeReference<>() {});
            atributos.forEach(atributo -> {
                Object id = atributo.get("id");
                Object valor = atributo.get("value_name");
                Object valorId = atributo.get("value_id");
                if (id != null && ((valor != null && !valor.toString().isBlank())
                        || (valorId != null && !valorId.toString().isBlank()))) {
                    ids.add(id.toString());
                }
            });
        } catch (Exception e) {
            // La pantalla lo mostrará como pendiente cuando consulte los atributos.
        }
        return ids;
    }

    private Map<String, String> valoresAtributosProducto(Producto producto) {
        Map<String, String> valores = new LinkedHashMap<>();
        agregarValor(valores, "BRAND", producto.getMercadoLibreMarca());
        agregarValor(valores, "MODEL", producto.getMercadoLibreModelo());
        agregarValor(valores, "GARMENT_TYPE", producto.getMercadoLibreTipoPrenda());
        agregarValor(valores, "GENDER", producto.getMercadoLibreGenero());
        agregarValor(valores, "GTIN", producto.getMercadoLibreGtin());
        leerAtributosProductoPayload(producto).forEach((id, atributo) -> {
            Object valor = atributo.get("value_name");
            if (valor != null && !valor.toString().isBlank()) valores.put(id, valor.toString());
        });
        return valores;
    }

    private Map<String, String> valoresAtributosVariante(ProductoVariante variante) {
        Map<String, String> valores = new LinkedHashMap<>();
        agregarValor(valores, "SIZE", variante.getTalle());
        agregarValor(valores, "COLOR", variante.getColor());
        String json = variante.getMercadoLibreAtributosJson();
        if (!tieneTexto(json)) return valores;
        try {
            valores.putAll(objectMapper.readValue(
                    json, new TypeReference<LinkedHashMap<String, String>>() {}));
        } catch (Exception ignored) {
            // La validación ya informa los JSON inválidos.
        }
        return valores;
    }

    private Set<String> atributosVariante(ProductoVariante variante) {
        Set<String> ids = new LinkedHashSet<>();
        agregarSiTiene(ids, "SIZE", variante.getTalle());
        agregarSiTiene(ids, "COLOR", variante.getColor());
        agregarSiTiene(ids, "GTIN", variante.getMercadoLibreGtin());
        String json = variante.getMercadoLibreAtributosJson();
        if (!tieneTexto(json)) return ids;
        try {
            Map<String, String> atributos = objectMapper.readValue(
                    json, new TypeReference<LinkedHashMap<String, String>>() {});
            atributos.forEach((id, valor) -> agregarSiTiene(ids, id, valor));
        } catch (Exception e) {
            // La validación de publicación informará el JSON inválido si se intenta usar.
        }
        return ids;
    }

    public void actualizar(
            Long productoId,
            String titulo,
            String descripcionMercadoLibre,
            String categoriaMercadoLibre,
            String marca,
            String modelo,
            Integer stock,
            BigDecimal precio,
            String modoEnvio,
            boolean envioGratis,
            boolean retiroPersonal,
            String tipoPublicacion,
            List<Long> varianteIds,
            List<Integer> stocksVariantes,
            List<BigDecimal> preciosVariantes,
            Map<String, String> parametros) {
        Producto producto = productoRepository.findById(productoId)
                .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado"));
        if (!tieneTexto(titulo)) throw new IllegalArgumentException("Ingrese el título");
        producto.setDescripcion(titulo.trim());
        producto.setMercadoLibreDescripcion(limpiar(descripcionMercadoLibre));
        producto.setMercadoLibreCategoriaId(limpiar(categoriaMercadoLibre));
        producto.setMercadoLibreMarca(limpiar(marca));
        producto.setMercadoLibreModelo(limpiar(modelo));
        producto.setMercadoLibreModoEnvio(limpiar(modoEnvio));
        producto.setMercadoLibreEnvioGratis(envioGratis);
        producto.setMercadoLibreRetiroPersonal(retiroPersonal);
        producto.setMercadoLibreListingTypeId(limpiar(tipoPublicacion));
        Set<String> idsGeneralesActualizados = actualizarAtributosGenerales(
                producto, parametros);

        List<ProductoVariante> variantes = varianteRepository
                .findByProductoIdOrderByNombreAsc(productoId);
        if (variantes.isEmpty()) {
            producto.setCantidad(Math.max(Optional.ofNullable(stock).orElse(0), 0));
            producto.setPrecioContado(precio);
        } else {
            actualizarAtributosDeVariantes(
                    variantes, parametros, idsGeneralesActualizados);
            Map<Long, ProductoVariante> porId = new LinkedHashMap<>();
            variantes.forEach(variante -> porId.put(variante.getId(), variante));
            int cantidad = varianteIds == null ? 0 : varianteIds.size();
            for (int i = 0; i < cantidad; i++) {
                ProductoVariante variante = porId.get(varianteIds.get(i));
                if (variante == null) throw new IllegalArgumentException("Variante inválida");
                Integer stockVariante = valor(stocksVariantes, i);
                BigDecimal precioVariante = valor(preciosVariantes, i);
                variante.setStock(Math.max(Optional.ofNullable(stockVariante).orElse(0), 0));
                variante.setPrecioContado(precioVariante);
            }
            varianteRepository.saveAll(variantes);
            producto.setCantidad(variantes.stream()
                    .mapToInt(v -> Optional.ofNullable(v.getStock()).orElse(0)).sum());
            producto.setUsaVariantes(true);
        }
        productoRepository.save(producto);
    }

    private Set<String> actualizarAtributosGenerales(
            Producto producto, Map<String, String> parametros) {
        if (parametros == null) return Set.of();
        String prefijo = "ml_general_";
        Set<String> ids = parametros.keySet().stream()
                .filter(clave -> clave.startsWith(prefijo))
                .map(clave -> clave.substring(prefijo.length()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (ids.isEmpty()) return ids;
        Map<String, Map<String, Object>> adicionales =
                leerAtributosProductoPayload(producto);
        for (String id : ids) {
            String valor = limpiar(parametros.get(prefijo + id));
            if ("BRAND".equals(id)) {
                producto.setMercadoLibreMarca(valor); adicionales.remove(id);
            } else if ("MODEL".equals(id)) {
                producto.setMercadoLibreModelo(valor); adicionales.remove(id);
            } else if ("GARMENT_TYPE".equals(id)) {
                producto.setMercadoLibreTipoPrenda(valor); adicionales.remove(id);
            } else if (valor == null) {
                adicionales.remove(id);
            } else {
                Map<String, Object> atributo = new LinkedHashMap<>();
                atributo.put("id", id);
                atributo.put("value_name", valor);
                adicionales.put(id, atributo);
            }
        }
        try {
            producto.setMercadoLibreAtributosJson(
                    objectMapper.writeValueAsString(adicionales.values()));
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "No se pudieron guardar los atributos generales", e);
        }
        return ids;
    }

    private void actualizarAtributosDeVariantes(
            List<ProductoVariante> variantes,
            Map<String, String> parametros,
            Set<String> idsGenerales) {
        if (parametros == null) return;
        for (ProductoVariante variante : variantes) {
            Map<String, String> atributos = valoresAtributosVariante(variante);
            atributos.keySet().removeAll(idsGenerales);
            String prefijo = "ml_variante_" + variante.getId() + "_";
            parametros.forEach((clave, valor) -> {
                if (!clave.startsWith(prefijo)) return;
                String id = clave.substring(prefijo.length());
                if (valor == null || valor.isBlank()) atributos.remove(id);
                else atributos.put(id, valor.trim());
            });
            variante.setTalle(atributos.get("SIZE"));
            variante.setColor(atributos.get("COLOR"));
            try {
                variante.setMercadoLibreAtributosJson(
                        objectMapper.writeValueAsString(atributos));
            } catch (Exception e) {
                throw new IllegalArgumentException(
                        "No se pudieron guardar los atributos de la variante "
                                + variante.getNombreMostrar(), e);
            }
        }
    }

    private Map<String, Map<String, Object>> leerAtributosProductoPayload(
            Producto producto) {
        Map<String, Map<String, Object>> resultado = new LinkedHashMap<>();
        String json = producto.getMercadoLibreAtributosJson();
        if (!tieneTexto(json)) return resultado;
        try {
            List<Map<String, Object>> lista = objectMapper.readValue(
                    json, new TypeReference<>() {});
            for (Map<String, Object> atributo : lista) {
                Object id = atributo.get("id");
                if (id != null) resultado.put(
                        id.toString(), new LinkedHashMap<>(atributo));
            }
            return resultado;
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Los atributos generales guardados no son válidos", e);
        }
    }

    private void agregarValor(Map<String, String> valores, String id, String valor) {
        if (tieneTexto(valor)) valores.put(id, valor.trim());
    }

    private <T> T valor(List<T> valores, int indice) {
        return valores != null && indice < valores.size() ? valores.get(indice) : null;
    }

    private void agregarSiTiene(Set<String> ids, String id, String valor) {
        if (tieneTexto(valor)) ids.add(id);
    }

    private boolean precioPositivo(BigDecimal precio) {
        return precio != null && precio.signum() > 0;
    }

    private boolean tieneTexto(String valor) {
        return valor != null && !valor.isBlank();
    }

    private String limpiar(String valor) {
        return tieneTexto(valor) ? valor.trim() : null;
    }

    private String mensaje(RuntimeException e) {
        return e.getMessage() == null || e.getMessage().isBlank()
                ? e.getClass().getSimpleName() : e.getMessage();
    }
}

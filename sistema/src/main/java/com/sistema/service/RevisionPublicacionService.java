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
        ConsultasMercadoLibre consultas = new ConsultasMercadoLibre(
                System.nanoTime() + TIEMPO_MAXIMO_CONSULTAS_ML_MS * 1_000_000L);
        List<RevisionProductoPublicacionDto> resultado = new ArrayList<>();
        for (Long id : ids) {
            Producto producto = productosPorId.get(id);
            if (producto != null) {
                resultado.add(revisar(producto,
                        variantesPorProducto.getOrDefault(id, List.of()),
                        revisarMercadoLibre, consultas));
            }
        }
        return resultado;
    }

    private RevisionProductoPublicacionDto revisar(
            Producto producto,
            List<ProductoVariante> variantes,
            boolean revisarMercadoLibre,
            ConsultasMercadoLibre consultas) {
        LinkedHashSet<String> faltantes = new LinkedHashSet<>();
        LinkedHashSet<String> atributosFaltantes = new LinkedHashSet<>();
        LinkedHashSet<String> atributosObligatorios = new LinkedHashSet<>();

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
                atributosObligatorios, consultas);
        return new RevisionProductoPublicacionDto(
                producto, variantes, List.copyOf(faltantes),
                List.copyOf(atributosFaltantes),
                List.copyOf(atributosObligatorios));
    }

    private void revisarMercadoLibre(
            Producto producto,
            List<ProductoVariante> variantes,
            Set<String> faltantes,
            Set<String> atributosFaltantes,
            Set<String> atributosObligatorios,
            ConsultasMercadoLibre consultas) {
        if (!tieneTexto(producto.getMercadoLibreCategoriaId())) {
            faltantes.add("Categoría de Mercado Libre");
            return;
        }
        try {
            ConsultaAtributos consulta = consultas.obtener(
                    producto.getMercadoLibreCategoriaId());
            if (consulta.error() != null) throw consulta.error();
            var resultado = consulta.resultado();
            boolean tieneFoto = producto.tieneFoto()
                    || variantes.stream().anyMatch(ProductoVariante::tieneFoto);
            if (!tieneFoto) faltantes.add("Foto");
            if (resultado == null) return;
            Set<String> atributosProducto = atributosProducto(producto);
            List<Set<String>> atributosVariantes = variantes.stream()
                    .map(this::atributosVariante).toList();
            for (AtributoVarianteMl atributo : resultado.atributos()) {
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

    private final class ConsultasMercadoLibre {
        private final long limiteNanos;
        private final Map<String, ConsultaAtributos> porCategoria =
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
    }

    private record ConsultaAtributos(
            MercadoLibreAtributosVarianteService.Resultado resultado,
            RuntimeException error) {
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
            List<BigDecimal> preciosVariantes) {
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

        List<ProductoVariante> variantes = varianteRepository
                .findByProductoIdOrderByNombreAsc(productoId);
        if (variantes.isEmpty()) {
            producto.setCantidad(Math.max(Optional.ofNullable(stock).orElse(0), 0));
            producto.setPrecioContado(precio);
        } else {
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

package com.sistema.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sistema.dto.ProductoCanalImportado;
import com.sistema.dto.ResultadoImportacionCanal;
import com.sistema.dto.VarianteCanalImportada;
import com.sistema.dto.AtributoVarianteMl;
import com.sistema.model.*;
import com.sistema.repository.ProductoRepository;
import com.sistema.repository.ProductoVarianteRepository;
import com.sistema.repository.PublicacionCanalRepository;
import com.sistema.service.canal.ImportadorCanal;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ImportacionCanalService {
    private final ProductoRepository productoRepository;
    private final ProductoService productoService;
    private final PublicacionCanalRepository publicacionRepository;
    private final Map<CanalVenta, ImportadorCanal> importadores;
    private final ProductoVarianteRepository varianteRepository;
    private final ProductoVarianteService varianteService;
    private final ObjectMapper objectMapper;
    private final MercadoLibreAtributosVarianteService atributosVarianteMlService;

    @Autowired
    public ImportacionCanalService(ProductoRepository productoRepository, ProductoService productoService,
                                   PublicacionCanalRepository publicacionRepository, List<ImportadorCanal> importadores,
                                   ProductoVarianteRepository varianteRepository, ProductoVarianteService varianteService,
                                   ObjectMapper objectMapper,
                                   MercadoLibreAtributosVarianteService atributosVarianteMlService) {
        this.productoRepository = productoRepository;
        this.productoService = productoService;
        this.publicacionRepository = publicacionRepository;
        this.importadores = importadores.stream().collect(Collectors.toMap(ImportadorCanal::canal, i -> i));
        this.varianteRepository = varianteRepository;
        this.varianteService = varianteService;
        this.objectMapper = objectMapper;
        this.atributosVarianteMlService = atributosVarianteMlService;
    }

    ImportacionCanalService(ProductoRepository productoRepository, ProductoService productoService,
                            PublicacionCanalRepository publicacionRepository, List<ImportadorCanal> importadores,
                            ProductoVarianteRepository varianteRepository, ProductoVarianteService varianteService,
                            ObjectMapper objectMapper) {
        this(productoRepository, productoService, publicacionRepository, importadores, varianteRepository,
                varianteService, objectMapper, null);
    }

    public boolean configurado(CanalVenta canal) {
        ImportadorCanal importador = importadores.get(canal);
        return importador != null && importador.configurado();
    }

    public ResultadoImportacionCanal importar(CanalVenta canal) {
        ImportadorCanal importador = importadores.get(canal);
        if (importador == null || !importador.configurado()) throw new IllegalStateException(canal.getDescripcion() + " no está configurado");
        ResultadoImportacionCanal resultado = new ResultadoImportacionCanal();
        for (ProductoCanalImportado dato : importador.obtenerProductos()) {
            try { guardar(canal, dato, resultado); }
            catch (Exception e) {
                String referencia = dato.sku() == null || dato.sku().isBlank() ? dato.idExterno() : dato.sku();
                resultado.error(referencia + ": " + e.getMessage());
            }
        }
        return resultado;
    }

    private void guardar(CanalVenta canal, ProductoCanalImportado dato, ResultadoImportacionCanal resultado) {
        validarStockVariantesMercadoLibre(canal, dato);
        Set<Producto> productosSeparados = new LinkedHashSet<>();
        Optional<PublicacionCanal> mapeo = publicacionRepository.findByCanalAndIdExterno(canal, dato.idExterno());
        Optional<Producto> encontrado = mapeo.map(PublicacionCanal::getProducto);
        mapeo.map(PublicacionCanal::getProducto).ifPresent(productosSeparados::add);
        String familyId = canal == CanalVenta.MERCADO_LIBRE ? texto(dato.datosCanal(), "familyId") : null;
        if (encontrado.isEmpty() && familyId != null && !familyId.isBlank()) {
            encontrado = productoRepository.findByMercadoLibreFamilyId(familyId);
        }
        if (canal == CanalVenta.MERCADO_LIBRE && dato.variantes() != null) {
            for (VarianteCanalImportada variante : dato.variantes()) {
                if (!variante.itemMercadoLibre()) continue;
                Optional<PublicacionCanal> publicacion = publicacionRepository
                        .findByCanalAndIdExterno(canal, variante.idExterno());
                if (publicacion.isPresent()) {
                    productosSeparados.add(publicacion.get().getProducto());
                    if (encontrado.isEmpty()) encontrado = Optional.of(publicacion.get().getProducto());
                }
            }
        }
        if (encontrado.isEmpty() && dato.sku() != null && !dato.sku().isBlank()) encontrado = productoRepository.findBySkuIgnoreCase(dato.sku());
        boolean nuevo = encontrado.isEmpty();
        Producto producto = encontrado.orElseGet(Producto::new);
        producto.setDescripcion(dato.descripcion());
        if (dato.sku() != null && !dato.sku().isBlank()) producto.setSku(dato.sku());
        producto.setCantidad(Optional.ofNullable(dato.cantidad()).orElse(0));
        if (dato.precio() != null) {
            producto.setPrecioContado(dato.precio());
            producto.setPrecioTarjeta(dato.precio());
            producto.setPrecioCuentaCorriente(dato.precio());
        }
        if (producto.getTipoIva() == null) producto.setTipoIva(TipoIva.IVA_21);
        if (dato.fotoUrl() != null && !dato.fotoUrl().isBlank()) {
            producto.setFotoContenido(null);
            producto.setFotoNombre(null);
            producto.setFotoTipoContenido(null);
            producto.setFotoUrlExterna(dato.fotoUrl());
        }
        if (canal == CanalVenta.MERCADO_LIBRE) {
            producto.setMercadoLibreId(dato.idExterno());
            if (familyId != null && !familyId.isBlank()) producto.setMercadoLibreFamilyId(familyId);
            producto.setMercadoLibreCategoriaId(dato.mercadoLibreCategoriaId());
            aplicarDatosMercadoLibre(producto, dato.datosCanal());
        }
        productoService.saveProducto(producto);
        List<VarianteCanalImportada> variantes = dato.variantes();
        if (canal == CanalVenta.MERCADO_LIBRE && (variantes == null || variantes.isEmpty())) {
            variantes = crearPresentacionSimple(producto, dato);
        }
        importarVariantes(canal, producto, variantes);
        PublicacionCanal publicacion = mapeo.orElseGet(() -> publicacionRepository
                .findByProductoIdAndCanal(producto.getId(), canal).orElseGet(PublicacionCanal::new));
        publicacion.setProducto(producto);
        publicacion.setCanal(canal);
        publicacion.setIdExterno(dato.idExterno());
        publicacion.setEstado(EstadoPublicacion.IMPORTADO);
        publicacion.setUltimoError(null);
        publicacion.setFechaActualizacion(LocalDateTime.now());
        publicacionRepository.save(publicacion);
        consolidarProductosSeparados(producto, productosSeparados);
        if (nuevo) resultado.creado(producto.getId()); else resultado.actualizado(producto.getId());
    }

    private void validarStockVariantesMercadoLibre(CanalVenta canal, ProductoCanalImportado dato) {
        if (canal != CanalVenta.MERCADO_LIBRE || dato.variantes() == null || dato.variantes().isEmpty()
                || Optional.ofNullable(dato.cantidad()).orElse(0) <= 0) {
            return;
        }
        int stockVariantes = dato.variantes().stream()
                .map(VarianteCanalImportada::stock)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
        if (stockVariantes <= 0) {
            throw new IllegalStateException("Mercado Libre informó stock para el producto, pero no para sus "
                    + "presentaciones. No se importó con stock cero: vuelva a sincronizar para consultar "
                    + "el inventario de cada talle.");
        }
    }

    private void aplicarDatosMercadoLibre(Producto producto, Map<String, Object> datos) {
        if (datos == null || datos.isEmpty()) return;
        Object officialStoreId = datos.get("officialStoreId");
        producto.setMercadoLibreOfficialStoreId(officialStoreId instanceof Number numero ? numero.longValue() : null);
        producto.setMercadoLibreMarca(texto(datos, "marca"));
        producto.setMercadoLibreModelo(texto(datos, "modelo"));
        producto.setMercadoLibreGtin(texto(datos, "gtin"));
        producto.setMercadoLibreGuiaTallesId(texto(datos, "guiaTallesId"));
        producto.setMercadoLibreGuiaTallesFilaId(texto(datos, "guiaTallesFilaId"));
        producto.setMercadoLibreGenero(texto(datos, "genero"));
        producto.setMercadoLibreGarantiaTipo(texto(datos, "garantiaTipo"));
        producto.setMercadoLibreGarantiaTiempo(texto(datos, "garantiaTiempo"));
        producto.setMercadoLibreVideoId(texto(datos, "videoId"));
        producto.setMercadoLibreCondicion(texto(datos, "condicion"));
        producto.setMercadoLibreEstado(texto(datos, "estado"));
        producto.setMercadoLibreListingTypeId(texto(datos, "listingTypeId"));
        producto.setMercadoLibreModoEnvio(texto(datos, "modoEnvio"));
        producto.setMercadoLibreDescripcion(texto(datos, "descripcion"));
        producto.setMercadoLibreAtributosJson(texto(datos, "atributosJson"));
        producto.setFotosUrlsExternas(texto(datos, "fotosUrlsExternas"));
        Object envioGratis = datos.get("envioGratis");
        producto.setMercadoLibreEnvioGratis(envioGratis instanceof Boolean valor ? valor : null);
        Object retiroPersonal = datos.get("retiroPersonal");
        producto.setMercadoLibreRetiroPersonal(retiroPersonal instanceof Boolean valor ? valor : null);
        String tiempoDisponibilidad = texto(datos, "tiempoDisponibilidad");
        if (tiempoDisponibilidad != null) {
            String numero = tiempoDisponibilidad.replaceAll("[^0-9]", "");
            producto.setMercadoLibreTiempoDisponibilidad(numero.isBlank() ? null : Integer.valueOf(numero));
        }
    }

    private String texto(Map<String, Object> datos, String clave) {
        Object valor = datos.get(clave);
        return valor == null ? null : valor.toString();
    }

    private void importarVariantes(CanalVenta canal, Producto producto, List<VarianteCanalImportada> datos) {
        if (datos == null || datos.isEmpty()) return;
        for (VarianteCanalImportada dato : datos) {
            Optional<ProductoVariante> encontrada = Optional.empty();
            if (dato.idExterno() != null && !dato.idExterno().isBlank()) {
                encontrada = switch (canal) {
                    case MERCADO_LIBRE -> dato.itemMercadoLibre()
                            ? varianteRepository.findByMercadoLibreItemId(dato.idExterno())
                            : varianteRepository.findByMercadoLibreVariationId(dato.idExterno());
                    case WOOCOMMERCE -> varianteRepository.findByWooCommerceVariationId(dato.idExterno());
                    case TIENDANUBE -> varianteRepository.findByTiendaNubeVariationId(dato.idExterno());
                };
            }
            if (encontrada.isEmpty() && dato.sku() != null && !dato.sku().isBlank()) {
                encontrada = varianteRepository.findBySkuIgnoreCase(dato.sku())
                        .filter(variante -> variante.getProducto() != null
                                && Objects.equals(variante.getProducto().getId(), producto.getId()));
            }
            ProductoVariante variante = encontrada.orElseGet(ProductoVariante::new);
            String skuImportado = normalizarSkuImportado(dato.sku());
            if (!skuImportado.isBlank()) variante.setSku(skuImportado);
            else if (normalizarSkuImportado(variante.getSku()).isBlank()) variante.setSku(null);
            variante.setNombre(dato.nombre()); variante.setTalle(dato.talle()); variante.setColor(dato.color());
            variante.setMercadoLibreGtin(dato.gtin());
            if (dato.fotoUrl() != null && !dato.fotoUrl().isBlank()) {
                variante.setFotoContenido(null);
                variante.setFotoNombre(null);
                variante.setFotoTipoContenido(null);
                variante.setFotoUrlExterna(dato.fotoUrl().trim());
            }
            if (dato.atributos() != null && !dato.atributos().isEmpty()) {
                try { variante.setMercadoLibreAtributosJson(objectMapper.writeValueAsString(dato.atributos())); }
                catch (Exception e) { throw new IllegalArgumentException("No se pudieron guardar los atributos de la variante", e); }
            }
            variante.setStock(Optional.ofNullable(dato.stock()).orElse(0));
            if (dato.precio() != null) {
                variante.setPrecioContado(dato.precio()); variante.setPrecioTarjeta(dato.precio());
                variante.setPrecioCuentaCorriente(dato.precio());
            }
            switch (canal) {
                case MERCADO_LIBRE -> {
                    if (dato.itemMercadoLibre()) {
                        variante.setMercadoLibreItemId(dato.idExterno());
                        variante.setMercadoLibreVariationId(null);
                    } else {
                        variante.setMercadoLibreVariationId(dato.idExterno());
                    }
                    variante.setMercadoLibreProductNumber(dato.productNumber());
                }
                case WOOCOMMERCE -> variante.setWooCommerceVariationId(dato.idExterno());
                case TIENDANUBE -> variante.setTiendaNubeVariationId(dato.idExterno());
            }
            varianteService.guardarImportada(producto, variante);
        }
    }

    private String normalizarSkuImportado(String sku) {
        if (sku == null) return "";
        String normalizado = sku.trim();
        return normalizado.isBlank() || "null".equalsIgnoreCase(normalizado) ? "" : normalizado;
    }

    private List<VarianteCanalImportada> crearPresentacionSimple(Producto producto,
                                                                  ProductoCanalImportado dato) {
        if (atributosVarianteMlService == null || dato.datosCanal() == null) return List.of();
        Object atributosCrudos = dato.datosCanal().get("atributosItem");
        if (!(atributosCrudos instanceof Map<?, ?> mapaCrudo)) return List.of();
        Map<String, String> valores = new LinkedHashMap<>();
        mapaCrudo.forEach((id, valor) -> {
            if (id != null && valor != null && !valor.toString().isBlank()) {
                valores.put(id.toString(), valor.toString());
            }
        });
        if (valores.isEmpty()) return List.of();
        try {
            Set<String> permitidos = atributosVarianteMlService.obtener(producto).atributos().stream()
                    .map(AtributoVarianteMl::id)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            Map<String, String> atributos = new LinkedHashMap<>();
            permitidos.forEach(id -> {
                String valor = valores.get(id);
                if (valor != null && !valor.isBlank()) atributos.put(id, valor);
            });
            if (atributos.isEmpty()) return List.of();
            return List.of(new VarianteCanalImportada(
                    dato.idExterno(), dato.sku(), "Presentación única",
                    atributos.getOrDefault("SIZE", ""), atributos.getOrDefault("COLOR", ""),
                    dato.cantidad(), dato.precio(), null, valores.get("PRODUCT_NUMBER"), valores.get("GTIN"),
                    atributos, dato.fotoUrl(), true));
        } catch (RuntimeException ignored) {
            // Si Mercado Libre no permite consultar la ficha técnica, se conserva el producto simple.
            return List.of();
        }
    }

    private void consolidarProductosSeparados(Producto principal, Set<Producto> candidatos) {
        for (Producto candidato : candidatos) {
            if (candidato.getId() == null || candidato.getId().equals(principal.getId())) continue;
            if (varianteRepository.existsByProductoId(candidato.getId())) continue;
            try {
                productoService.deleteProducto(candidato.getId());
            } catch (RuntimeException ignored) {
                // Si tiene ventas o movimientos se conserva: nunca se borra historial para consolidar una familia.
            }
        }
    }
}

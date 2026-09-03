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
import com.sistema.service.canal.MercadoLibreImportador;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.function.BooleanSupplier;

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
        return importar(canal, obtenerProductos(canal));
    }

    public List<ProductoCanalImportado> obtenerProductos(CanalVenta canal) {
        return filtrarConStock(importadorConfigurado(canal).obtenerProductos());
    }

    public List<ProductoCanalImportado> obtenerProductos(
            CanalVenta canal, boolean incluirInactivas) {
        return obtenerProductos(canal, incluirInactivas, () -> false);
    }

    public List<ProductoCanalImportado> obtenerProductos(
            CanalVenta canal, boolean incluirInactivas,
            BooleanSupplier cancelacionSolicitada) {
        ImportadorCanal importador = importadorConfigurado(canal);
        return filtrarConStock(importador.obtenerProductos(
                incluirInactivas, cancelacionSolicitada));
    }

    public List<ProductoCanalImportado> obtenerUltimasPublicacionesMercadoLibre(
            int cantidad, String categoria, boolean incluirInactivas,
            BooleanSupplier cancelacionSolicitada) {
        ImportadorCanal importador = importadorConfigurado(CanalVenta.MERCADO_LIBRE);
        if (!(importador instanceof MercadoLibreImportador mercadoLibre)) {
            throw new IllegalStateException("El importador de Mercado Libre no está disponible");
        }
        return filtrarConStock(mercadoLibre.obtenerUltimasPublicaciones(
                cantidad, categoria, incluirInactivas, cancelacionSolicitada));
    }

    public ResultadoImportacionCanal importar(CanalVenta canal,
                                              Collection<ProductoCanalImportado> productos) {
        return importar(canal, productos, () -> false);
    }

    public ResultadoImportacionCanal importar(CanalVenta canal,
                                              Collection<ProductoCanalImportado> productos,
                                              BooleanSupplier cancelacionSolicitada) {
        importadorConfigurado(canal);
        ResultadoImportacionCanal resultado = new ResultadoImportacionCanal();
        if (productos == null) return resultado;
        List<ProductoCanalImportado> productosConStock = filtrarConStock(productos);
        resultado.recibidos(productosConStock.size());
        for (ProductoCanalImportado dato : productosConStock) {
            if (cancelacionSolicitada.getAsBoolean()) return resultado;
            try { guardar(canal, dato, resultado); }
            catch (Exception e) {
                String referencia = dato.sku() == null || dato.sku().isBlank() ? dato.idExterno() : dato.sku();
                resultado.error(referencia + ": " + e.getMessage());
            }
        }
        return resultado;
    }

    private List<ProductoCanalImportado> filtrarConStock(
            Collection<ProductoCanalImportado> productos) {
        if (productos == null || productos.isEmpty()) return List.of();
        return productos.stream()
                .filter(Objects::nonNull)
                .filter(this::tieneStockPositivo)
                .toList();
    }

    private boolean tieneStockPositivo(ProductoCanalImportado producto) {
        if (Optional.ofNullable(producto.cantidad()).orElse(0) > 0) return true;
        if (producto.variantes() == null) return false;
        return producto.variantes().stream()
                .filter(Objects::nonNull)
                .anyMatch(variante -> Optional.ofNullable(variante.stock()).orElse(0) > 0);
    }

    private ImportadorCanal importadorConfigurado(CanalVenta canal) {
        if (canal == null) throw new IllegalArgumentException("Falta el canal de origen");
        ImportadorCanal importador = importadores.get(canal);
        if (importador == null || !importador.configurado()) {
            throw new IllegalStateException(canal.getDescripcion() + " no está configurado");
        }
        return importador;
    }

    private void guardar(CanalVenta canal, ProductoCanalImportado dato, ResultadoImportacionCanal resultado) {
        validarStockVariantesMercadoLibre(canal, dato);
        Optional<ProductoVariante> varianteExistente = buscarVarianteExistenteParaProductoIndividual(canal, dato);
        if (varianteExistente.isPresent()) {
            guardarComoVarianteExistente(canal, dato, varianteExistente.get(), resultado);
            return;
        }
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
        if (encontrado.isEmpty() && dato.sku() != null && !dato.sku().isBlank()) {
            encontrado = productoRepository.findBySkuIgnoreCase(dato.sku())
                    .filter(producto -> puedeVincularPorSku(canal, dato.idExterno(), producto));
        }
        boolean nuevo = encontrado.isEmpty();
        Producto producto = encontrado.orElseGet(Producto::new);
        producto.setDescripcion(dato.descripcion());
        switch (canal) {
            case MERCADO_LIBRE -> producto.setMercadoLibreTitulo(dato.descripcion());
            case WOOCOMMERCE -> producto.setWooCommerceTitulo(dato.descripcion());
            case TIENDANUBE -> producto.setTiendaNubeTitulo(dato.descripcion());
        }
        asignarSkuImportadoSiDisponible(producto, dato.sku());
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
            producto.setMercadoLibreCategoriaFijada(
                    tieneTexto(dato.mercadoLibreCategoriaId()));
            aplicarDatosMercadoLibre(producto, dato.datosCanal());
        } else if (canal == CanalVenta.WOOCOMMERCE
                && dato.datosCanal() != null) {
            producto.setWooCommerceDescripcion(
                    texto(dato.datosCanal(), "descripcion"));
            producto.setWooCommerceAtributosJson(
                    texto(dato.datosCanal(), "atributosJson"));
        } else if (canal == CanalVenta.TIENDANUBE
                && dato.datosCanal() != null) {
            String categoriasOrigen = nombresCategorias(dato.datosCanal().get("categorias"));
            if (tieneTexto(categoriasOrigen)) producto.setCategoriaOrigen(categoriasOrigen);
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

    private Optional<ProductoVariante> buscarVarianteExistenteParaProductoIndividual(
            CanalVenta canal, ProductoCanalImportado dato) {
        List<VarianteCanalImportada> variantes = dato.variantes() == null
                ? List.of()
                : dato.variantes().stream().filter(Objects::nonNull).toList();
        if (variantes.size() > 1) return Optional.empty();

        String sku = variantes.isEmpty() ? dato.sku() : variantes.get(0).sku();
        if (normalizarSkuImportado(sku).isBlank()) sku = dato.sku();
        if (normalizarSkuImportado(sku).isBlank()) return Optional.empty();
        return varianteRepository.findBySkuIgnoreCase(sku.trim())
                .filter(variante -> variante.getProducto() != null)
                .filter(variante -> puedeVincularPorSku(
                        canal, dato.idExterno(), variante.getProducto()));
    }

    private boolean puedeVincularPorSku(CanalVenta canal, String idExterno, Producto producto) {
        if (canal == CanalVenta.MERCADO_LIBRE || producto == null || producto.getId() == null) {
            return true;
        }
        return publicacionRepository.findByProductoIdAndCanal(producto.getId(), canal)
                .map(publicacion -> Objects.equals(
                        normalizarIdExterno(publicacion.getIdExterno()), normalizarIdExterno(idExterno)))
                .orElse(true);
    }

    private String normalizarIdExterno(String id) {
        return id == null ? "" : id.trim();
    }

    private void guardarComoVarianteExistente(CanalVenta canal, ProductoCanalImportado dato,
                                               ProductoVariante variante,
                                               ResultadoImportacionCanal resultado) {
        Producto producto = variante.getProducto();
        if (producto == null || producto.getId() == null) {
            throw new IllegalStateException("La variante con SKU " + variante.getSku()
                    + " no tiene un producto asociado");
        }

        VarianteCanalImportada detalle = dato.variantes() == null || dato.variantes().isEmpty()
                ? null : dato.variantes().get(0);
        actualizarVarianteDesdeCanal(canal, dato, detalle, variante);
        switch (canal) {
            case MERCADO_LIBRE -> producto.setMercadoLibreTitulo(dato.descripcion());
            case WOOCOMMERCE -> producto.setWooCommerceTitulo(dato.descripcion());
            case TIENDANUBE -> producto.setTiendaNubeTitulo(dato.descripcion());
        }
        if (canal == CanalVenta.MERCADO_LIBRE) {
            if (producto.getMercadoLibreId() == null || producto.getMercadoLibreId().isBlank()) {
                producto.setMercadoLibreId(dato.idExterno());
            }
            if (producto.getMercadoLibreCategoriaId() == null
                    || producto.getMercadoLibreCategoriaId().isBlank()) {
                producto.setMercadoLibreCategoriaId(dato.mercadoLibreCategoriaId());
                producto.setMercadoLibreCategoriaFijada(
                        tieneTexto(dato.mercadoLibreCategoriaId()));
            }
            aplicarDatosMercadoLibre(producto, dato.datosCanal());
        }
        productoService.saveProducto(producto);
        varianteService.guardarImportada(producto, variante);
        consolidarMapeoDeProductoIndividual(canal, dato.idExterno(), producto);
        resultado.actualizado(producto.getId());
    }

    private void actualizarVarianteDesdeCanal(CanalVenta canal, ProductoCanalImportado productoRemoto,
                                               VarianteCanalImportada detalle,
                                               ProductoVariante variante) {
        Integer stock = detalle == null ? productoRemoto.cantidad() : detalle.stock();
        variante.setStock(Optional.ofNullable(stock).orElse(0));
        java.math.BigDecimal precio = detalle == null ? productoRemoto.precio() : detalle.precio();
        if (precio != null) {
            variante.setPrecioContado(precio);
            variante.setPrecioTarjeta(precio);
            variante.setPrecioCuentaCorriente(precio);
        }

        String foto = detalle == null ? productoRemoto.fotoUrl() : detalle.fotoUrl();
        if (foto != null && !foto.isBlank()) {
            variante.setFotoContenido(null);
            variante.setFotoNombre(null);
            variante.setFotoTipoContenido(null);
            variante.setFotoUrlExterna(foto.trim());
        }
        if (detalle != null) {
            if (detalle.nombre() != null && !detalle.nombre().isBlank()) variante.setNombre(detalle.nombre());
            if (detalle.talle() != null && !detalle.talle().isBlank()) variante.setTalle(detalle.talle());
            if (detalle.color() != null && !detalle.color().isBlank()) variante.setColor(detalle.color());
            if (detalle.codigoBarras() != null && !detalle.codigoBarras().isBlank()) {
                variante.setCodigoBarras(detalle.codigoBarras());
            }
            aplicarDatosEspecificosVariante(canal, variante, detalle);
        }

        String idExterno = detalle != null && detalle.idExterno() != null
                && !detalle.idExterno().isBlank() ? detalle.idExterno() : productoRemoto.idExterno();
        switch (canal) {
            case MERCADO_LIBRE -> {
                if (detalle == null || detalle.itemMercadoLibre()) {
                    variante.setMercadoLibreItemId(idExterno);
                } else {
                    variante.setMercadoLibreVariationId(idExterno);
                }
                if (detalle != null) variante.setMercadoLibreProductNumber(detalle.productNumber());
            }
            case WOOCOMMERCE -> {
                if (detalle != null) variante.setWooCommerceVariationId(idExterno);
            }
            case TIENDANUBE -> {
                if (detalle != null) variante.setTiendaNubeVariationId(idExterno);
            }
        }
    }

    private void consolidarMapeoDeProductoIndividual(CanalVenta canal, String idExterno,
                                                     Producto productoPrincipal) {
        Optional<PublicacionCanal> mapeoExterno = publicacionRepository
                .findByCanalAndIdExterno(canal, idExterno);
        Producto duplicado = mapeoExterno.map(PublicacionCanal::getProducto)
                .filter(producto -> !Objects.equals(producto.getId(), productoPrincipal.getId()))
                .orElse(null);
        if (duplicado != null) publicacionRepository.delete(mapeoExterno.get());

        PublicacionCanal publicacion = publicacionRepository
                .findByProductoIdAndCanal(productoPrincipal.getId(), canal)
                .orElseGet(PublicacionCanal::new);
        publicacion.setProducto(productoPrincipal);
        publicacion.setCanal(canal);
        if (publicacion.getIdExterno() == null || publicacion.getIdExterno().isBlank()
                || canal != CanalVenta.MERCADO_LIBRE) {
            publicacion.setIdExterno(idExterno);
        }
        publicacion.setEstado(EstadoPublicacion.IMPORTADO);
        publicacion.setUltimoError(null);
        publicacion.setFechaActualizacion(LocalDateTime.now());
        publicacionRepository.save(publicacion);

        if (duplicado != null && !varianteRepository.existsByProductoId(duplicado.getId())) {
            try {
                productoService.deleteProducto(duplicado.getId());
            } catch (RuntimeException ignored) {
                // Si el duplicado tiene movimientos se conserva su historial, pero ya no se usa para sincronizar.
            }
        }
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
        if (datos == null) return null;
        Object valor = datos.get(clave);
        return valor == null ? null : valor.toString();
    }

    private String nombresCategorias(Object categorias) {
        if (!(categorias instanceof Collection<?> lista)) return null;
        return lista.stream().map(categoria -> {
                    if (categoria instanceof Map<?, ?> mapa) {
                        Object nombre = mapa.get("nombre");
                        return nombre == null ? "" : nombre.toString().trim();
                    }
                    return "";
                })
                .filter(this::tieneTexto)
                .distinct()
                .collect(Collectors.joining(" / "));
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
            if (dato.fotoUrl() != null && !dato.fotoUrl().isBlank()) {
                variante.setFotoContenido(null);
                variante.setFotoNombre(null);
                variante.setFotoTipoContenido(null);
                variante.setFotoUrlExterna(dato.fotoUrl().trim());
            }
            aplicarDatosEspecificosVariante(canal, variante, dato);
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

    private void aplicarDatosEspecificosVariante(
            CanalVenta canal, ProductoVariante variante,
            VarianteCanalImportada dato) {
        if (dato == null) return;
        if (canal == CanalVenta.MERCADO_LIBRE) {
            variante.setMercadoLibreGtin(dato.gtin());
        }
        if (dato.atributos() == null || dato.atributos().isEmpty()) return;
        final String json;
        try {
            json = objectMapper.writeValueAsString(dato.atributos());
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "No se pudieron guardar los atributos de la variante", e);
        }
        switch (canal) {
            case MERCADO_LIBRE -> variante.setMercadoLibreAtributosJson(json);
            case WOOCOMMERCE -> variante.setWooCommerceAtributosJson(json);
            case TIENDANUBE -> variante.setTiendaNubeAtributosJson(json);
        }
    }

    private String normalizarSkuImportado(String sku) {
        if (sku == null) return "";
        String normalizado = sku.trim();
        return normalizado.isBlank() || "null".equalsIgnoreCase(normalizado) ? "" : normalizado;
    }

    private void asignarSkuImportadoSiDisponible(Producto producto, String sku) {
        String normalizado = normalizarSkuImportado(sku);
        if (normalizado.isBlank()) return;
        Optional<Producto> propietario = productoRepository.findBySkuIgnoreCase(normalizado);
        boolean disponible = propietario.isEmpty() || (producto.getId() != null
                && Objects.equals(propietario.get().getId(), producto.getId()));
        if (disponible) producto.setSku(normalizado);
    }

    private boolean tieneTexto(String valor) {
        return valor != null && !valor.isBlank();
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

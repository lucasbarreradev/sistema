package com.sistema.service;

import com.sistema.model.Producto;
import com.sistema.model.ProductoVariante;
import com.sistema.dto.ProductoOpcionDto;
import com.sistema.dto.ProductoFotoProjection;
import com.sistema.dto.ProductoListadoDto;
import com.sistema.model.Proveedor;
import com.sistema.repository.MovimientoInventarioRepository;
import com.sistema.repository.ProductoRepository;
import com.sistema.repository.ProveedorRepository;
import com.sistema.repository.PublicacionCanalRepository;
import com.sistema.repository.ProductoVarianteRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Service
public class ProductoService {
    private final ProductoRepository productoRepo;
    private final ProveedorRepository proveedorRepository;
    private final MovimientoInventarioRepository movimientoRepo;
    private final PresupuestoService presupuestoService;
    private final PublicacionCanalRepository publicacionCanalRepository;
    private final ProductoVarianteRepository productoVarianteRepository;

    public ProductoService(ProductoRepository productoRepo,
                            ProveedorRepository proveedorRepository,
                           MovimientoInventarioRepository movimientoRepo,
                           PresupuestoService presupuestoService,
                           PublicacionCanalRepository publicacionCanalRepository,
                           ProductoVarianteRepository productoVarianteRepository) {
        this.productoRepo = productoRepo;
        this.proveedorRepository = proveedorRepository;
        this.movimientoRepo = movimientoRepo;
        this.presupuestoService = presupuestoService;
        this.publicacionCanalRepository = publicacionCanalRepository;
        this.productoVarianteRepository = productoVarianteRepository;
    }

    public List<Producto> getProductos() {
        return productoRepo.findAllByOrderByDescripcionAsc();
    }

    @Transactional(readOnly = true)
    public Page<ProductoListadoDto> getProductosListado(String busqueda, Pageable pageable) {
        String filtro = busqueda == null ? "" : busqueda.trim();
        return productoRepo.buscarPaginaListado(filtro, pageable).map(ProductoListadoDto::new);
    }

    @Transactional(readOnly = true)
    public List<Long> getIdsProductosListado(String busqueda) {
        String filtro = busqueda == null ? "" : busqueda.trim();
        return productoRepo.buscarIdsListado(filtro);
    }

    @Transactional(readOnly = true)
    public Optional<ProductoFotoProjection> getFotoProducto(Long id) {
        return productoRepo.buscarFotoPorId(id);
    }

    public Optional<Producto> getProductoById(Long id) {
        return productoRepo.findById(id);
    }

    public String generarSku(String descripcion) {

        String base = descripcion
                .toUpperCase()
                .replaceAll("[^A-Z]", "");

        String prefijo = base.substring(0, Math.min(4, base.length()));

        long numero = productoRepo.countBySkuPrefix(prefijo) + 1;
        String sku;
        do {
            sku = prefijo + "-" + String.format("%03d", numero++);
        } while (productoRepo.findBySkuIgnoreCase(sku).isPresent());
        return sku;
    }



    public void saveProducto(Producto producto) {

        if (producto.getProveedor() != null
                && producto.getProveedor().getId() != null) {

            Proveedor proveedor = proveedorRepository
                    .findById(producto.getProveedor().getId())
                    .orElse(null);

            producto.setProveedor(proveedor);
        }

        if (producto.getSku() == null || producto.getSku().isEmpty()) {
            producto.setSku(generarSku(producto.getDescripcion()));
        }

        productoRepo.save(producto);
    }

    public void guardarFoto(Producto producto, MultipartFile foto) {
        if (foto == null || foto.isEmpty()) {
            return;
        }
        String tipo = foto.getContentType();
        if (tipo == null || !List.of("image/jpeg", "image/png", "image/webp", "image/gif").contains(tipo.toLowerCase())) {
            throw new IllegalArgumentException("La imagen debe ser JPG, PNG, WebP o GIF");
        }
        if (foto.getSize() > 5 * 1024 * 1024) {
            throw new IllegalArgumentException("La imagen no puede superar los 5 MB");
        }
        try {
            producto.setFotoContenido(foto.getBytes());
            producto.setFotoNombre(foto.getOriginalFilename());
            producto.setFotoTipoContenido(tipo);
            producto.setFotoUrlExterna(null);
        } catch (IOException e) {
            throw new IllegalArgumentException("No se pudo leer la imagen", e);
        }
    }

    public Producto updateProducto(Long id, Producto producto) {

        Producto existente = productoRepo.findById(id)
                .orElseThrow(() -> new RuntimeException(
                        "Producto no encontrado con id: " + id));

        existente.setDescripcion(producto.getDescripcion());
        if (productoVarianteRepository.existsByProductoId(id)) {
            int stockVariantes = productoVarianteRepository.findByProductoIdOrderByNombreAsc(id).stream()
                    .mapToInt(v -> Optional.ofNullable(v.getStock()).orElse(0)).sum();
            existente.setCantidad(stockVariantes);
            existente.setUsaVariantes(true);
        } else {
            existente.setCantidad(producto.getCantidad());
            existente.setUsaVariantes(false);
        }
        existente.setPrecioCompra(producto.getPrecioCompra());
        existente.setPrecioContado(producto.getPrecioContado());
        existente.setPrecioTarjeta(producto.getPrecioTarjeta());
        existente.setPrecioCuentaCorriente(producto.getPrecioCuentaCorriente());
        existente.setTipoIva(producto.getTipoIva());
        existente.setMercadoLibreId(producto.getMercadoLibreId());
        existente.setMercadoLibreCategoriaId(producto.getMercadoLibreCategoriaId());
        existente.setMercadoLibreGuiaTallesId(producto.getMercadoLibreGuiaTallesId());
        existente.setMercadoLibreGuiaTallesFilaId(producto.getMercadoLibreGuiaTallesFilaId());
        existente.setMercadoLibreGenero(producto.getMercadoLibreGenero());
        existente.setMercadoLibreOfficialStoreId(producto.getMercadoLibreOfficialStoreId());
        existente.setMercadoLibreMarca(producto.getMercadoLibreMarca());
        existente.setMercadoLibreModelo(producto.getMercadoLibreModelo());
        existente.setMercadoLibreTipoPrenda(producto.getMercadoLibreTipoPrenda());
        existente.setMercadoLibreGtin(producto.getMercadoLibreGtin());
        existente.setMercadoLibreGarantiaTipo(producto.getMercadoLibreGarantiaTipo());
        existente.setMercadoLibreGarantiaTiempo(producto.getMercadoLibreGarantiaTiempo());
        existente.setMercadoLibreVideoId(producto.getMercadoLibreVideoId());
        existente.setMercadoLibreEnvioGratis(producto.getMercadoLibreEnvioGratis());
        existente.setMercadoLibreRetiroPersonal(producto.getMercadoLibreRetiroPersonal());
        existente.setMercadoLibreModoEnvio(producto.getMercadoLibreModoEnvio());
        existente.setMercadoLibreCondicion(producto.getMercadoLibreCondicion());
        existente.setMercadoLibreEstado(producto.getMercadoLibreEstado());
        existente.setMercadoLibreTiempoDisponibilidad(producto.getMercadoLibreTiempoDisponibilidad());
        existente.setMercadoLibreListingTypeId(producto.getMercadoLibreListingTypeId());
        existente.setMercadoLibreConfiguracionCuotas(producto.getMercadoLibreConfiguracionCuotas());
        existente.setMercadoLibreCargoVenta(producto.getMercadoLibreCargoVenta());
        existente.setMercadoLibreCostoFinanciacion(producto.getMercadoLibreCostoFinanciacion());
        existente.setMercadoLibreDescripcion(producto.getMercadoLibreDescripcion());
        existente.setMercadoLibreAtributosJson(producto.getMercadoLibreAtributosJson());
        existente.setFotosUrlsExternas(producto.getFotosUrlsExternas());
        existente.setFotoUrlExterna(producto.getFotoUrlExterna());
        if (producto.tieneFotoLocal()) {
            existente.setFotoContenido(producto.getFotoContenido());
            existente.setFotoNombre(producto.getFotoNombre());
            existente.setFotoTipoContenido(producto.getFotoTipoContenido());
            existente.setFotoUrlExterna(null);
        }

        // ===== PROVEEDOR =====
        if (producto.getProveedor() != null
                && producto.getProveedor().getId() != null) {

            Proveedor proveedor = proveedorRepository
                    .findById(producto.getProveedor().getId())
                    .orElseThrow(() -> new RuntimeException(
                            "Proveedor no encontrado"));

            existente.setProveedor(proveedor);
        }

        presupuestoService.actualizarPrecioProductoEnPendientes(
                existente.getId(),
                existente.getPrecioContado(),
                existente.getPrecioTarjeta(),
                existente.getPrecioCuentaCorriente());

        return productoRepo.save(existente);

    }

    @Transactional
    public void deleteProducto(Long id) {
        if (movimientoRepo.existsByProductoId(id)) {
            throw new IllegalStateException(
                    "No se puede eliminar el producto porque tiene movimientos"
            );
        }
        publicacionCanalRepository.deleteAllByProductoId(id);
        productoRepo.deleteById(id);
    }


    public List<Producto> buscar(String q) {
        return productoRepo
                .findByDescripcionContainingIgnoreCaseOrSkuContainingIgnoreCase(
                        q, q
                );
    }

    @Transactional(readOnly = true)
    public List<ProductoOpcionDto> buscarOpciones(String q) {
        Map<String, ProductoOpcionDto> opciones = new LinkedHashMap<>();
        for (Producto producto : buscar(q)) agregarOpciones(opciones, producto);
        for (ProductoVariante variante : productoVarianteRepository
                .findBySkuContainingIgnoreCaseOrNombreContainingIgnoreCaseOrCodigoBarrasContainingIgnoreCase(q, q, q)) {
            agregarVariante(opciones, variante);
        }
        return new ArrayList<>(opciones.values());
    }

    private void agregarOpciones(Map<String, ProductoOpcionDto> opciones, Producto producto) {
        List<ProductoVariante> variantes = productoVarianteRepository.findByProductoIdOrderByNombreAsc(producto.getId());
        if (!variantes.isEmpty()) {
            variantes.forEach(v -> agregarVariante(opciones, v));
        } else {
            opciones.put(producto.getId() + "-0", new ProductoOpcionDto(producto.getId(), null,
                    producto.getDescripcion(), producto.getCantidad(), producto.getPrecioCompra(), producto.getPrecioContado(),
                    producto.getPrecioTarjeta(), producto.getPrecioCuentaCorriente()));
        }
    }

    private void agregarVariante(Map<String, ProductoOpcionDto> opciones, ProductoVariante variante) {
        Producto p = variante.getProducto();
        opciones.put(p.getId() + "-" + variante.getId(), new ProductoOpcionDto(p.getId(), variante.getId(),
                p.getDescripcion() + " - " + variante.getNombreMostrar(), variante.getStock(),
                variante.getPrecioCompra() != null ? variante.getPrecioCompra() : p.getPrecioCompra(),
                variante.getPrecioContado() != null ? variante.getPrecioContado() : p.getPrecioContado(),
                variante.getPrecioTarjeta() != null ? variante.getPrecioTarjeta() : p.getPrecioTarjeta(),
                variante.getPrecioCuentaCorriente() != null ? variante.getPrecioCuentaCorriente() : p.getPrecioCuentaCorriente()));
    }
}

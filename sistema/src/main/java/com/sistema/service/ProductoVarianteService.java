package com.sistema.service;

import com.sistema.model.Producto;
import com.sistema.model.ProductoVariante;
import com.sistema.repository.ProductoRepository;
import com.sistema.repository.ProductoVarianteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.ThreadLocalRandom;

@Service
@Transactional
public class ProductoVarianteService {
    private final ProductoVarianteRepository repository;
    private final ProductoRepository productoRepository;

    public ProductoVarianteService(ProductoVarianteRepository repository, ProductoRepository productoRepository) {
        this.repository = repository;
        this.productoRepository = productoRepository;
    }

    public List<ProductoVariante> listar(Long productoId) {
        return repository.findByProductoIdOrderByNombreAsc(productoId);
    }

    public Optional<ProductoVariante> buscar(Long id) { return repository.findById(id); }

    public ProductoVariante guardar(Long productoId, ProductoVariante variante) {
        Producto producto = productoRepository.findById(productoId)
                .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado"));
        validar(producto, variante);
        ProductoVariante destino = variante;
        if (variante.getId() != null) {
            destino = repository.findById(variante.getId()).orElseThrow(() -> new IllegalArgumentException("Variante no encontrada"));
            if (!destino.getProducto().getId().equals(productoId)) throw new IllegalArgumentException("La variante no pertenece al producto");
            if (tieneTexto(variante.getSku())) destino.setSku(variante.getSku().trim());
            destino.setTalle(variante.getTalle()); destino.setColor(variante.getColor()); destino.setStock(variante.getStock());
            destino.setMercadoLibreGtin(variante.getMercadoLibreGtin());
            if (tieneTexto(variante.getNombre())) destino.setNombre(variante.getNombre());
            if (tieneTexto(variante.getMercadoLibreAtributosJson())) {
                destino.setMercadoLibreAtributosJson(variante.getMercadoLibreAtributosJson());
            }
            destino.setPrecioCompra(variante.getPrecioCompra()); destino.setPrecioContado(variante.getPrecioContado());
            destino.setPrecioTarjeta(variante.getPrecioTarjeta()); destino.setPrecioCuentaCorriente(variante.getPrecioCuentaCorriente());
            if (variante.tieneFoto()) {
                destino.setFotoContenido(variante.getFotoContenido());
                destino.setFotoNombre(variante.getFotoNombre());
                destino.setFotoTipoContenido(variante.getFotoTipoContenido());
                destino.setFotoUrlExterna(variante.getFotoUrlExterna());
            }
        } else {
            if (!tieneTexto(destino.getSku())) destino.setSku(generarSku(producto));
            else destino.setSku(destino.getSku().trim());
            destino.setCodigoBarras(generarCodigoBarras());
        }
        boolean duplicado = destino.getId() == null ? repository.existsBySkuIgnoreCase(destino.getSku())
                : repository.existsBySkuIgnoreCaseAndIdNot(destino.getSku(), destino.getId());
        if (duplicado) throw new IllegalArgumentException("Ya existe una variante con el SKU " + destino.getSku());
        destino.setProducto(producto);
        if (destino.getStock() == null) destino.setStock(0);
        producto.setUsaVariantes(true);
        ProductoVariante guardada = repository.save(destino);
        sincronizarStock(producto);
        return guardada;
    }

    public void guardarFoto(ProductoVariante variante, MultipartFile foto) {
        if (foto == null || foto.isEmpty()) return;
        String tipo = foto.getContentType();
        if (tipo == null || !List.of("image/jpeg", "image/png", "image/webp", "image/gif")
                .contains(tipo.toLowerCase())) {
            throw new IllegalArgumentException("La imagen de la variante debe ser JPG, PNG, WebP o GIF");
        }
        if (foto.getSize() > 5 * 1024 * 1024) {
            throw new IllegalArgumentException("La imagen de la variante no puede superar los 5 MB");
        }
        try {
            variante.setFotoContenido(foto.getBytes());
            variante.setFotoNombre(foto.getOriginalFilename());
            variante.setFotoTipoContenido(tipo);
            variante.setFotoUrlExterna(null);
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("No se pudo guardar la imagen de la variante", e);
        }
    }

    public ProductoVariante guardarImportada(Producto producto, ProductoVariante variante) {
        variante.setProducto(producto);
        if (variante.getStock() == null) variante.setStock(0);
        boolean skuOcupado = tieneTexto(variante.getSku()) && repository.findBySkuIgnoreCase(variante.getSku())
                .filter(otra -> variante.getId() == null || !otra.getId().equals(variante.getId())).isPresent();
        if (!tieneTexto(variante.getSku()) || skuOcupado) variante.setSku(generarSku(producto));
        if (variante.getId() == null || !tieneTexto(variante.getCodigoBarras())) {
            variante.setCodigoBarras(generarCodigoBarras());
        }
        producto.setUsaVariantes(true);
        ProductoVariante guardada = repository.save(variante);
        sincronizarStock(producto);
        return guardada;
    }

    public void eliminar(Long id) {
        ProductoVariante variante = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Variante no encontrada"));
        if (Optional.ofNullable(variante.getStock()).orElse(0) > 0) {
            throw new IllegalStateException("No se puede eliminar una variante con stock");
        }
        Producto producto = variante.getProducto();
        repository.delete(variante);
        repository.flush();
        if (!repository.existsByProductoId(producto.getId())) producto.setUsaVariantes(false);
        sincronizarStock(producto);
    }

    public void quitarAtributosMercadoLibre(Long productoId, Set<String> ids) {
        if (ids == null || ids.isEmpty()) return;
        ObjectMapper mapper = new ObjectMapper();
        List<ProductoVariante> variantes = repository
                .findByProductoIdOrderByNombreAsc(productoId);
        for (ProductoVariante variante : variantes) {
            String json = variante.getMercadoLibreAtributosJson();
            if (json == null || json.isBlank()) continue;
            try {
                java.util.LinkedHashMap<String, String> atributos = mapper.readValue(
                        json, new TypeReference<>() {});
                if (atributos.keySet().removeAll(ids)) {
                    variante.setMercadoLibreAtributosJson(
                            mapper.writeValueAsString(atributos));
                }
            } catch (Exception e) {
                throw new IllegalArgumentException(
                        "Los atributos de la variante " + variante.getSku()
                                + " no son válidos", e);
            }
        }
        repository.saveAll(variantes);
    }

    public void sincronizarStock(Producto producto) {
        if (repository.existsByProductoId(producto.getId())) {
            producto.setUsaVariantes(true);
            int total = repository.findByProductoIdOrderByNombreAsc(producto.getId()).stream()
                    .mapToInt(v -> Optional.ofNullable(v.getStock()).orElse(0)).sum();
            producto.setCantidad(total);
            productoRepository.save(producto);
        } else {
            producto.setUsaVariantes(false);
            productoRepository.save(producto);
        }
    }

    private void validar(Producto producto, ProductoVariante variante) {
        boolean sinCaracteristicas = !tieneTexto(variante.getMercadoLibreAtributosJson())
                || "{}".equals(variante.getMercadoLibreAtributosJson());
        boolean actualSinCaracteristicas = sinCaracteristicas
                && !tieneTexto(variante.getTalle()) && !tieneTexto(variante.getColor());
        List<ProductoVariante> existentes = null;
        if (actualSinCaracteristicas) {
            existentes = repository.findByProductoIdOrderByNombreAsc(producto.getId());
            boolean hayOtraPresentacion = existentes.stream()
                    .anyMatch(v -> variante.getId() == null || !v.getId().equals(variante.getId()));
            if (hayOtraPresentacion) {
                throw new IllegalArgumentException("Para agregar más de una presentación, complete sus características");
            }
        }
        if (variante.getId() == null) {
            if (existentes == null) existentes = repository.findByProductoIdOrderByNombreAsc(producto.getId());
            boolean existeSinCaracteristicas = existentes.stream().filter(Objects::nonNull).anyMatch(v ->
                    (!tieneTexto(v.getMercadoLibreAtributosJson()) || "{}".equals(v.getMercadoLibreAtributosJson()))
                            && !tieneTexto(v.getTalle()) && !tieneTexto(v.getColor()));
            if (existeSinCaracteristicas) {
                throw new IllegalArgumentException("Antes de agregar otra presentación, edite la existente y complete sus características");
            }
        }
        validarPrecio(variante.getPrecioContado(), producto.getPrecioContado(), "contado");
    }

    private void validarPrecio(java.math.BigDecimal precioVariante, java.math.BigDecimal precioGeneral, String nombre) {
        if (precioVariante == null && precioGeneral == null) {
            throw new IllegalArgumentException("Ingrese el precio de " + nombre + " de la variante");
        }
    }

    private String generarSku(Producto producto) {
        String prefijo = tieneTexto(producto.getSku()) ? producto.getSku().trim() : "VAR";
        int numero = 1;
        String sku;
        do {
            sku = prefijo + "-" + String.format("%03d", numero++);
        } while (repository.existsBySkuIgnoreCase(sku));
        return sku;
    }

    private String generarCodigoBarras() {
        String codigo;
        do {
            int idProducto = ThreadLocalRandom.current().nextInt(1000);
            int numeroAleatorio = ThreadLocalRandom.current().nextInt(10000);
            codigo = String.format("EMP%03d%04d", idProducto, numeroAleatorio);
        } while (repository.existsByCodigoBarras(codigo));
        return codigo;
    }

    private boolean tieneTexto(String valor) {
        return valor != null && !valor.isBlank();
    }
}

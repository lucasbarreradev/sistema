package com.sistema.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sistema.dto.ProductoCanalImportado;
import com.sistema.dto.ProductoRemotoSeleccionable;
import com.sistema.model.CanalVenta;
import com.sistema.model.ProductoCatalogoCanal;
import com.sistema.repository.ProductoCatalogoCanalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class CatalogoImportacionService {
    private final ProductoCatalogoCanalRepository repository;
    private final ObjectMapper objectMapper;

    public CatalogoImportacionService(ProductoCatalogoCanalRepository repository,
                                      ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void guardar(CanalVenta canal, List<ProductoCanalImportado> productos) {
        if (canal == null) throw new IllegalArgumentException("Falta el canal");
        Map<String, ProductoCatalogoCanal> existentes = new LinkedHashMap<>();
        repository.findByCanalOrderByDescripcionAsc(canal)
                .forEach(producto -> existentes.put(producto.getIdExterno(), producto));

        Map<String, ProductoCanalImportado> recibidos = new LinkedHashMap<>();
        if (productos != null) {
            productos.stream()
                    .filter(producto -> producto != null
                            && producto.idExterno() != null
                            && !producto.idExterno().isBlank())
                    .forEach(producto -> recibidos.put(producto.idExterno().trim(), producto));
        }

        LocalDateTime ahora = LocalDateTime.now();
        List<ProductoCatalogoCanal> actualizados = recibidos.entrySet().stream()
                .map(entry -> actualizar(
                        existentes.remove(entry.getKey()), canal, entry.getValue(), ahora))
                .toList();
        repository.saveAll(actualizados);
        if (!existentes.isEmpty()) repository.deleteAll(List.copyOf(existentes.values()));
    }

    @Transactional(readOnly = true)
    public boolean disponible(CanalVenta canal) {
        return canal != null && repository.existsByCanal(canal);
    }

    @Transactional(readOnly = true)
    public List<ProductoRemotoSeleccionable> listar(CanalVenta canal) {
        if (canal == null) throw new IllegalArgumentException("Falta el canal");
        return repository.findByCanalOrderByDescripcionAsc(canal).stream()
                .map(this::resumir)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProductoCanalImportado> seleccionar(
            CanalVenta canal, Collection<String> idsExternos) {
        if (idsExternos == null || idsExternos.isEmpty()) {
            throw new IllegalArgumentException("Seleccione al menos un producto para importar");
        }
        Set<String> ids = idsExternos.stream()
                .filter(id -> id != null && !id.isBlank())
                .map(String::trim)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<ProductoCanalImportado> seleccionados =
                repository.findByCanalAndIdExternoIn(canal, ids).stream()
                        .map(this::deserializar)
                        .toList();
        if (seleccionados.isEmpty()) {
            throw new IllegalArgumentException("No se encontró ningún producto seleccionado");
        }
        return seleccionados;
    }

    private ProductoCatalogoCanal actualizar(
            ProductoCatalogoCanal entidad, CanalVenta canal,
            ProductoCanalImportado producto, LocalDateTime ahora) {
        if (entidad == null) entidad = new ProductoCatalogoCanal();
        entidad.setCanal(canal);
        entidad.setIdExterno(producto.idExterno().trim());
        entidad.setSku(producto.sku());
        entidad.setDescripcion(producto.descripcion());
        entidad.setStock(producto.cantidad());
        entidad.setPrecio(producto.precio());
        entidad.setFotoUrl(producto.fotoUrl());
        entidad.setVariantes(producto.variantes() == null ? 0 : producto.variantes().size());
        entidad.setProductoJson(serializar(producto));
        entidad.setActualizadoEn(ahora);
        return entidad;
    }

    private ProductoRemotoSeleccionable resumir(ProductoCatalogoCanal producto) {
        return new ProductoRemotoSeleccionable(
                producto.getIdExterno(), producto.getSku(), producto.getDescripcion(),
                producto.getStock(), producto.getPrecio(), producto.getFotoUrl(),
                producto.getVariantes() == null ? 0 : producto.getVariantes());
    }

    private String serializar(ProductoCanalImportado producto) {
        try {
            return objectMapper.writeValueAsString(producto);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "No se pudo guardar el producto remoto " + producto.idExterno(), e);
        }
    }

    private ProductoCanalImportado deserializar(ProductoCatalogoCanal producto) {
        try {
            return objectMapper.readValue(
                    producto.getProductoJson(), ProductoCanalImportado.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "No se pudo leer el producto remoto " + producto.getIdExterno(), e);
        }
    }
}

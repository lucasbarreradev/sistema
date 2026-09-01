package com.sistema.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sistema.model.Producto;
import com.sistema.model.ProductoVariante;
import com.sistema.dto.AtributoVarianteMl;
import com.sistema.service.MercadoLibreAtributosVarianteService;
import com.sistema.service.ProductoService;
import com.sistema.service.ProductoVarianteService;
import com.sistema.service.TenantPublicResourceService;
import com.sistema.service.ImagenWooCommerceService;
import com.sistema.tenant.TenantContext;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/productos/{productoId}/variantes")
public class ProductoVarianteController {
    private final ProductoService productoService;
    private final ProductoVarianteService varianteService;
    private final MercadoLibreAtributosVarianteService atributosService;
    private final TenantPublicResourceService tenantPublicResourceService;
    private final ImagenWooCommerceService imagenWooCommerceService;
    private final ObjectMapper objectMapper;

    public ProductoVarianteController(ProductoService productoService, ProductoVarianteService varianteService,
                                      MercadoLibreAtributosVarianteService atributosService,
                                      TenantPublicResourceService tenantPublicResourceService,
                                      ImagenWooCommerceService imagenWooCommerceService,
                                      ObjectMapper objectMapper) {
        this.productoService = productoService;
        this.varianteService = varianteService;
        this.atributosService = atributosService;
        this.tenantPublicResourceService = tenantPublicResourceService;
        this.imagenWooCommerceService = imagenWooCommerceService;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public String listar(@PathVariable Long productoId,
                         @RequestParam(defaultValue = "") String volver,
                         Model model) {
        Producto producto = productoService.getProductoById(productoId)
                .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado"));
        List<ProductoVariante> variantes = varianteService.listar(productoId);
        ProductoVariante nueva = new ProductoVariante();
        if (variantes.isEmpty()) {
            nueva.setStock(producto.getCantidad());
            nueva.setPrecioCompra(producto.getPrecioCompra());
            nueva.setPrecioContado(producto.getPrecioContado());
            nueva.setPrecioTarjeta(producto.getPrecioTarjeta());
            nueva.setPrecioCuentaCorriente(producto.getPrecioCuentaCorriente());
            nueva.setMercadoLibreGtin(producto.getMercadoLibreGtin());
        }
        model.addAttribute("producto", producto);
        model.addAttribute("variantes", variantes);
        model.addAttribute("volver", rutaVolver(volver));
        prepararAtributos(producto, nueva, model);
        return "producto/variantes";
    }

    @PostMapping
    public String guardar(@PathVariable Long productoId, @ModelAttribute ProductoVariante variante,
                          @RequestParam Map<String, String> parametros,
                          @RequestParam(required = false) MultipartFile foto,
                          @RequestParam(defaultValue = "") String volver,
                          RedirectAttributes ra) {
        try {
            aplicarAtributosDinamicos(variante, parametros);
            varianteService.guardarFoto(variante, foto);
            varianteService.guardar(productoId, variante);
            ra.addFlashAttribute("mensaje", "Presentación guardada");
        } catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return redireccionDespuesDeGuardar(productoId, volver);
    }

    @GetMapping({"/{id}/foto", "/{id}/foto/{nombre:.+}"})
    @ResponseBody
    public ResponseEntity<?> foto(@PathVariable Long productoId, @PathVariable Long id,
                                  @PathVariable(required = false) String nombre) {
        if (TenantContext.get() == null) {
            return tenantPublicResourceService.buscarTenantVariante(id)
                    .map(tenantId -> TenantContext.call(
                            tenantId, () -> cargarFoto(productoId, id, nombre)))
                    .orElseGet(() -> ResponseEntity.notFound().build());
        }
        return cargarFoto(productoId, id, nombre);
    }

    private ResponseEntity<?> cargarFoto(Long productoId, Long id, String nombre) {
        ProductoVariante variante = varianteService.buscar(id).orElse(null);
        if (variante == null || !variante.getProducto().getId().equals(productoId)
                || (!variante.tieneFoto() && !variante.getProducto().tieneFotoLocal())) {
            return ResponseEntity.notFound().build();
        }
        if (!variante.tieneFotoLocal() && variante.getFotoUrlExterna() != null
                && !variante.getFotoUrlExterna().isBlank()) {
            if ("woocommerce.jpg".equalsIgnoreCase(nombre)) {
                try {
                    return fotoWooCommerce(imagenWooCommerceService
                            .normalizarDesdeUrl(variante.getFotoUrlExterna()));
                } catch (Exception ignored) {
                    // Si la normalización no es posible se conserva la foto original.
                }
            }
            return ResponseEntity.status(302).location(java.net.URI.create(variante.getFotoUrlExterna())).build();
        }
        byte[] contenido = variante.tieneFotoLocal() ? variante.getFotoContenido() : variante.getProducto().getFotoContenido();
        String tipoContenido = variante.tieneFotoLocal() ? variante.getFotoTipoContenido()
                : variante.getProducto().getFotoTipoContenido();
        if ("woocommerce.jpg".equalsIgnoreCase(nombre)) {
            try {
                return fotoWooCommerce(imagenWooCommerceService.normalizar(contenido));
            } catch (Exception ignored) {
                // Si la normalización no es posible se entrega el archivo original.
            }
        }
        MediaType tipo;
        try { tipo = MediaType.parseMediaType(tipoContenido); }
        catch (Exception e) { tipo = MediaType.APPLICATION_OCTET_STREAM; }
        return ResponseEntity.ok().contentType(tipo).body(contenido);
    }

    private ResponseEntity<byte[]> fotoWooCommerce(byte[] contenido) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .contentType(MediaType.IMAGE_JPEG)
                .body(contenido);
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable Long productoId, @PathVariable Long id,
                         @RequestParam(defaultValue = "") String volver,
                         Model model) {
        ProductoVariante variante = varianteService.buscar(id)
                .orElseThrow(() -> new IllegalArgumentException("Variante no encontrada"));
        if (!variante.getProducto().getId().equals(productoId)) throw new IllegalArgumentException("Variante inválida");
        model.addAttribute("producto", variante.getProducto());
        model.addAttribute("variantes", varianteService.listar(productoId));
        model.addAttribute("volver", rutaVolver(volver));
        prepararAtributos(variante.getProducto(), variante, model);
        return "producto/variantes";
    }

    @PostMapping("/{id}/eliminar")
    public String eliminar(@PathVariable Long productoId, @PathVariable Long id,
                           @RequestParam(defaultValue = "") String volver,
                           RedirectAttributes ra) {
        try { varianteService.eliminar(id); ra.addFlashAttribute("mensaje", "Variante eliminada"); }
        catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return redireccionDespuesDeGuardar(productoId, volver);
    }

    private String redireccionDespuesDeGuardar(Long productoId, String volver) {
        String segura = rutaVolver(volver);
        return segura.isBlank()
                ? "redirect:/productos/" + productoId + "/variantes"
                : "redirect:" + segura + "#producto-" + productoId;
    }

    private String rutaVolver(String volver) {
        return "/canales/publicar/revision".equals(volver) ? volver : "";
    }

    void prepararAtributos(Producto producto, ProductoVariante variante, Model model) {
        MercadoLibreAtributosVarianteService.Resultado resultado;
        try {
            resultado = atributosService.obtener(producto);
            if ((producto.getMercadoLibreCategoriaId() == null || producto.getMercadoLibreCategoriaId().isBlank())
                    && resultado.categoriaId() != null) {
                producto.setMercadoLibreCategoriaId(resultado.categoriaId());
                productoService.saveProducto(producto);
            }
        } catch (Exception e) {
            resultado = new MercadoLibreAtributosVarianteService.Resultado(
                    producto.getMercadoLibreCategoriaId(), java.util.List.of());
            model.addAttribute("advertenciaAtributos", "No se pudieron consultar los atributos de Mercado Libre: " + e.getMessage());
        }
        Map<String, String> valores = leerAtributos(variante);
        List<AtributoVarianteMl> atributos = new ArrayList<>(resultado.atributos());
        Set<String> idsInformados = atributos.stream()
                .map(AtributoVarianteMl::id)
                .collect(Collectors.toCollection(HashSet::new));
        valores.keySet().stream()
                .filter(id -> !idsInformados.contains(id))
                .forEach(id -> atributos.add(new AtributoVarianteMl(
                        id, nombreAtributoGuardado(id), "string",
                        List.of(), List.of(), "", false,
                        permiteVariarAtributoGuardado(id))));
        for (int i = 0; i < atributos.size(); i++) {
            AtributoVarianteMl atributo = atributos.get(i);
            String valorGuardado = valores.get(atributo.id());
            if (valorGuardado == null || valorGuardado.isBlank()
                    || atributo.valores().isEmpty()
                    || atributo.valores().contains(valorGuardado)) {
                continue;
            }
            List<String> opciones = new ArrayList<>(atributo.valores());
            opciones.add(valorGuardado);
            atributos.set(i, new AtributoVarianteMl(
                    atributo.id(), atributo.nombre(), atributo.tipo(), opciones,
                    atributo.unidades(), atributo.unidadPredeterminada(),
                    atributo.obligatorio(), atributo.permiteVariar()));
        }
        Map<String, String> anteriores = leerAtributosProducto(producto);
        atributos.forEach(a -> {
            if (variante.getId() == null || a.obligatorio() || !a.permiteVariar()) {
                String valor = anteriores.get(a.id());
                if (valor != null && !valor.isBlank()) valores.putIfAbsent(a.id(), valor);
            }
        });
        Map<String, String> unidades = separarUnidades(atributos, valores);
        model.addAttribute("atributosVariante", atributos);
        model.addAttribute("valoresAtributos", valores);
        model.addAttribute("unidadesAtributos", unidades);
        model.addAttribute("variante", variante);
    }

    void aplicarAtributosDinamicos(ProductoVariante variante, Map<String, String> parametros) {
        boolean recibioAtributos = parametros.keySet().stream()
                .anyMatch(clave -> clave.startsWith("atributo_"));
        if (!recibioAtributos) {
            // El formulario alternativo envía talle y color directamente en el objeto.
            // No deben borrarse si la API de Mercado Libre no devolvió campos dinámicos.
            return;
        }

        Map<String, String> atributos = new LinkedHashMap<>();
        boolean tieneNombreExistente = false;
        if (variante.getId() != null) {
            ProductoVariante existente = varianteService.buscar(variante.getId()).orElse(null);
            if (existente != null) {
                atributos.putAll(leerAtributos(existente));
                tieneNombreExistente = existente.getNombre() != null
                        && !existente.getNombre().isBlank();
            }
        }
        parametros.forEach((clave, valor) -> {
            if (!clave.startsWith("atributo_")) return;
            String id = clave.substring("atributo_".length());
            if (valor == null || valor.isBlank()) atributos.remove(id);
            else atributos.put(id, valor.trim());
        });
        atributos.replaceAll((id, valor) -> {
            String unidad = parametros.get("unidad_" + id);
            String normalizado = unidad == null ? valor : valor.replace(',', '.');
            if (unidad != null && !unidad.isBlank() && normalizado.matches("[-+]?\\d+(\\.\\d+)?")) {
                return normalizado + " " + unidad.trim();
            }
            return normalizado;
        });
        try {
            variante.setMercadoLibreAtributosJson(objectMapper.writeValueAsString(atributos));
        } catch (Exception e) {
            throw new IllegalArgumentException("No se pudieron guardar los atributos de la variante", e);
        }
        variante.setTalle(atributos.get("SIZE"));
        variante.setColor(atributos.get("COLOR"));
        Set<String> idsVariacion = parametros.keySet().stream()
                .filter(clave -> clave.startsWith("es_variacion_"))
                .map(clave -> clave.substring("es_variacion_".length()))
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        if (idsVariacion.isEmpty()) {
            atributos.keySet().stream()
                    .filter(this::permiteVariarAtributoGuardado)
                    .forEach(idsVariacion::add);
        }
        java.util.LinkedHashSet<String> partesNombre = new java.util.LinkedHashSet<>();
        atributos.forEach((id, valor) -> {
            if (!idsVariacion.contains(id) || valor == null || valor.isBlank()) return;
            if ("MAIN_COLOR".equals(id) && atributos.containsKey("COLOR")) return;
            if ("FILTRABLE_SIZE".equals(id) && atributos.containsKey("SIZE")) return;
            partesNombre.add(valor.trim());
        });
        if (!partesNombre.isEmpty() && !tieneNombreExistente
                && (variante.getNombre() == null || variante.getNombre().isBlank())) {
            variante.setNombre(String.join(" / ", partesNombre));
        }
    }

    private String nombreAtributoGuardado(String id) {
        if (id == null) return "Característica";
        return switch (id) {
            case "SIZE" -> "Talle";
            case "COLOR", "COLOR_SECONDARY_COLOR" -> "Color";
            case "BRAND" -> "Marca";
            case "MODEL" -> "Modelo";
            case "GTIN" -> "GTIN";
            default -> {
                String texto = id.replace('_', ' ').toLowerCase();
                yield texto.isBlank()
                        ? "Característica"
                        : Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
            }
        };
    }

    private boolean permiteVariarAtributoGuardado(String id) {
        if (id == null || id.isBlank()) return false;
        if (id.startsWith("SELLER_PACKAGE_")) return false;
        return !Set.of(
                "BRAND", "MODEL", "GARMENT_TYPE", "GTIN", "EMPTY_GTIN_REASON",
                "VALUE_ADDED_TAX", "IMPORT_DUTY", "FILTRABLE_SIZE",
                "ITEM_CONDITION", "GENDER", "SIZE_GRID_ID", "SIZE_GRID_ROW_ID",
                "HAZMAT_TRANSPORTABILITY", "AGID", "MPN"
        ).contains(id);
    }

    private Map<String, String> leerAtributos(ProductoVariante variante) {
        Map<String, String> valores = new LinkedHashMap<>();
        if (variante.getMercadoLibreAtributosJson() == null || variante.getMercadoLibreAtributosJson().isBlank()) {
            if (variante.getTalle() != null && !variante.getTalle().isBlank()) valores.put("SIZE", variante.getTalle());
            if (variante.getColor() != null && !variante.getColor().isBlank()) valores.put("COLOR", variante.getColor());
            return valores;
        }
        try {
            valores.putAll(objectMapper.readValue(
                    variante.getMercadoLibreAtributosJson(),
                    new TypeReference<LinkedHashMap<String, String>>() {}));
        } catch (Exception e) {
            // Los campos básicos de la variante todavía pueden recuperarse.
        }
        if (variante.getTalle() != null && !variante.getTalle().isBlank()) {
            valores.putIfAbsent("SIZE", variante.getTalle());
        }
        if (variante.getColor() != null && !variante.getColor().isBlank()) {
            valores.putIfAbsent("COLOR", variante.getColor());
        }
        return valores;
    }

    private Map<String, String> leerAtributosProducto(Producto producto) {
        Map<String, String> valores = new LinkedHashMap<>();
        if (producto.getMercadoLibreMarca() != null && !producto.getMercadoLibreMarca().isBlank()) {
            valores.put("BRAND", producto.getMercadoLibreMarca());
        }
        if (producto.getMercadoLibreModelo() != null && !producto.getMercadoLibreModelo().isBlank()) {
            valores.put("MODEL", producto.getMercadoLibreModelo());
        }
        String json = producto.getMercadoLibreAtributosJson();
        if (json == null || json.isBlank()) return valores;
        try {
            List<Map<String, Object>> atributos = objectMapper.readValue(json, new TypeReference<>() {});
            for (Map<String, Object> atributo : atributos) {
                Object id = atributo.get("id");
                Object valor = atributo.get("value_name");
                if (id != null && valor != null) valores.put(id.toString(), valor.toString());
            }
        } catch (Exception ignored) {
            // Los datos anteriores no deben impedir cargar la pantalla de presentaciones.
        }
        return valores;
    }

    private Map<String, String> separarUnidades(List<AtributoVarianteMl> campos, Map<String, String> valores) {
        Map<String, String> seleccionadas = new LinkedHashMap<>();
        for (AtributoVarianteMl campo : campos) {
            String unidad = campo.unidadPredeterminada();
            String valor = valores.get(campo.id());
            if (valor != null) {
                for (String candidata : campo.unidades()) {
                    String sufijo = " " + candidata;
                    if (valor.endsWith(sufijo)) {
                        unidad = candidata;
                        valores.put(campo.id(), valor.substring(0, valor.length() - sufijo.length()));
                        break;
                    }
                }
            }
            seleccionadas.put(campo.id(), unidad);
        }
        return seleccionadas;
    }
}

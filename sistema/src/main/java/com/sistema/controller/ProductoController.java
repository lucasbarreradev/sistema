package com.sistema.controller;

import com.sistema.model.Producto;
import com.sistema.model.Proveedor;
import com.sistema.model.TipoIva;
import com.sistema.dto.ProductoOpcionDto;
import com.sistema.service.ProductoService;
import com.sistema.service.ProveedorService;
import com.sistema.service.MercadoLibreAtributosProductoService;
import com.sistema.service.TenantPublicResourceService;
import com.sistema.tenant.TenantContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.support.SessionStatus;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.net.URI;

@Controller
@RequestMapping("/productos")
@SessionAttributes("producto")
public class ProductoController {
    private final ProductoService productoService;
    private final ProveedorService proveedorService;
    private final MercadoLibreAtributosProductoService atributosProductoMlService;
    private final TenantPublicResourceService tenantPublicResourceService;
    private final ObjectMapper objectMapper;

    public ProductoController(ProductoService productoService,
                              ProveedorService proveedorService,
                              MercadoLibreAtributosProductoService atributosProductoMlService,
                              TenantPublicResourceService tenantPublicResourceService,
                              ObjectMapper objectMapper) {
        this.productoService = productoService;
        this.proveedorService = proveedorService;
        this.atributosProductoMlService = atributosProductoMlService;
        this.tenantPublicResourceService = tenantPublicResourceService;
        this.objectMapper = objectMapper;
    }

    // ==========================================
    // LISTAR productos
    // ==========================================
    @GetMapping
    public String listar(Model model) {
        model.addAttribute("productos",
                productoService.getProductos());
        return "producto/listar";
    }

    // ==========================================
    // FORM NUEVO producto
    // ==========================================
    @GetMapping("/nuevo")
    public String nuevo(
            @RequestParam(required = false) Long proveedorId,
            Model model) {

        Producto producto = (Producto) model.getAttribute("producto");

        if (producto == null) {
            producto = new Producto();
        }

        if (proveedorId != null) {
            proveedorService.getProveedorById(proveedorId)
                    .ifPresent(producto::setProveedor);
        }

        model.addAttribute("producto", producto);
        model.addAttribute("tiposIva", TipoIva.values());
        return "producto/form";
    }


    // ==========================================
    // GUARDAR producto (nuevo)
    // ==========================================
    @PostMapping("/guardar")
    public String guardar(@ModelAttribute("producto") Producto producto,
                          @RequestParam(required = false) Long proveedorId,
                          @RequestParam(required = false) MultipartFile foto,
                          @RequestParam Map<String, String> parametros,
                          SessionStatus status,
                          RedirectAttributes ra) {

        producto.setId(null);

        producto.setUsaVariantes(true);
        producto.setCantidad(0);
        producto.setPrecioCompra(null);
        producto.setPrecioContado(null);
        producto.setPrecioTarjeta(null);
        producto.setPrecioCuentaCorriente(null);

        if (proveedorId != null) {
            Proveedor proveedor = proveedorService
                    .getProveedorById(proveedorId)
                    .orElseThrow(() -> new RuntimeException("Proveedor no encontrado"));
            producto.setProveedor(proveedor);
        }

        try {
            aplicarAtributosProductoMl(producto, parametros);
            productoService.guardarFoto(producto, foto);
            productoService.saveProducto(producto);
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/productos/nuevo";
        }

        status.setComplete(); // 🔥 limpia la sesión
        return "redirect:/productos/" + producto.getId() + "/variantes";
    }



    // ==========================================
    // FORM EDITAR producto
    // ==========================================
    @GetMapping("/editar/{id}")
    public String editar(@PathVariable Long id,
                         @RequestParam(required = false) Long proveedorId,
                         Model model,
                         RedirectAttributes ra) {

        Producto producto = productoService.getProductoById(id).orElse(null);

        if (producto == null) {
            ra.addFlashAttribute("error", "Producto no encontrado");
            return "redirect:/productos";
        }

        if (proveedorId != null) {
            proveedorService.getProveedorById(proveedorId)
                    .ifPresent(producto::setProveedor);
        }

        model.addAttribute("producto", producto);
        model.addAttribute("tiposIva", TipoIva.values());
        return "producto/form";
    }



    // ==========================================
    // ACTUALIZAR productos
    // ==========================================
    @PostMapping("/actualizar/{id}")
    public String actualizar(@PathVariable Long id,
                             @ModelAttribute Producto producto,
                             @RequestParam(required = false) MultipartFile foto,
                             @RequestParam Map<String, String> parametros,
                             RedirectAttributes ra) {

        try {
            aplicarAtributosProductoMl(producto, parametros);
            productoService.guardarFoto(producto, foto);
            productoService.updateProducto(id, producto);
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/productos/editar/" + id;
        }
        ra.addFlashAttribute("mensaje",
                "Producto actualizado correctamente");

        return "redirect:/productos";
    }

    private void prepararAtributosProductoMl(Producto producto, Model model) {
        try {
            model.addAttribute("atributosProductoMl", atributosProductoMlService.obtenerObligatorios(producto));
            model.addAttribute("valoresAtributosProductoMl", leerAtributosProductoMl(producto));
        } catch (Exception e) {
            model.addAttribute("advertenciaAtributosProductoMl",
                    "No se pudieron consultar los campos de la categoría de Mercado Libre: " + e.getMessage());
        }
    }

    private void aplicarAtributosProductoMl(Producto producto, Map<String, String> parametros) {
        boolean recibioAtributos = parametros.keySet().stream().anyMatch(k -> k.startsWith("ml_atributo_"));
        if (!recibioAtributos) return;
        Map<String, Map<String, Object>> atributos = leerAtributosPayload(producto);
        parametros.forEach((clave, valor) -> {
            if (!clave.startsWith("ml_atributo_")) return;
            String id = clave.substring("ml_atributo_".length());
            if (valor == null || valor.isBlank()) { atributos.remove(id); return; }
            String valorFinal = valor.trim().replace(',', '.');
            String unidad = parametros.get("ml_unidad_" + id);
            if (unidad != null && !unidad.isBlank() && valorFinal.matches("[-+]?\\d+(\\.\\d+)?")) {
                valorFinal += " " + unidad.trim();
            }
            String[] partes = valorFinal.split("\\|\\|\\|", 2);
            Map<String, Object> atributo = new LinkedHashMap<>();
            atributo.put("id", id);
            if (partes.length == 2 && !partes[0].isBlank()) atributo.put("value_id", partes[0]);
            atributo.put("value_name", partes.length == 2 ? partes[1] : valorFinal);
            atributos.put(id, atributo);
        });
        try { producto.setMercadoLibreAtributosJson(objectMapper.writeValueAsString(atributos.values())); }
        catch (Exception e) { throw new IllegalArgumentException("No se pudieron guardar los campos de Mercado Libre", e); }
    }

    private Map<String, String> leerAtributosProductoMl(Producto producto) {
        Map<String, String> resultado = new LinkedHashMap<>();
        leerAtributosPayload(producto).forEach((id, atributo) -> {
            String valueId = atributo.get("value_id") == null ? "" : atributo.get("value_id").toString();
            String valueName = atributo.get("value_name") == null ? "" : atributo.get("value_name").toString();
            resultado.put(id, valueId.isBlank() ? valueName : valueId + "|||" + valueName);
        });
        return resultado;
    }

    private Map<String, Map<String, Object>> leerAtributosPayload(Producto producto) {
        Map<String, Map<String, Object>> resultado = new LinkedHashMap<>();
        String json = producto.getMercadoLibreAtributosJson();
        if (json == null || json.isBlank()) return resultado;
        try {
            List<Map<String, Object>> lista = objectMapper.readValue(json, new TypeReference<>() {});
            for (Map<String, Object> atributo : lista) {
                Object id = atributo.get("id");
                if (id != null) resultado.put(id.toString(), new LinkedHashMap<>(atributo));
            }
            return resultado;
        } catch (Exception e) {
            throw new IllegalArgumentException("Los campos guardados de Mercado Libre no son válidos", e);
        }
    }

    // ==========================================
    // ELIMINAR producto
    // ==========================================
    @PostMapping("/eliminar/{id}")
    public String eliminar(@PathVariable Long id, RedirectAttributes ra) {
        try {
            productoService.deleteProducto(id);
            ra.addFlashAttribute("mensaje", "Producto eliminado");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/productos";
    }



    @GetMapping("/buscar")
    @ResponseBody
    public List<ProductoOpcionDto> buscar(@RequestParam String q) {
        return productoService.buscarOpciones(q);
    }

    @GetMapping("/nuevo/limpio")
    public String nuevoLimpio(SessionStatus status) {
        status.setComplete();
        return "redirect:/productos/nuevo";
    }

    @GetMapping({"/{id}/foto", "/{id}/foto/{nombre:.+}"})
    @ResponseBody
    public ResponseEntity<?> foto(@PathVariable Long id,
                                  @PathVariable(required = false) String nombre) {
        if (TenantContext.get() == null) {
            return tenantPublicResourceService.buscarTenantProducto(id)
                    .map(tenantId -> TenantContext.call(tenantId, () -> cargarFoto(id)))
                    .orElseGet(() -> ResponseEntity.notFound().build());
        }
        return cargarFoto(id);
    }

    private ResponseEntity<?> cargarFoto(Long id) {
        Producto producto = productoService.getProductoById(id).orElse(null);
        if (producto == null || !producto.tieneFoto()) {
            return ResponseEntity.notFound().build();
        }
        if (!producto.tieneFotoLocal()) {
            try {
                URI uri = URI.create(producto.getFotoUrlExterna());
                if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))) {
                    return ResponseEntity.badRequest().build();
                }
                return ResponseEntity.status(302).location(uri).build();
            } catch (IllegalArgumentException e) {
                return ResponseEntity.notFound().build();
            }
        }
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(producto.getFotoTipoContenido());
        } catch (Exception e) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=3600")
                .contentType(mediaType)
                .body(producto.getFotoContenido());
    }


}

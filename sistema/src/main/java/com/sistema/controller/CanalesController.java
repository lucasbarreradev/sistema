package com.sistema.controller;

import com.sistema.dto.ResultadoImportacion;
import com.sistema.dto.ErrorSincronizacionDto;
import com.sistema.model.CanalVenta;
import com.sistema.model.TrabajoSincronizacion;
import com.sistema.service.ImportacionCsvService;
import com.sistema.service.ProductoService;
import com.sistema.service.PublicacionService;
import com.sistema.service.ImportacionCanalService;
import com.sistema.service.TrabajoSincronizacionService;
import com.sistema.service.MercadoLibreTokenService;
import com.sistema.service.TiendanubeCredencialesService;
import com.sistema.service.WooCommerceCredencialesService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.data.domain.PageRequest;

@Controller
@RequestMapping("/canales")
public class CanalesController {
    private static final Pattern ERROR_PUBLICACION = Pattern.compile("^(.+?) / ([^:]+): (.*)$");
    private static final Pattern CAMPOS_OBLIGATORIOS = Pattern.compile(
            "campos obligatorios de Mercado Libre: (.+?)(?:\\. Si |\\.$|$)", Pattern.CASE_INSENSITIVE);
    private final ProductoService productoService;
    private final ImportacionCsvService importacionCsvService;
    private final PublicacionService publicacionService;
    private final ImportacionCanalService importacionCanalService;
    private final TrabajoSincronizacionService trabajoSincronizacionService;
    private final MercadoLibreTokenService mercadoLibreTokenService;
    private final WooCommerceCredencialesService wooCommerceCredencialesService;
    private final TiendanubeCredencialesService tiendanubeCredencialesService;
    private final String mercadoLibreRedirectUri;
    private final String publicBaseUrl;

    public CanalesController(ProductoService productoService, ImportacionCsvService importacionCsvService,
                             PublicacionService publicacionService, ImportacionCanalService importacionCanalService,
                             TrabajoSincronizacionService trabajoSincronizacionService,
                             MercadoLibreTokenService mercadoLibreTokenService,
                             WooCommerceCredencialesService wooCommerceCredencialesService,
                             TiendanubeCredencialesService tiendanubeCredencialesService,
                             @Value("${integraciones.mercadolibre.redirect-uri:}") String mercadoLibreRedirectUri,
                             @Value("${integraciones.public-base-url:}") String publicBaseUrl) {
        this.productoService = productoService;
        this.importacionCsvService = importacionCsvService;
        this.publicacionService = publicacionService;
        this.importacionCanalService = importacionCanalService;
        this.trabajoSincronizacionService = trabajoSincronizacionService;
        this.mercadoLibreTokenService = mercadoLibreTokenService;
        this.wooCommerceCredencialesService = wooCommerceCredencialesService;
        this.tiendanubeCredencialesService = tiendanubeCredencialesService;
        this.mercadoLibreRedirectUri = mercadoLibreRedirectUri;
        this.publicBaseUrl = publicBaseUrl == null ? "" : publicBaseUrl.replaceAll("/+$", "");
    }

    @GetMapping
    public String canales(@RequestParam(defaultValue = "0") int productoPage,
                          @RequestParam(defaultValue = "50") int productoSize,
                          @RequestParam(defaultValue = "") String productoQ,
                          @RequestParam(defaultValue = "false") boolean seleccionarTodosResultados,
                          Model model) {
        int pagina = Math.max(productoPage, 0);
        int tamanio = Math.max(10, Math.min(productoSize, 100));
        var productos = productoService.getProductosListado(
                productoQ, PageRequest.of(pagina, tamanio));
        model.addAttribute("productos", productos.getContent());
        model.addAttribute("paginaProductos", productos);
        model.addAttribute("busquedaProductos", productoQ == null ? "" : productoQ.trim());
        model.addAttribute("seleccionarTodosResultados", seleccionarTodosResultados);
        model.addAttribute("tamanioPagina", tamanio);
        model.addAttribute("canales", CanalVenta.values());
        model.addAttribute("configuracion", publicacionService.estadoConfiguracion());
        model.addAttribute("configuracionImportacion", java.util.Arrays.stream(CanalVenta.values())
                .collect(java.util.stream.Collectors.toMap(c -> c, importacionCanalService::configurado)));
        model.addAttribute("publicaciones", publicacionService.historial());
        List<TrabajoSincronizacion> trabajos = trabajoSincronizacionService.ultimos();
        model.addAttribute("trabajosSincronizacion", trabajos);
        model.addAttribute("erroresSincronizacionPorTrabajo", mapearErrores(trabajos));
        model.addAttribute("sincronizacionActiva", trabajoSincronizacionService.hayTrabajoActivo());
        boolean mercadoLibreConectado = mercadoLibreTokenService.conectado();
        model.addAttribute("mercadoLibreConectado", mercadoLibreConectado);
        model.addAttribute("mercadoLibreCuentaNombre", mercadoLibreConectado
                ? mercadoLibreTokenService.nombreCuentaConectada() : "");
        model.addAttribute("mercadoLibreOAuthDisponible",
                mercadoLibreTokenService.aplicacionConfigurada() && !mercadoLibreRedirectUri.isBlank());
        model.addAttribute("mercadoLibreRedirectUri", mercadoLibreRedirectUri);
        boolean wooCommerceConectado = wooCommerceCredencialesService.conectado();
        model.addAttribute("wooCommerceConectado", wooCommerceConectado);
        model.addAttribute("wooCommerceCuentaNombre", wooCommerceConectado
                ? wooCommerceCredencialesService.nombreCuentaConectada() : "");
        model.addAttribute("wooCommerceConexionDisponible",
                wooCommerceCredencialesService.conexionDisponible() && !publicBaseUrl.isBlank());
        model.addAttribute("wooCommerceUrl", wooCommerceCredencialesService.urlTienda());
        boolean tiendanubeConectado = tiendanubeCredencialesService.conectado();
        model.addAttribute("tiendanubeConectado", tiendanubeConectado);
        model.addAttribute("tiendanubeCuentaNombre", tiendanubeConectado
                ? tiendanubeCredencialesService.nombreCuentaConectada() : "");
        model.addAttribute("tiendanubeOAuthDisponible", tiendanubeCredencialesService.aplicacionConfigurada());
        model.addAttribute("tiendanubeRedirectUri", tiendanubeCredencialesService.getRedirectUri());
        model.addAttribute("webhookMercadoLibreUrl",
                publicBaseUrl.isBlank() ? "" : publicBaseUrl + "/webhooks/mercadolibre");
        return "canales/index";
    }

    List<ErrorSincronizacionDto> mapearErrores(TrabajoSincronizacion trabajo) {
        return mapearErrores(trabajo, new HashMap<>());
    }

    private Map<Long, List<ErrorSincronizacionDto>> mapearErrores(List<TrabajoSincronizacion> trabajos) {
        Map<Long, List<ErrorSincronizacionDto>> resultado = new LinkedHashMap<>();
        Map<String, Optional<Long>> productosPorSku = new HashMap<>();
        for (TrabajoSincronizacion trabajo : trabajos) {
            if (trabajo.getId() != null) {
                resultado.put(trabajo.getId(), mapearErrores(trabajo, productosPorSku));
            }
        }
        return resultado;
    }

    private List<ErrorSincronizacionDto> mapearErrores(
            TrabajoSincronizacion trabajo, Map<String, Optional<Long>> productosPorSku) {
        if (trabajo == null || trabajo.getDetalle() == null || trabajo.getDetalle().isBlank()) return List.of();
        List<ErrorSincronizacionDto> resultado = new ArrayList<>();
        trabajo.getDetalle().lines().map(String::trim).filter(linea -> !linea.isBlank()).forEach(linea -> {
            Matcher error = ERROR_PUBLICACION.matcher(linea);
            String referencia = error.matches() ? error.group(1).trim() : referenciaGenerica(linea);
            String canal = error.matches() ? error.group(2).trim() : "";
            String mensaje = error.matches() ? error.group(3).trim() : mensajeGenerico(linea);
            Long productoId = productosPorSku.computeIfAbsent(referencia,
                    sku -> productoService.getProductoBySku(sku).map(p -> p.getId())).orElse(null);
            resultado.add(new ErrorSincronizacionDto(
                    referencia, canal, mensaje, correcciones(mensaje), productoId));
        });
        return resultado;
    }

    private String referenciaGenerica(String linea) {
        int separador = linea.indexOf(':');
        return separador > 0 ? linea.substring(0, separador).trim() : "Sin referencia";
    }

    private String mensajeGenerico(String linea) {
        int separador = linea.indexOf(':');
        return separador > 0 ? linea.substring(separador + 1).trim() : linea;
    }

    private List<String> correcciones(String mensaje) {
        Matcher campos = CAMPOS_OBLIGATORIOS.matcher(mensaje);
        if (campos.find()) {
            return java.util.Arrays.stream(campos.group(1).split(","))
                    .map(String::trim).filter(valor -> !valor.isBlank()).toList();
        }
        String normalizado = mensaje.toLowerCase(java.util.Locale.ROOT);
        if (normalizado.contains("precio de contado")) return List.of("Precio");
        if (normalizado.contains("gtin real")) return List.of("GTIN real");
        if (normalizado.contains("categoría final") || normalizado.contains("cambie la categoría")) {
            return List.of("Categoría de Mercado Libre");
        }
        if (normalizado.contains("under_review") || normalizado.contains("está en revisión")) {
            return List.of("Esperar revisión de Mercado Libre");
        }
        if (normalizado.contains("conflicto temporal") || normalizado.contains("repeated user-product")) {
            return List.of("Reintentar sincronización");
        }
        return List.of("Revisar detalle");
    }

    @PostMapping("/importar/{canal}")
    public String importarCanal(@PathVariable CanalVenta canal,
                                @RequestParam(defaultValue = "false") boolean incluirInactivas,
                                RedirectAttributes ra) {
        try {
            var trabajo = trabajoSincronizacionService
                    .iniciarImportacionCompleta(canal, incluirInactivas);
            ra.addFlashAttribute("mensaje", "Importación completa iniciada en segundo plano (trabajo #"
                    + trabajo.getId() + "). Puede salir de esta página sin interrumpirla.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/canales";
    }

    @PostMapping("/importar/mercadolibre/ultimas")
    public String importarUltimasMercadoLibre(
            @RequestParam int cantidad,
            @RequestParam(defaultValue = "") String categoria,
            @RequestParam(defaultValue = "false") boolean incluirInactivas,
            RedirectAttributes ra) {
        try {
            var trabajo = trabajoSincronizacionService
                    .iniciarImportacionFiltradaMercadoLibre(
                            cantidad, categoria, incluirInactivas);
            ra.addFlashAttribute("mensaje",
                    "Importación de las últimas publicaciones iniciada en segundo plano (trabajo #"
                            + trabajo.getId() + ").");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/canales";
    }

    @PostMapping("/sincronizar")
    public String sincronizar(@RequestParam CanalVenta origen,
                              @RequestParam(required = false) List<CanalVenta> destinos,
                              RedirectAttributes ra) {
        try {
            if (destinos == null) throw new IllegalArgumentException("Seleccione al menos un canal de destino");
            var trabajo = trabajoSincronizacionService.iniciar(origen, destinos);
            ra.addFlashAttribute("mensaje", "Sincronización iniciada en segundo plano (trabajo #"
                    + trabajo.getId() + "). Puede salir de esta página sin interrumpirla.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/canales";
    }

    @PostMapping("/importar/mercadolibre")
    public String importar(@RequestParam MultipartFile archivo, RedirectAttributes ra) {
        try {
            ResultadoImportacion resultado = importacionCsvService.importarMercadoLibre(archivo);
            ra.addFlashAttribute("mensaje", "Importación terminada: " + resultado.resumen());
            if (!resultado.getErrores().isEmpty()) ra.addFlashAttribute("erroresImportacion", resultado.getErrores());
        } catch (Exception e) {
            String mensaje = e.getMessage();
            ra.addFlashAttribute("error", mensaje == null || mensaje.isBlank()
                    ? "No se pudo importar el archivo" : mensaje);
        }
        return "redirect:/canales";
    }

    @PostMapping("/publicar")
    public String publicar(@RequestParam(required = false) List<Long> productoIds,
                           @RequestParam(required = false) List<CanalVenta> canales,
                           @RequestParam(defaultValue = "false") boolean seleccionarTodosResultados,
                           @RequestParam(defaultValue = "") String productoQ,
                           RedirectAttributes ra) {
        try {
            if (canales == null || canales.isEmpty()) {
                throw new IllegalArgumentException("Seleccione al menos un canal");
            }
            List<Long> productosSeleccionados = seleccionarTodosResultados
                    ? productoService.getIdsProductosListado(productoQ)
                    : productoIds;
            if (productosSeleccionados == null || productosSeleccionados.isEmpty()) {
                throw new IllegalArgumentException("Seleccione al menos un producto");
            }
            var trabajo = trabajoSincronizacionService
                    .iniciarPublicacion(productosSeleccionados, canales);
            ra.addFlashAttribute("mensaje", "Publicación iniciada en segundo plano (trabajo #"
                    + trabajo.getId() + ") para " + productosSeleccionados.size()
                    + " producto(s). Puede salir de esta página sin interrumpirla.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/canales";
    }

    @PostMapping("/trabajos/{trabajoId}/cancelar")
    public String cancelarTrabajo(@PathVariable Long trabajoId, RedirectAttributes ra) {
        try {
            trabajoSincronizacionService.solicitarCancelacion(trabajoId);
            ra.addFlashAttribute("mensaje", "Cancelación solicitada para el trabajo #" + trabajoId
                    + ". La operación actual puede tardar unos instantes en terminar.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/canales";
    }
}

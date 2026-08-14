package com.sistema.controller;

import com.sistema.dto.ResultadoImportacion;
import com.sistema.model.CanalVenta;
import com.sistema.service.ImportacionCsvService;
import com.sistema.service.ProductoService;
import com.sistema.service.PublicacionService;
import com.sistema.service.ImportacionCanalService;
import com.sistema.service.TrabajoSincronizacionService;
import com.sistema.service.CatalogoImportacionService;
import com.sistema.service.EdicionMasivaPrecioService;
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
import java.math.BigDecimal;
import org.springframework.data.domain.PageRequest;

@Controller
@RequestMapping("/canales")
public class CanalesController {
    private final ProductoService productoService;
    private final ImportacionCsvService importacionCsvService;
    private final PublicacionService publicacionService;
    private final ImportacionCanalService importacionCanalService;
    private final TrabajoSincronizacionService trabajoSincronizacionService;
    private final CatalogoImportacionService catalogoImportacionService;
    private final EdicionMasivaPrecioService edicionMasivaPrecioService;
    private final MercadoLibreTokenService mercadoLibreTokenService;
    private final WooCommerceCredencialesService wooCommerceCredencialesService;
    private final TiendanubeCredencialesService tiendanubeCredencialesService;
    private final String mercadoLibreRedirectUri;
    private final String publicBaseUrl;

    public CanalesController(ProductoService productoService, ImportacionCsvService importacionCsvService,
                             PublicacionService publicacionService, ImportacionCanalService importacionCanalService,
                             TrabajoSincronizacionService trabajoSincronizacionService,
                             CatalogoImportacionService catalogoImportacionService,
                             EdicionMasivaPrecioService edicionMasivaPrecioService,
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
        this.catalogoImportacionService = catalogoImportacionService;
        this.edicionMasivaPrecioService = edicionMasivaPrecioService;
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
                          Model model) {
        int pagina = Math.max(productoPage, 0);
        int tamanio = Math.max(10, Math.min(productoSize, 100));
        var productos = productoService.getProductosListado(
                productoQ, PageRequest.of(pagina, tamanio));
        model.addAttribute("productos", productos.getContent());
        model.addAttribute("paginaProductos", productos);
        model.addAttribute("busquedaProductos", productoQ == null ? "" : productoQ.trim());
        model.addAttribute("tamanioPagina", tamanio);
        model.addAttribute("canales", CanalVenta.values());
        model.addAttribute("configuracion", publicacionService.estadoConfiguracion());
        model.addAttribute("configuracionImportacion", java.util.Arrays.stream(CanalVenta.values())
                .collect(java.util.stream.Collectors.toMap(c -> c, importacionCanalService::configurado)));
        model.addAttribute("catalogosImportacion", java.util.Arrays.stream(CanalVenta.values())
                .collect(java.util.stream.Collectors.toMap(
                        canal -> canal, catalogoImportacionService::disponible)));
        model.addAttribute("publicaciones", publicacionService.historial());
        model.addAttribute("trabajosSincronizacion", trabajoSincronizacionService.ultimos());
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

    @PostMapping("/importar/{canal}/preparar")
    public String prepararSeleccionImportacion(@PathVariable CanalVenta canal,
                                               @RequestParam(defaultValue = "false") boolean incluirInactivas,
                                               RedirectAttributes ra) {
        try {
            var trabajo = trabajoSincronizacionService
                    .iniciarPreparacionImportacion(canal, incluirInactivas);
            ra.addFlashAttribute("mensaje", "Actualización de la lista iniciada en segundo plano (trabajo #"
                    + trabajo.getId() + "). Puede seguir usando la lista que ya estaba guardada.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/canales";
    }

    @GetMapping("/importar/{canal}/seleccionar")
    public String seleccionarImportacion(@PathVariable CanalVenta canal, Model model,
                                         RedirectAttributes ra) {
        try {
            model.addAttribute("canal", canal);
            model.addAttribute("productosRemotos", catalogoImportacionService.listar(canal));
            model.addAttribute("categoriasRemotas", catalogoImportacionService.listarCategorias(canal));
            model.addAttribute("canales", CanalVenta.values());
            model.addAttribute("configuracion", publicacionService.estadoConfiguracion());
            return "canales/seleccionar_importacion";
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/canales";
        }
    }

    @PostMapping("/importar/{canal}/seleccionados")
    public String importarSeleccionados(@PathVariable CanalVenta canal,
                                        @RequestParam(required = false) List<String> idsExternos,
                                        @RequestParam(required = false) List<CanalVenta> destinos,
                                        @RequestParam(defaultValue = "0") BigDecimal ajustePrecioPorcentaje,
                                        RedirectAttributes ra) {
        try {
            var seleccionados = catalogoImportacionService.seleccionar(canal, idsExternos);
            seleccionados = edicionMasivaPrecioService
                    .ajustarImportados(seleccionados, ajustePrecioPorcentaje);
            var trabajo = trabajoSincronizacionService
                    .iniciarImportacionSeleccionada(canal, seleccionados, destinos);
            ra.addFlashAttribute("mensaje", "Transferencia de " + seleccionados.size()
                    + " producto(s) iniciada en segundo plano (trabajo #" + trabajo.getId()
                    + "): " + trabajo.getFlujoDescripcion() + ".");
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
                           RedirectAttributes ra) {
        if (productoIds == null || productoIds.isEmpty() || canales == null || canales.isEmpty()) {
            ra.addFlashAttribute("error", "Seleccione al menos un producto y un canal");
            return "redirect:/canales";
        }
        try {
            var trabajo = trabajoSincronizacionService.iniciarPublicacion(productoIds, canales);
            ra.addFlashAttribute("mensaje", "Publicación iniciada en segundo plano (trabajo #"
                    + trabajo.getId() + "). Puede salir de esta página sin interrumpirla.");
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

package com.sistema.controller;

import com.sistema.model.CondicionFiscalArca;
import com.sistema.model.Venta;
import com.sistema.repository.VentaRepository;
import com.sistema.service.AfipService;
import com.sistema.service.ConfiguracionArcaService;
import com.sistema.service.FacturaPdfService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/facturacion")
public class FacturacionController {
    private final ConfiguracionArcaService configuracion;
    private final AfipService afipService;
    private final VentaRepository ventaRepository;
    private final FacturaPdfService facturaPdfService;

    public FacturacionController(ConfiguracionArcaService configuracion,
                                 AfipService afipService,
                                 VentaRepository ventaRepository,
                                 FacturaPdfService facturaPdfService) {
        this.configuracion = configuracion;
        this.afipService = afipService;
        this.ventaRepository = ventaRepository;
        this.facturaPdfService = facturaPdfService;
    }

    @GetMapping
    public String index(Model model) {
        model.addAttribute("configuracionArca", configuracion.resumen().orElse(null));
        model.addAttribute("arcaConfigurada", configuracion.configurada());
        model.addAttribute("condicionesFiscales", CondicionFiscalArca.values());
        model.addAttribute("ventas", ventaRepository.findByEstadoNotOrderByFechaVentaDesc(Venta.Estado.ANULADA));
        return "facturacion/index";
    }

    @PostMapping("/configuracion")
    public String guardarConfiguracion(@RequestParam String cuit,
                                       @RequestParam Integer puntoVenta,
                                       @RequestParam CondicionFiscalArca condicionFiscal,
                                       @RequestParam(required = false) MultipartFile certificado,
                                       @RequestParam(required = false) MultipartFile clavePrivada,
                                       RedirectAttributes ra) {
        try {
            configuracion.guardar(cuit, puntoVenta, condicionFiscal, certificado, clavePrivada);
            ra.addFlashAttribute("mensaje", "Configuración de homologación guardada correctamente");
        } catch (Exception e) {
            ra.addFlashAttribute("error", mensaje(e));
        }
        return "redirect:/facturacion";
    }

    @PostMapping("/probar")
    public String probar(RedirectAttributes ra) {
        try {
            afipService.probarConexion();
            ra.addFlashAttribute("mensaje", "Conexión de prueba con ARCA realizada correctamente");
        } catch (Exception e) {
            ra.addFlashAttribute("error", mensaje(e));
        }
        return "redirect:/facturacion";
    }

    @PostMapping("/emitir/{ventaId}")
    public String emitir(@PathVariable Long ventaId, RedirectAttributes ra) {
        try {
            Venta venta = afipService.facturarConAfip(ventaId);
            ra.addFlashAttribute("mensaje", "Comprobante de prueba autorizado. CAE: " + venta.getCae());
        } catch (Exception e) {
            ra.addFlashAttribute("error", mensaje(e));
        }
        return "redirect:/facturacion";
    }

    @GetMapping("/{ventaId}/pdf")
    public void pdf(@PathVariable Long ventaId, HttpServletResponse response) {
        Venta venta = ventaRepository.findById(ventaId)
                .orElseThrow(() -> new IllegalArgumentException("Venta no encontrada"));
        if (venta.getCae() == null || venta.getCae().isBlank()) {
            throw new IllegalStateException("La venta todavía no tiene un comprobante autorizado");
        }
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "inline; filename=factura-" + venta.getCodigo() + ".pdf");
        facturaPdfService.generar(venta, response);
    }

    private String mensaje(Exception e) {
        Throwable causa = e;
        while (causa.getCause() != null && causa.getMessage() != null
                && (causa.getMessage().isBlank() || causa.getMessage().startsWith("I/O error"))) {
            causa = causa.getCause();
        }
        return causa.getMessage() == null ? "No se pudo completar la operación" : causa.getMessage();
    }
}

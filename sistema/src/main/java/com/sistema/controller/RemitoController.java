package com.sistema.controller;

import com.sistema.model.*;
import com.sistema.repository.ClienteRepository;
import com.sistema.repository.VentaRepository;
import com.sistema.service.RemitoImpresionService;
import com.sistema.service.RemitoService;
import com.sistema.service.ConfiguracionDocumentoService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.util.List;

@Controller
@RequestMapping("/remitos")
public class RemitoController {

    private final RemitoService remitoService;
    private final ClienteRepository clienteRepo;
    private final RemitoImpresionService remitoImpresionService;
    private final VentaRepository ventaRepo;
    private final ConfiguracionDocumentoService configuracionDocumentoService;
    public RemitoController(RemitoService remitoService,
                            ClienteRepository clienteRepo,
                            RemitoImpresionService remitoImpresionService,
                            VentaRepository ventaRepo,
                            ConfiguracionDocumentoService configuracionDocumentoService) {
        this.remitoService = remitoService;
        this.clienteRepo = clienteRepo;
        this.remitoImpresionService = remitoImpresionService;
        this.ventaRepo = ventaRepo;
        this.configuracionDocumentoService = configuracionDocumentoService;
    }

    // ==========================================
    // LISTAR REMITOS
    // ==========================================
    @GetMapping
    public String listar(@RequestParam(required = false) String estado, Model model) {

        List<Remito> remitos;

        if (estado != null && !estado.isEmpty()) {
            try {
                Remito.Estado estadoEnum = Remito.Estado.valueOf(estado);
                remitos = remitoService.listarPorEstado(estadoEnum);
            } catch (IllegalArgumentException e) {
                remitos = remitoService.listarTodos();
            }
        } else {
            remitos = remitoService.listarTodos();
        }

        model.addAttribute("remitos", remitos);
        model.addAttribute("estadoFiltro", estado);

        return "remito/listar";
    }

    // ==========================================
    // FORMULARIO NUEVO REMITO
    // ==========================================
    @GetMapping("/nuevo")
    public String nuevo(Model model) {
        if (!configuracionDocumentoService.configurada()) {
            return "redirect:/datos-empresa?continuar=/remitos/nuevo";
        }
        model.addAttribute("clientes", clienteRepo.findAll());
        return "remito/nuevo";
    }

    // ==========================================
    // GUARDAR REMITO
    // ==========================================
    @PostMapping("/guardar")
    public String guardar(
            @RequestParam(required = false) Long clienteId,
            @RequestParam("productoTalleIds") List<Long> productoTalleIds,
            @RequestParam("cantidades") List<Integer> cantidades,
            @RequestParam(value = "precios", required = false) List<BigDecimal> precios,
            @RequestParam(value = "direccionEntrega", required = false) String direccionEntrega,
            @RequestParam(value = "observaciones", required = false) String observaciones,
            @RequestParam(value = "incluyePrecios", defaultValue = "false") Boolean incluyePrecios,
            @RequestParam(value = "descontarStock", defaultValue = "false") Boolean descontarStock,
            RedirectAttributes redirectAttributes) {

        if (!configuracionDocumentoService.configurada()) {
            return "redirect:/datos-empresa?continuar=/remitos/nuevo";
        }
        try {
            Cliente cliente = null;
            if (clienteId != null) {
                cliente = clienteRepo.findById(clienteId).orElse(null);
            }

            Remito remito = remitoService.crear(
                    cliente,
                    productoTalleIds,
                    cantidades,
                    precios,
                    direccionEntrega,
                    observaciones,
                    incluyePrecios,
                    descontarStock
            );

            redirectAttributes.addFlashAttribute("success",
                    "Remito creado exitosamente: " + remito.getCodigo());

            return "redirect:/remitos/imprimir/" + remito.getId();

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "Error al crear remito: " + e.getMessage());
            return "redirect:/remitos/nuevo";
        }
    }

    // ==========================================
    // VER DETALLE
    // ==========================================
    @GetMapping("/detalle/{id}")
    public String detalle(@PathVariable Long id, Model model) {
        Remito remito = remitoService.buscarPorId(id);
        model.addAttribute("remito", remito);
        return "remito/detalle";
    }

    // ==========================================
    // MARCAR COMO ENTREGADO
    // ==========================================
    @PostMapping("/entregar/{id}")
    public String marcarEntregado(
            @PathVariable Long id,
            @RequestParam(value = "descontarStock", defaultValue = "false") Boolean descontarStock,
            RedirectAttributes redirectAttributes) {

        try {
            remitoService.marcarComoEntregado(id, descontarStock);
            redirectAttributes.addFlashAttribute("success", "Remito marcado como entregado");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "Error al marcar como entregado: " + e.getMessage());
        }

        return "redirect:/remitos/detalle/" + id;
    }

    // ==========================================
    // CONVERTIR A VENTA
    // ==========================================
    @GetMapping("/convertir/{id}")
    public String formConvertir(@PathVariable Long id, Model model) {
        Remito remito = remitoService.buscarPorId(id);
        model.addAttribute("remito", remito);
        return "remito/convertir";
    }

    @PostMapping("/convertir/{id}")
    public String convertirAVenta(
            @PathVariable Long id,
            @RequestParam("formaPago") FormaPago formaPago,
            @RequestParam(value = "descontarStock", defaultValue = "true") Boolean descontarStock,
            RedirectAttributes redirectAttributes) {

        try {
            Venta venta = remitoService.convertirAVenta(id, formaPago, descontarStock);

            redirectAttributes.addFlashAttribute("success",
                    "Remito convertido a venta: " + venta.getCodigo());

            return "redirect:/ventas/ticket/" + venta.getId() + "/vista";

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "Error al convertir remito: " + e.getMessage());
            return "redirect:/remitos/detalle/" + id;
        }
    }

    // ==========================================
    // ANULAR REMITO
    // ==========================================
    @PostMapping("/anular/{id}")
    public String anular(
            @PathVariable Long id,
            @RequestParam(value = "devolverStock", defaultValue = "false") Boolean devolverStock,
            RedirectAttributes redirectAttributes) {

        try {
            remitoService.anular(id, devolverStock);
            redirectAttributes.addFlashAttribute("success", "Remito anulado correctamente");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "Error al anular remito: " + e.getMessage());
        }

        return "redirect:/remitos";
    }

    // ==========================================
    // IMPRIMIR REMITO (PDF)
    // ==========================================
    @GetMapping("/{id}/pdf")
    public void imprimirPdf(@PathVariable Long id, HttpServletRequest request,
                            HttpServletResponse response) {

        try {
            if (!configuracionDocumentoService.configurada()) {
                response.sendRedirect(request.getContextPath()
                        + "/datos-empresa?continuar=/remitos/" + id + "/pdf");
                return;
            }
            Remito remito = remitoService.buscarPorId(id);

            response.setContentType("application/pdf");
            response.setHeader("Content-Disposition",
                    "attachment; filename=Remito_" + remito.getCodigo() + ".pdf");

            OutputStream out = response.getOutputStream();
            remitoImpresionService.generarRemitoPdf(remito, out);
            out.flush();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    @GetMapping("/venta/{ventaId}/pdf")
    public void generarDesdeVenta(@PathVariable Long ventaId,
                                  HttpServletRequest request,
                                  HttpServletResponse response) {

        try {
            if (!configuracionDocumentoService.configurada()) {
                response.sendRedirect(request.getContextPath()
                        + "/datos-empresa?continuar=/remitos/venta/"
                        + ventaId + "/pdf");
                return;
            }
            Remito remito = remitoService.buscarPorVentaId(ventaId);

            if (remito == null) {

                Venta venta = ventaRepo.findById(ventaId)
                        .orElseThrow(() -> new RuntimeException("Venta no encontrada"));

                remito = remitoService.crearDesdeVenta(venta);
            }

            response.setContentType("application/pdf");
            response.setHeader("Content-Disposition",
                    "attachment; filename=Remito_" + remito.getCodigo() + ".pdf");

            OutputStream out = response.getOutputStream();
            remitoImpresionService.generarRemitoPdf(remito, out);
            out.flush();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

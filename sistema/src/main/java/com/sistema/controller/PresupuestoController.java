package com.sistema.controller;

import com.sistema.model.*;
import com.sistema.repository.*;
import com.sistema.service.PresupuestoPdfService;
import com.sistema.service.PresupuestoService;
import com.sistema.service.RemitoService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Controller
@RequestMapping("/presupuestos")
public class PresupuestoController {

    private final PresupuestoService presupuestoService;
    private final ProductoRepository productoRepo;
    private final ClienteRepository clienteRepo;
    private final PresupuestoRepository presupuestoRepo;
    private final PresupuestoPdfService presupuestoPdfService;
    private final RemitoService remitoService;

    public PresupuestoController(PresupuestoService presupuestoService,
                                 ProductoRepository productoRepo,
                                 ClienteRepository clienteRepo,
                                 PresupuestoRepository presupuestoRepo,
                                 PresupuestoPdfService presupuestoPdfService,
                                 RemitoService remitoService) {
        this.presupuestoService = presupuestoService;
        this.productoRepo = productoRepo;
        this.clienteRepo = clienteRepo;
        this.presupuestoRepo = presupuestoRepo;
        this.presupuestoPdfService = presupuestoPdfService;
        this.remitoService = remitoService;
    }

    // ==========================================
    // LISTAR PRESUPUESTOS
    // ==========================================
    @GetMapping
    public String listar(@RequestParam(required = false) String estado,
                         Model model) {

        List<Presupuesto> presupuestos;

        if (estado != null && !estado.isEmpty()) {
            EstadoPresupuesto estadoEnum = EstadoPresupuesto.valueOf(estado);
            presupuestos = presupuestoService.buscarPorEstado(estadoEnum);
        } else {
            presupuestos = presupuestoService.buscarTodos();
        }

        model.addAttribute("presupuestos", presupuestos);
        model.addAttribute("estados", EstadoPresupuesto.values());
        model.addAttribute("filtroEstado", estado);

        return "presupuesto/listar";
    }

    // ==========================================
    // FORM NUEVO PRESUPUESTO
    // ==========================================
    @GetMapping("/nuevo")
    public String nuevo(Model model) {
        model.addAttribute("productos", productoRepo.findAll());
        model.addAttribute("clientes", clienteRepo.findAll());

        return "presupuesto/form";
    }

    // ==========================================
    // CREAR PRESUPUESTO
    // ==========================================
    @PostMapping("/guardar")
    public String guardar(
            @RequestParam(required = false) Long clienteId,
            @RequestParam FormaPago formaPago,
            @RequestParam List<Long> productoIds,
            @RequestParam(required = false) List<Long> varianteIds,
            @RequestParam List<Integer> cantidades,
            @RequestParam(required = false) List<BigDecimal> descuentos,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaValidez,
            @RequestParam(required = false, defaultValue = "ARS") Presupuesto.Moneda moneda,
            @RequestParam(required = false) BigDecimal tipoCambio,
            @RequestParam(required = false) String notaTipoCambio,
            @RequestParam(required = false) List<BigDecimal> precios,
            RedirectAttributes ra
    ) {
        try {
            Presupuesto presupuesto = presupuestoService.crear(
                    clienteId,
                    formaPago,
                    productoIds,
                    varianteIds,
                    cantidades,
                    descuentos,
                    fechaValidez,
                    moneda,
                    tipoCambio,
                    notaTipoCambio,
                    precios
            );

            ra.addFlashAttribute("mensaje", "Presupuesto creado: " + presupuesto.getCodigo());
            return "redirect:/presupuestos/detalle/" + presupuesto.getId() + "?pdf=true";

        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/presupuestos/nuevo";
        }
    }





    @GetMapping("/{id}")
    public String detalle(@PathVariable Long id, Model model) {

        Presupuesto p = presupuestoService.buscarPorId(id);

        model.addAttribute("presupuesto", p);
        model.addAttribute("fechaPresupuestoFmt", p.getFechaFormateada());

        return "presupuesto/detallePresupuesto";
    }

    @GetMapping("/detalle/{id}")
    public String verPresupuesto(@PathVariable Long id, Model model) {

        Presupuesto presupuesto = presupuestoRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Presupuesto no encontrada"));

        // ✅ Esto devuelve neto, IVA, total y el map de ivas
        TotalesConIva totales = presupuestoService.calcularTotalesConIvaMap(presupuesto);

        model.addAttribute("presupuesto", presupuesto);
        model.addAttribute("totales", totales);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");

        model.addAttribute("fechaPresupuestoFmt",
                presupuesto.getFecha().format(formatter));

        if (presupuesto.getFechaValidez() != null) {
            model.addAttribute("fechaValidezFmt",
                    presupuesto.getFechaValidez().format(formatter));
        } else {
            model.addAttribute("fechaValidezFmt",
                    presupuesto.getFecha().plusDays(30).format(formatter));
        }

        return "presupuesto/detallePresupuesto";
    }

    // ==========================================
    // BUSCAR POR CÓDIGO
    // ==========================================
    @GetMapping("/buscar")
    public String buscarPorCodigo(@RequestParam String codigo,
                                  RedirectAttributes ra) {
        try {
            Presupuesto presupuesto = presupuestoService.buscarPorCodigo(codigo);
            return "redirect:/presupuestos/" + presupuesto.getId();

        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/presupuestos";
        }
    }

    // ==========================================
    // FORM EDITAR
    // ==========================================
    @GetMapping("/{id}/editar")
    public String editar(@PathVariable Long id,
                         Model model,
                         RedirectAttributes ra) {
        try {
            Presupuesto presupuesto = presupuestoService.buscarPorId(id);

            if (presupuesto.getEstado() != EstadoPresupuesto.PENDIENTE &&
                    presupuesto.getEstado() != EstadoPresupuesto.APROBADO) {
                ra.addFlashAttribute("error",
                        "Solo se puede editar un presupuesto PENDIENTE o APROBADO");
                return "redirect:/presupuestos/detalle/" + id;
            }

            model.addAttribute("presupuesto", presupuesto);
            model.addAttribute("productos", productoRepo.findAll());
            model.addAttribute("clientes", clienteRepo.findAll());

            return "presupuesto/form";

        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/presupuestos";
        }
    }

    // ==========================================
    // ACTUALIZAR PRESUPUESTO
    // ==========================================
    @PostMapping("/{id}/actualizar")
    public String actualizar(
            @PathVariable Long id,
            @RequestParam(required = false) Long clienteId,
            @RequestParam List<Long> productoIds,
            @RequestParam(required = false) List<Long> varianteIds,
            @RequestParam List<Integer> cantidades,
            @RequestParam(required = false) List<BigDecimal> descuentos,
            @RequestParam(required = false) List<BigDecimal> precios,
            @RequestParam(required = false) List<Long> actualizarPrecioProducto,
            @RequestParam FormaPago formaPago,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaValidez,
            @RequestParam(required = false, defaultValue = "ARS") Presupuesto.Moneda moneda,
            @RequestParam(required = false) BigDecimal tipoCambio,
            @RequestParam(required = false) String notaTipoCambio,
            RedirectAttributes ra) {

        try {
            presupuestoService.actualizar(
                    id, clienteId, productoIds, varianteIds, cantidades,
                    descuentos, precios, actualizarPrecioProducto, formaPago, fechaValidez, moneda, tipoCambio, notaTipoCambio);

            ra.addFlashAttribute("mensaje", "Presupuesto actualizado exitosamente");
            return "redirect:/presupuestos/detalle/" + id;

        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/presupuestos/" + id + "/editar";
        }
    }

    // ==========================================
    // APROBAR → CREAR VENTA
    // ==========================================
    @PostMapping("/aprobar")
    public String aprobar(@RequestParam Long id,
                          @RequestParam(value = "generarRemito", defaultValue = "false") Boolean generarRemito,
                          RedirectAttributes ra) {

        try {
            Venta venta = presupuestoService.aprobar(id);
            if (generarRemito) {
                Remito remito = remitoService.crearDesdeVenta(venta);

                ra.addFlashAttribute("mensaje",
                        "Venta generada: " + venta.getCodigo());

                return "redirect:/ventas/detalle/" + venta.getId() + "?remitoId=" + remito.getId();
            }

            ra.addFlashAttribute("mensaje",
                    "Presupuesto aprobado. Venta generada: " + venta.getCodigo());

            return "redirect:/ventas/detalle/" + venta.getId();

        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/presupuestos/" + id;
        }
    }




    // ==========================================
    // RECHAZAR
    // ==========================================
    @PostMapping("/rechazar")
    public String rechazar(@RequestParam Long id,
                           RedirectAttributes ra) {

        try {
            presupuestoService.rechazar(id);
            ra.addFlashAttribute("mensaje",
                    "Presupuesto rechazado correctamente");

        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/presupuestos/" + id;
    }



    // ==========================================
    // ELIMINAR (solo PENDIENTE)
    // ==========================================

    @GetMapping("/{id}/pdf")
    public void generarPdf(
            @PathVariable Long id,
            HttpServletResponse response
    ) {
        try {

        Presupuesto presupuesto = presupuestoService.buscarPorId(id);

        response.setContentType("application/pdf");
        response.setHeader(
                "Content-Disposition",
                "inline; filename=presupuesto_" + presupuesto.getCodigo() + ".pdf"
        );

        presupuestoPdfService.generarPdf(
                presupuesto,
                response.getOutputStream()
        );
    } catch (Exception e) {
        throw new RuntimeException("Error generando PDF", e);
    }
    }

    }

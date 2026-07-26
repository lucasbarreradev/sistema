package com.sistema.controller;

import com.sistema.model.*;
import com.sistema.repository.*;
import com.sistema.service.RemitoService;
import com.sistema.service.VentaService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/ventas")
public class VentaController {

    private final VentaService ventaService;
    private final ProductoRepository productoRepo;
    private final ClienteRepository clienteRepo;
    private final VentaRepository ventaRepo;
    private final VentaItemRepository ventaItemRepo;
    private final RemitoService remitoService;
    private final RemitoRepository remitoRepo;
    private final ProductoVarianteRepository varianteRepo;

    public VentaController(VentaService ventaService,
                           ProductoRepository productoRepo,
                           ClienteRepository clienteRepo,
                           VentaRepository ventaRepo,
                           VentaItemRepository ventaItemRepo,
                           RemitoService remitoService,
                           RemitoRepository remitoRepo,
                           ProductoVarianteRepository varianteRepo) {
        this.ventaService = ventaService;
        this.productoRepo = productoRepo;
        this.clienteRepo = clienteRepo;
        this.ventaRepo = ventaRepo;
        this.ventaItemRepo = ventaItemRepo;
        this.remitoService = remitoService;
        this.remitoRepo = remitoRepo;
        this.varianteRepo = varianteRepo;
    }

    // ==========================================
    // LISTAR VENTAS
    @GetMapping
    public String listarVentas(Model model) {
        model.addAttribute("ventas", ventaService.listarVentasNoAnuladas());
        return "venta/listar";
    }
    // ==========================================
    // ==========================================
    // FORM NUEVA VENTA
    // ==========================================
    @GetMapping("/nueva")
    public String nuevaVenta(
            @RequestParam(required = false) Long clienteId,
            Model model) {

        Venta venta = new Venta();

        if (clienteId != null) {
            Cliente cliente = clienteRepo.findById(clienteId).orElse(null);
            venta.setCliente(cliente);
        }

        model.addAttribute("venta", venta);
        model.addAttribute("productos", productoRepo.findAll());
        model.addAttribute("clientes", clienteRepo.findAll());
        return "venta/form";
    }



    // ==========================================
    // CREAR VENTA DIRECTA
    // ==========================================
    @PostMapping("/guardar")
    public String guardarVenta(
            @RequestParam(required = false) Long clienteId,
            @RequestParam FormaPago formaPago,
            @RequestParam(required = false) String nota,
            @RequestParam(required = false) List<Long> productoIds,
            @RequestParam(required = false) List<Long> varianteIds,
            @RequestParam(required = false) List<Integer> cantidades,
            @RequestParam(required = false) List<BigDecimal> descuentos,
            @RequestParam(value = "generarRemito", defaultValue = "false") Boolean generarRemito,
            RedirectAttributes ra
    ) {




        try {

            if (productoIds == null || productoIds.isEmpty()) {
                throw new IllegalArgumentException("Debe agregar al menos un producto");
            }

            if (cantidades == null || cantidades.isEmpty()) {
                throw new IllegalArgumentException("Debe ingresar cantidades");
            }

            if (productoIds.size() != cantidades.size()) {
                throw new IllegalArgumentException("Datos inválidos de productos");
            }

            List<VentaItem> items = new ArrayList<>();

            for (int i = 0; i < productoIds.size(); i++) {

                Producto producto = productoRepo.findById(productoIds.get(i))
                        .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado"));

                VentaItem item = new VentaItem();
                item.setProducto(producto);
                if (varianteIds != null && i < varianteIds.size() && varianteIds.get(i) != null && varianteIds.get(i) > 0) {
                    ProductoVariante variante = varianteRepo.findById(varianteIds.get(i))
                            .orElseThrow(() -> new IllegalArgumentException("Variante no encontrada"));
                    item.setVariante(variante);
                }
                item.setCantidad(cantidades.get(i));


                if (descuentos != null && descuentos.size() == productoIds.size()) {
                    item.setDescuentoPct(descuentos.get(i));
                } else {
                    item.setDescuentoPct(BigDecimal.ZERO);
                }


                items.add(item);
            }


            Venta venta = ventaService.crearVentaDirecta(
                    clienteId,
                    items,
                    formaPago,
                    nota
            );

            if (generarRemito) {
                Remito remito = remitoService.crearDesdeVenta(venta);

                ra.addFlashAttribute("mensaje", "Venta realizada correctamente");

                return "redirect:/ventas?remitoId=" + remito.getId();
            }

            ra.addFlashAttribute("mensaje", "Venta realizada correctamente");
            return "redirect:/ventas";

        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/ventas/nueva";
        }
    }


    // ==========================================
    // CREAR VENTA DESDE PRESUPUESTO
    // ==========================================
    @PostMapping("/desde-presupuesto/{id}")
    public String venderDesdePresupuesto(
            @PathVariable Long id,
            @RequestParam FormaPago formaPago,
            @RequestParam(required = false) String nota,
            @RequestParam(value = "generarRemito", defaultValue = "false") Boolean generarRemito,
            RedirectAttributes ra
    ) {
        try {
            Venta venta = ventaService.crearDesdePresupuesto(id, formaPago);
            if (generarRemito) {
                Remito remito = remitoService.crearDesdeVenta(venta);
                return "redirect:/remitos/" + remito.getId() + "/pdf";
            }
            ra.addFlashAttribute("mensaje", "Venta creada desde presupuesto");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/ventas";
    }

    // ==========================================
    // VER DETALLE DE VENTA
    // ==========================================

    @GetMapping("/detalle/{id}")
    public String verVenta(@PathVariable Long id, Model model) {

        Venta venta = ventaRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Venta no encontrada"));
        List<Remito> remitos = remitoRepo.findByVentaId(venta.getId());
        // ✅ Esto devuelve neto, IVA, total y el map de ivas
        TotalesConIva totales = ventaService.calcularTotalesConIvaMap(venta);

        model.addAttribute("venta", venta);
        model.addAttribute("totales", totales);
        model.addAttribute("remitos", remitos);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");

        model.addAttribute("fechaVentaFmt",
                venta.getFechaVenta().format(formatter));

        return "venta/detalleVenta";
    }


    @PostMapping("/anular")
    public String anularVenta(
            @RequestParam Long id,
            RedirectAttributes ra
    ) {
        try {
            ventaService.anularVenta(id);
            ra.addFlashAttribute("mensaje", "Venta anulada correctamente");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/ventas";
    }







}

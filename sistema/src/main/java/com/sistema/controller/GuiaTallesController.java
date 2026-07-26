package com.sistema.controller;

import com.sistema.service.MercadoLibreGuiaTallesService;
import com.sistema.service.ProductoService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/guias-talles")
public class GuiaTallesController {
    private final MercadoLibreGuiaTallesService guiaService;
    private final ProductoService productoService;

    public GuiaTallesController(MercadoLibreGuiaTallesService guiaService, ProductoService productoService) {
        this.guiaService = guiaService;
        this.productoService = productoService;
    }

    @GetMapping
    public String index(@RequestParam(required = false) Long productoId, Model model) {
        model.addAttribute("productos", productoService.getProductos());
        if (productoId != null) {
            try { model.addAttribute("contexto", guiaService.contexto(productoId)); }
            catch (Exception e) { model.addAttribute("error", e.getMessage()); }
        }
        return "guia_talles/index";
    }

    @PostMapping("/buscar")
    public String buscar(@RequestParam Long productoId, @RequestParam MultiValueMap<String, String> parametros,
                         Model model) {
        model.addAttribute("productos", productoService.getProductos());
        try {
            model.addAttribute("contexto", guiaService.contexto(productoId));
            model.addAttribute("guias", guiaService.buscar(productoId, parametros));
            model.addAttribute("valores", parametros.toSingleValueMap());
        } catch (Exception e) { model.addAttribute("error", e.getMessage()); }
        return "guia_talles/index";
    }

    @PostMapping("/preparar")
    public String preparar(@RequestParam Long productoId, @RequestParam String nombre,
                           @RequestParam(defaultValue = "BODY_MEASURE") String tipoMedida,
                           @RequestParam MultiValueMap<String, String> parametros, Model model) {
        try {
            model.addAttribute("constructor", guiaService.preparar(productoId, nombre, tipoMedida, parametros));
            model.addAttribute("parametros", parametros.toSingleValueMap());
            return "guia_talles/construir";
        } catch (Exception e) {
            model.addAttribute("productos", productoService.getProductos());
            model.addAttribute("error", e.getMessage());
            try { model.addAttribute("contexto", guiaService.contexto(productoId)); } catch (Exception ignored) {}
            return "guia_talles/index";
        }
    }

    @PostMapping("/crear")
    public String crear(@RequestParam Long productoId, @RequestParam String nombre,
                        @RequestParam(defaultValue = "BODY_MEASURE") String tipoMedida,
                        @RequestParam String atributoPrincipal,
                        @RequestParam MultiValueMap<String, String> parametros,
                        Model model, RedirectAttributes ra) {
        try {
            MercadoLibreGuiaTallesService.Guia guia = guiaService.crear(productoId, nombre, tipoMedida,
                    atributoPrincipal, parametros);
            ra.addFlashAttribute("mensaje", "Guía creada correctamente. Revise las filas y asígnela al producto.");
            return "redirect:/guias-talles/" + guia.id() + "?productoId=" + productoId;
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            try {
                model.addAttribute("constructor", guiaService.preparar(productoId, nombre, tipoMedida, parametros));
                model.addAttribute("parametros", parametros.toSingleValueMap());
                model.addAttribute("atributoPrincipalSeleccionado", atributoPrincipal);
                return "guia_talles/construir";
            } catch (Exception prepararError) {
                ra.addFlashAttribute("error", e.getMessage());
                return "redirect:/guias-talles?productoId=" + productoId;
            }
        }
    }

    @GetMapping("/{chartId}")
    public String detalle(@PathVariable String chartId, @RequestParam(required = false) Long productoId, Model model) {
        try {
            model.addAttribute("guia", guiaService.consultar(chartId));
            if (productoId != null) model.addAttribute("producto", productoService.getProductoById(productoId).orElse(null));
        } catch (Exception e) { model.addAttribute("error", e.getMessage()); }
        return "guia_talles/detalle";
    }

    @PostMapping("/asignar")
    public String asignar(@RequestParam Long productoId, @RequestParam String chartId,
                          @RequestParam(required = false) String rowId, RedirectAttributes ra) {
        try {
            guiaService.asignar(productoId, chartId, rowId);
            ra.addFlashAttribute("mensaje", "Guía asignada al producto correctamente");
        } catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/guias-talles/" + chartId + "?productoId=" + productoId;
    }

    @PostMapping("/desasignar")
    public String desasignar(@RequestParam Long productoId, RedirectAttributes ra) {
        try { guiaService.desasignar(productoId); ra.addFlashAttribute("mensaje", "Guía desasignada"); }
        catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/guias-talles?productoId=" + productoId;
    }
}

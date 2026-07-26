package com.sistema.controller;

import com.sistema.model.Tenant;
import com.sistema.service.TenantService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/tenants")
@PreAuthorize("hasRole('SUPERADMIN')")
public class TenantController {
    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @GetMapping
    public String listar(Model model) {
        model.addAttribute("tenants", tenantService.listar());
        return "tenant/listar";
    }

    @GetMapping("/nuevo")
    public String nuevo() { return "tenant/form"; }

    @PostMapping
    public String crear(@RequestParam String nombreNegocio,
                        @RequestParam(required = false) String codigo,
                        @RequestParam String username,
                        @RequestParam String password,
                        @RequestParam String nombreAdmin,
                        @RequestParam String apellidoAdmin,
                        RedirectAttributes ra) {
        try {
            Tenant tenant = tenantService.crear(nombreNegocio, codigo, username, password, nombreAdmin, apellidoAdmin);
            ra.addFlashAttribute("mensaje", "Negocio " + tenant.getNombre() + " creado correctamente");
            return "redirect:/tenants";
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/tenants/nuevo";
        }
    }

    @PostMapping("/{id}/estado")
    public String estado(@PathVariable Long id, RedirectAttributes ra) {
        try {
            tenantService.cambiarEstado(id);
            ra.addFlashAttribute("mensaje", "Estado del negocio actualizado");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/tenants";
    }
}

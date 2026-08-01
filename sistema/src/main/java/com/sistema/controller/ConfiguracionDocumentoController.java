package com.sistema.controller;

import com.sistema.model.ConfiguracionDocumento;
import com.sistema.service.ConfiguracionDocumentoService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/datos-empresa")
public class ConfiguracionDocumentoController {
    private final ConfiguracionDocumentoService service;

    public ConfiguracionDocumentoController(
            ConfiguracionDocumentoService service) {
        this.service = service;
    }

    @GetMapping
    public String formulario(@RequestParam(required = false) String continuar,
                             Model model) {
        if (!model.containsAttribute("configuracion")) {
            model.addAttribute("configuracion",
                    service.obtener().orElseGet(ConfiguracionDocumento::new));
        }
        model.addAttribute("continuar", continuarSeguro(continuar));
        model.addAttribute("primeraConfiguracion", !service.configurada());
        return "configuracion/documentos";
    }

    @PostMapping
    public String guardar(@ModelAttribute ConfiguracionDocumento configuracion,
                          @RequestParam(required = false) MultipartFile logo,
                          @RequestParam(defaultValue = "false") boolean quitarLogo,
                          @RequestParam(required = false) String continuar,
                          RedirectAttributes ra) {
        String destino = continuarSeguro(continuar);
        try {
            service.guardar(configuracion, logo, quitarLogo);
            ra.addFlashAttribute("mensaje",
                    "Datos de la empresa guardados correctamente");
            return "redirect:" + (destino == null ? "/datos-empresa" : destino);
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
            ra.addFlashAttribute("configuracion", configuracion);
            return "redirect:/datos-empresa"
                    + (destino == null ? "" : "?continuar=" + destino);
        }
    }

    @GetMapping("/logo")
    @ResponseBody
    public ResponseEntity<byte[]> logo() {
        return service.obtener()
                .filter(ConfiguracionDocumento::tieneLogo)
                .map(config -> {
                    MediaType tipo;
                    try {
                        tipo = MediaType.parseMediaType(
                                config.getLogoTipoContenido());
                    } catch (Exception e) {
                        tipo = MediaType.APPLICATION_OCTET_STREAM;
                    }
                    return ResponseEntity.ok().contentType(tipo)
                            .body(config.getLogoContenido());
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private String continuarSeguro(String continuar) {
        if (continuar == null || continuar.isBlank()) return null;
        String valor = continuar.trim();
        if (!valor.startsWith("/") || valor.startsWith("//")
                || valor.contains("://") || valor.contains("\r")
                || valor.contains("\n")) {
            return null;
        }
        return valor;
    }
}

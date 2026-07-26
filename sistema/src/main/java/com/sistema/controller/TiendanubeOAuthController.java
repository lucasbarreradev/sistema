package com.sistema.controller;

import com.sistema.service.RegistroWebhooksService;
import com.sistema.service.TiendanubeCredencialesService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/canales/tiendanube")
@PreAuthorize("hasRole('ADMIN')")
public class TiendanubeOAuthController {
    private static final String SESSION_INICIADA = "tiendanube_oauth_iniciada";
    private final TiendanubeCredencialesService credenciales;
    private final RegistroWebhooksService webhooks;

    public TiendanubeOAuthController(TiendanubeCredencialesService credenciales, RegistroWebhooksService webhooks) {
        this.credenciales = credenciales;
        this.webhooks = webhooks;
    }

    @GetMapping("/conectar")
    public ResponseEntity<Void> conectar(HttpSession session) {
        if (!credenciales.aplicacionConfigurada()) {
            throw new IllegalStateException("Configure APP ID, Client Secret, URL de retorno y clave de cifrado de Tiendanube");
        }
        session.setAttribute(SESSION_INICIADA, Boolean.TRUE);
        String url = "https://www.tiendanube.com/apps/" + credenciales.getClientId() + "/authorize";
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, url).build();
    }

    @GetMapping("/callback")
    public String callback(@RequestParam(required = false) String code,
                           @RequestParam(required = false) String error,
                           HttpSession session, RedirectAttributes ra) {
        Object iniciada = session.getAttribute(SESSION_INICIADA);
        session.removeAttribute(SESSION_INICIADA);
        try {
            if (!Boolean.TRUE.equals(iniciada)) throw new IllegalStateException("La respuesta no corresponde a una conexión iniciada desde el sistema");
            if (error != null) throw new IllegalStateException("La autorización fue cancelada o rechazada por Tiendanube");
            String storeId = credenciales.vincularConCodigo(code);
            webhooks.registrarTiendaNubeAhora();
            ra.addFlashAttribute("mensaje", "Cuenta de Tiendanube conectada correctamente (tienda " + storeId + ")");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage() == null ? "No se pudo conectar Tiendanube" : e.getMessage());
        }
        return "redirect:/canales";
    }

    @PostMapping("/desconectar")
    public String desconectar(RedirectAttributes ra) {
        credenciales.desconectar();
        ra.addFlashAttribute("mensaje", "Cuenta de Tiendanube desconectada");
        return "redirect:/canales";
    }
}

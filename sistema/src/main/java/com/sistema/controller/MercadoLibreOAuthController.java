package com.sistema.controller;

import com.sistema.service.MercadoLibreTokenService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.security.SecureRandom;
import java.util.Base64;

@Controller
@RequestMapping("/canales/mercadolibre")
@PreAuthorize("hasRole('ADMIN')")
public class MercadoLibreOAuthController {
    private static final String SESSION_STATE = "mercadolibre_oauth_state";
    private final SecureRandom secureRandom = new SecureRandom();
    private final MercadoLibreTokenService tokenService;
    private final String clientId;
    private final String redirectUri;

    public MercadoLibreOAuthController(MercadoLibreTokenService tokenService,
            @Value("${integraciones.mercadolibre.client-id:}") String clientId,
            @Value("${integraciones.mercadolibre.redirect-uri:}") String redirectUri) {
        this.tokenService = tokenService;
        this.clientId = clientId;
        this.redirectUri = redirectUri;
    }

    @GetMapping("/conectar")
    public ResponseEntity<Void> conectar(HttpSession session) {
        if (clientId.isBlank() || redirectUri.isBlank()) {
            throw new IllegalStateException("Configure MERCADOLIBRE_CLIENT_ID y MERCADOLIBRE_REDIRECT_URI antes de conectar la cuenta");
        }
        byte[] aleatorio = new byte[32];
        secureRandom.nextBytes(aleatorio);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(aleatorio);
        session.setAttribute(SESSION_STATE, state);
        URI autorizacion = UriComponentsBuilder.fromUriString("https://auth.mercadolibre.com.ar/authorization")
                .queryParam("response_type", "code").queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri).queryParam("state", state)
                .build().encode().toUri();
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, autorizacion.toString()).build();
    }

    @GetMapping("/callback")
    public String callback(@RequestParam(required = false) String code,
                           @RequestParam(required = false) String state,
                           @RequestParam(required = false) String error,
                           HttpSession session, RedirectAttributes ra) {
        String esperado = (String) session.getAttribute(SESSION_STATE);
        session.removeAttribute(SESSION_STATE);
        try {
            if (error != null) throw new IllegalStateException("La autorización fue cancelada o rechazada por Mercado Libre");
            if (esperado == null || state == null || !esperado.equals(state)) {
                throw new IllegalStateException("La respuesta de Mercado Libre no corresponde al intento de conexión iniciado");
            }
            Long userId = tokenService.vincularConCodigo(code, redirectUri);
            ra.addFlashAttribute("mensaje", "Cuenta de Mercado Libre conectada correctamente"
                    + (userId == null ? "" : " (usuario " + userId + ")"));
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage() == null ? "No se pudo conectar Mercado Libre" : e.getMessage());
        }
        return "redirect:/canales";
    }

    @PostMapping("/desconectar")
    public String desconectar(RedirectAttributes ra) {
        tokenService.desconectar();
        ra.addFlashAttribute("mensaje", "Cuenta de Mercado Libre desconectada");
        return "redirect:/canales";
    }
}

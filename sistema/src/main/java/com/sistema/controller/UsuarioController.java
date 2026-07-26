// src/main/java/com/sistema/controller/UsuarioController.java
package com.sistema.controller;

import com.sistema.model.Usuario;
import com.sistema.repository.UsuarioRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.sistema.tenant.TenantContext;

@Controller
@RequestMapping("/usuarios")
@PreAuthorize("hasRole('ADMIN')") // Solo ADMIN puede gestionar usuarios
public class UsuarioController {

    private final UsuarioRepository usuarioRepo;
    private final PasswordEncoder passwordEncoder;

    public UsuarioController(UsuarioRepository usuarioRepo,
                             PasswordEncoder passwordEncoder) {
        this.usuarioRepo = usuarioRepo;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    public String listar(Model model) {
        model.addAttribute("usuarios", usuarioRepo.findAllByTenantIdOrderByUsernameAsc(TenantContext.require()));
        model.addAttribute("roles", java.util.List.of(Usuario.Rol.ADMIN, Usuario.Rol.EMPLEADO));
        return "usuario/listar";
    }

    @GetMapping("/nuevo")
    public String nuevo(Model model) {
        model.addAttribute("roles", java.util.List.of(Usuario.Rol.ADMIN, Usuario.Rol.EMPLEADO));
        return "usuario/form";
    }

    @PostMapping("/guardar")
    public String guardar(
            @RequestParam String username,
            @RequestParam String password,
            @RequestParam String nombre,
            @RequestParam String apellido,
            @RequestParam(required = false) String[] roles,
            RedirectAttributes ra) {

        try {
            // Validar username único
            if (usuarioRepo.existsByUsername(username)) {
                ra.addFlashAttribute("error", "El usuario ya existe");
                return "redirect:/usuarios/nuevo";
            }

            // Crear usuario
            Usuario usuario = new Usuario();
            usuario.setUsername(username);
            usuario.setPassword(passwordEncoder.encode(password)); // Encriptar
            usuario.setNombre(nombre);
            usuario.setApellido(apellido);
            usuario.setActivo(true);
            usuario.setTenantId(TenantContext.require());

            // Agregar roles
            if (roles != null) {
                for (String rol : roles) {
                    Usuario.Rol valor = Usuario.Rol.valueOf(rol);
                    if (valor == Usuario.Rol.SUPERADMIN) throw new IllegalArgumentException("Rol no permitido");
                    usuario.getRoles().add(valor);
                }
            } else {
                // Por defecto EMPLEADO
                usuario.getRoles().add(Usuario.Rol.EMPLEADO);
            }

            usuarioRepo.save(usuario);

            ra.addFlashAttribute("mensaje", "Usuario creado exitosamente");
            return "redirect:/usuarios";

        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/usuarios/nuevo";
        }
    }

    @PostMapping("/{id}/activar")
    public String activar(@PathVariable Long id, RedirectAttributes ra) {
        Usuario usuario = usuarioRepo.findByIdAndTenantId(id, TenantContext.require())
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        usuario.setActivo(!usuario.getActivo());
        usuarioRepo.save(usuario);

        ra.addFlashAttribute("mensaje",
                usuario.getActivo() ? "Usuario activado" : "Usuario desactivado");
        return "redirect:/usuarios";
    }
}

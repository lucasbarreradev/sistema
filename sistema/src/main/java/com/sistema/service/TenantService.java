package com.sistema.service;

import com.sistema.model.Tenant;
import com.sistema.model.Usuario;
import com.sistema.repository.TenantRepository;
import com.sistema.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

@Service
public class TenantService {
    private final TenantRepository tenantRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public TenantService(TenantRepository tenantRepository, UsuarioRepository usuarioRepository,
                         PasswordEncoder passwordEncoder) {
        this.tenantRepository = tenantRepository;
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<Tenant> listar() { return tenantRepository.findAllByOrderByNombreAsc(); }

    @Transactional
    public Tenant crear(String nombreNegocio, String codigo, String username, String password,
                        String nombreAdmin, String apellidoAdmin) {
        if (nombreNegocio == null || nombreNegocio.isBlank()) throw new IllegalArgumentException("Ingrese el nombre del negocio");
        String codigoNormalizado = normalizarCodigo(codigo == null || codigo.isBlank() ? nombreNegocio : codigo);
        if (tenantRepository.findByCodigoIgnoreCase(codigoNormalizado).isPresent()) {
            throw new IllegalArgumentException("Ya existe un tenant con ese código");
        }
        if (usuarioRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("El nombre de usuario ya está utilizado");
        }
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("La contraseña debe tener al menos 8 caracteres");
        }
        Tenant tenant = new Tenant();
        tenant.setNombre(nombreNegocio.trim());
        tenant.setCodigo(codigoNormalizado);
        tenant = tenantRepository.save(tenant);

        Usuario admin = new Usuario(username.trim(), passwordEncoder.encode(password),
                nombreAdmin.trim(), apellidoAdmin.trim(), Usuario.Rol.ADMIN);
        admin.setTenantId(tenant.getId());
        usuarioRepository.save(admin);
        return tenant;
    }

    @Transactional
    public void cambiarEstado(Long id) {
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Tenant no encontrado"));
        if (tenant.getId() == 1L && Boolean.TRUE.equals(tenant.getActivo())) {
            throw new IllegalArgumentException("El tenant principal no puede desactivarse");
        }
        tenant.setActivo(!Boolean.TRUE.equals(tenant.getActivo()));
        tenantRepository.save(tenant);
    }

    private String normalizarCodigo(String valor) {
        String normalizado = Normalizer.normalize(valor, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (normalizado.isBlank()) throw new IllegalArgumentException("El código del tenant no es válido");
        return normalizado.length() > 80 ? normalizado.substring(0, 80) : normalizado;
    }
}

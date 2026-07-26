package com.sistema.security;

import com.sistema.model.Usuario;
import com.sistema.repository.UsuarioRepository;
import com.sistema.repository.TenantRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepo;
    private final TenantRepository tenantRepo;

    public CustomUserDetailsService(UsuarioRepository usuarioRepo, TenantRepository tenantRepo) {
        this.usuarioRepo = usuarioRepo;
        this.tenantRepo = tenantRepo;
    }

    @Override
    public UserDetails loadUserByUsername(String username)
            throws UsernameNotFoundException {

        Usuario usuario = usuarioRepo.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Usuario no encontrado: " + username));

        if (!usuario.getActivo()) {
            throw new UsernameNotFoundException(
                    "Usuario inactivo: " + username);
        }
        com.sistema.model.Tenant tenant = tenantRepo.findById(usuario.getTenantId())
                .filter(t -> Boolean.TRUE.equals(t.getActivo()))
                .orElseThrow(() -> new UsernameNotFoundException("Negocio inactivo o inexistente"));


        // Convertir roles a authorities
        Set<GrantedAuthority> authorities = usuario.getRoles().stream()
                .map(rol -> new SimpleGrantedAuthority("ROLE_" + rol.name()))
                .collect(Collectors.toSet());

        return new TenantUserDetails(usuario.getUsername(), usuario.getPassword(), true,
                authorities, tenant.getId(), tenant.getNombre());

    }



}

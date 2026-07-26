package com.sistema.config;

import com.sistema.model.Usuario;
import com.sistema.repository.UsuarioRepository;
import com.sistema.repository.TenantRepository;
import com.sistema.model.Tenant;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataLoader {

    @Bean
    CommandLineRunner initDatabase(
            UsuarioRepository usuarioRepo,
            TenantRepository tenantRepo,
            PasswordEncoder passwordEncoder,
            Environment env) {

        return args -> {
            Tenant tenant = tenantRepo.findById(1L).orElseGet(() -> {
                Tenant nuevo = new Tenant();
                nuevo.setId(1L);
                nuevo.setCodigo("principal");
                nuevo.setNombre("Negocio principal");
                return tenantRepo.save(nuevo);
            });

            // =========================
            // ADMIN
            // =========================
            String adminUser = env.getProperty("app.admin.user");
            String adminPass = env.getProperty("app.admin.pass");

            if (adminUser != null && adminPass != null &&
                    !usuarioRepo.existsByUsername(adminUser)) {

                Usuario admin = new Usuario(
                        adminUser,
                        passwordEncoder.encode(adminPass),
                        "Administrador",
                        "Sistema",
                        Usuario.Rol.ADMIN
                );
                admin.setTenantId(tenant.getId());
                admin.getRoles().add(Usuario.Rol.SUPERADMIN);
                usuarioRepo.save(admin);
            }
            usuarioRepo.findByUsername(adminUser).ifPresent(admin -> {
                admin.setTenantId(tenant.getId());
                admin.getRoles().add(Usuario.Rol.ADMIN);
                admin.getRoles().add(Usuario.Rol.SUPERADMIN);
                usuarioRepo.save(admin);
            });


            String empUser = env.getProperty("app.empleado.user");
            String empPass = env.getProperty("app.empleado.pass");

            if (!usuarioRepo.existsByUsername(empUser)) {

                Usuario empleado = new Usuario(
                        empUser,
                        passwordEncoder.encode(empPass),
                        "Empleado",
                        "Sistema",
                        Usuario.Rol.EMPLEADO
                );
                empleado.setTenantId(tenant.getId());
                usuarioRepo.save(empleado);
            }
        };
    }


}

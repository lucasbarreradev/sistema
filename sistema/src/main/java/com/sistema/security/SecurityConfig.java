package com.sistema.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

@Configuration @EnableWebSecurity @EnableMethodSecurity // Habilita @PreAuthorize
public class SecurityConfig {
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(); }
    @Bean
    public AuthenticationManager authenticationManager( AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager(); }
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           TenantAuthenticationSuccessHandler successHandler,
                                           TenantContextFilter tenantContextFilter) throws Exception {
        http .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/login").permitAll()
                .requestMatchers("/WEB-INF/**").permitAll()
                .requestMatchers("/css/**", "/js/**", "/img/**", "/vendor/**", "/webjars/**").permitAll()
                .requestMatchers("/error").permitAll()
                .requestMatchers(HttpMethod.POST, "/webhooks/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/canales/mercadolibre/callback").permitAll()
                .requestMatchers(HttpMethod.POST, "/canales/woocommerce/callback").permitAll()
                .requestMatchers(HttpMethod.GET, "/productos/*/foto", "/productos/*/foto/*").permitAll()
                .requestMatchers(HttpMethod.GET, "/productos/*/fotos/*/*").permitAll()
                .requestMatchers(HttpMethod.GET, "/productos/*/variantes/*/foto", "/productos/*/variantes/*/foto/*").permitAll()
                .requestMatchers(HttpMethod.HEAD, "/productos/*/foto", "/productos/*/foto/*").permitAll()
                .requestMatchers(HttpMethod.HEAD, "/productos/*/fotos/*/*").permitAll()
                .requestMatchers(HttpMethod.HEAD, "/productos/*/variantes/*/foto", "/productos/*/variantes/*/foto/*").permitAll()

                //Roles

                .requestMatchers("/tenants/**").hasRole("SUPERADMIN")
                .requestMatchers("/usuarios/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/facturacion/configuracion", "/facturacion/probar").hasRole("ADMIN")
                .requestMatchers("/reportes/**").hasRole("ADMIN") // ========================================== // ADMIN y EMPLEADO // ==========================================

                        .requestMatchers(
                                "/ventas/**",
                                "/presupuestos/**",
                                "/productos/**",
                                "/canales/**",
                                "/guias-talles/**",
                                "/facturacion/**",
                                "/clientes/**",
                                "/proveedores/**"
                        ) .hasAnyRole("ADMIN", "EMPLEADO")
                        // ========================================== // TODO LO DEMÁS requiere autenticación // ==========================================

                .anyRequest().authenticated()
        )
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .successHandler(successHandler)
                        .failureUrl("/login?error=true")
                        .permitAll() )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout=true")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                )
                .exceptionHandling(ex -> ex
                        .accessDeniedPage("/acceso-denegado")
                )
                .csrf(csrf -> csrf.ignoringRequestMatchers(
                        "/webhooks/**",
                        "/canales/mercadolibre/callback",
                        "/canales/woocommerce/callback"
                ));
        http.addFilterAfter(tenantContextFilter, AnonymousAuthenticationFilter.class);
        return http.build();
    } }

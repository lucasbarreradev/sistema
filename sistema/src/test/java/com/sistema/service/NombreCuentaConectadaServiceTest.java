package com.sistema.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sistema.model.CredencialMercadoLibre;
import com.sistema.model.CredencialTiendanube;
import com.sistema.model.CredencialWooCommerce;
import com.sistema.repository.CredencialMercadoLibreRepository;
import com.sistema.repository.CredencialTiendanubeRepository;
import com.sistema.repository.CredencialWooCommerceRepository;
import com.sistema.tenant.TenantContext;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NombreCuentaConectadaServiceTest {

    @Test
    void muestraUsuarioYNombreGuardadoDeMercadoLibre() {
        CredencialMercadoLibreRepository repository = mock(CredencialMercadoLibreRepository.class);
        CredencialMercadoLibre credencial = new CredencialMercadoLibre();
        credencial.setTenantId(7L);
        credencial.setUsuarioExternoId(3543745002L);
        credencial.setNombreCuenta("JOYERIA_LUCAS");
        when(repository.findByTenantId(7L)).thenReturn(Optional.of(credencial));
        MercadoLibreTokenService service = new MercadoLibreTokenService(
                repository, mock(CifradoCredencialesService.class), "app", "secret", "", "");

        try (TenantContext.Scope ignored = TenantContext.use(7L)) {
            assertEquals("JOYERIA_LUCAS (usuario 3543745002)", service.nombreCuentaConectada());
        }
    }

    @Test
    void muestraNombreYDominioDeWooCommerce() {
        CredencialWooCommerceRepository repository = mock(CredencialWooCommerceRepository.class);
        CredencialWooCommerce credencial = new CredencialWooCommerce();
        credencial.setTenantId(7L);
        credencial.setUrlTienda("https://www.tiendafenixsport.com.ar");
        credencial.setNombreCuenta("Tienda Fénix Sport");
        when(repository.findByTenantId(7L)).thenReturn(Optional.of(credencial));
        WooCommerceCredencialesService service = new WooCommerceCredencialesService(
                repository, mock(CifradoCredencialesService.class));

        try (TenantContext.Scope ignored = TenantContext.use(7L)) {
            assertEquals("Tienda Fénix Sport (tiendafenixsport.com.ar)",
                    service.nombreCuentaConectada());
        }
    }

    @Test
    void muestraNombreEIdDeTiendanube() {
        CredencialTiendanubeRepository repository = mock(CredencialTiendanubeRepository.class);
        CredencialTiendanube credencial = new CredencialTiendanube();
        credencial.setTenantId(7L);
        credencial.setStoreId("123456");
        credencial.setNombreCuenta("Joyería Lucas");
        when(repository.findByTenantId(7L)).thenReturn(Optional.of(credencial));
        TiendanubeCredencialesService service = new TiendanubeCredencialesService(
                repository, mock(CifradoCredencialesService.class), new ObjectMapper(),
                "", "", "SistemaStock/1.0", "app", "secret", "https://retorno.test", "");

        try (TenantContext.Scope ignored = TenantContext.use(7L)) {
            assertEquals("Joyería Lucas (tienda 123456)", service.nombreCuentaConectada());
        }
    }
}

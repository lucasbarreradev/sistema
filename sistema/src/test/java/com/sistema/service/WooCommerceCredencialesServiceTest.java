package com.sistema.service;

import com.sistema.repository.CredencialWooCommerceRepository;
import com.sistema.tenant.TenantContext;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WooCommerceCredencialesServiceTest {

    @Test
    void desconectarEliminaLaCredencialDelTenantYDejaElCanalSinConfigurar() {
        CredencialWooCommerceRepository repository = mock(CredencialWooCommerceRepository.class);
        CifradoCredencialesService cifrado = mock(CifradoCredencialesService.class);
        WooCommerceCredencialesService service = new WooCommerceCredencialesService(repository, cifrado);
        AtomicBoolean guardada = new AtomicBoolean(true);
        when(repository.existsByTenantId(7L)).thenAnswer(invocation -> guardada.get());
        doAnswer(invocation -> {
            guardada.set(false);
            return null;
        }).when(repository).deleteByTenantId(7L);

        try (TenantContext.Scope ignored = TenantContext.use(7L)) {
            service.desconectar();

            assertFalse(service.configurado());
        }

        var orden = inOrder(repository);
        orden.verify(repository).deleteByTenantId(7L);
        orden.verify(repository).existsByTenantId(7L);
    }

    @Test
    void noUsaCredencialesGlobalesCuandoElTenantNoEstaConectado() {
        CredencialWooCommerceRepository repository = mock(CredencialWooCommerceRepository.class);
        CifradoCredencialesService cifrado = mock(CifradoCredencialesService.class);
        WooCommerceCredencialesService service = new WooCommerceCredencialesService(repository, cifrado);

        try (TenantContext.Scope ignored = TenantContext.use(1L)) {
            assertFalse(service.configurado());
            assertThrows(IllegalStateException.class, service::obtener);
        }
    }
}

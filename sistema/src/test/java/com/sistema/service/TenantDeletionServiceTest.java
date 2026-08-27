package com.sistema.service;

import com.sistema.model.Tenant;
import com.sistema.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TenantDeletionServiceTest {
    private TenantRepository tenantRepository;
    private JdbcTemplate jdbcTemplate;
    private TenantDeletionService service;

    @BeforeEach
    void setUp() {
        tenantRepository = mock(TenantRepository.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        service = new TenantDeletionService(tenantRepository, jdbcTemplate);
    }

    @Test
    void exigeDesactivarYConfirmarElCodigo() {
        Tenant tenant = tenant(8L, "sucursal-norte", true);
        when(tenantRepository.findById(8L)).thenReturn(Optional.of(tenant));

        IllegalArgumentException activo = assertThrows(IllegalArgumentException.class,
                () -> service.eliminar(8L, "sucursal-norte", 1L, "root"));
        assertTrue(activo.getMessage().contains("desactivar"));

        tenant.setActivo(false);
        IllegalArgumentException codigo = assertThrows(IllegalArgumentException.class,
                () -> service.eliminar(8L, "otro", 1L, "root"));
        assertTrue(codigo.getMessage().contains("no coincide"));
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void protegeElPrincipalYElNegocioDeLaSesion() {
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant(1L, "principal", false)));
        assertThrows(IllegalArgumentException.class,
                () -> service.eliminar(1L, "principal", 2L, "root"));

        when(tenantRepository.findById(9L)).thenReturn(Optional.of(tenant(9L, "actual", false)));
        assertThrows(IllegalArgumentException.class,
                () -> service.eliminar(9L, "actual", 9L, "root"));
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void noBorraMientrasHayTrabajosActivos() {
        when(tenantRepository.findById(8L)).thenReturn(Optional.of(tenant(8L, "sucursal", false)));
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(8L))).thenReturn(1);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.eliminar(8L, "sucursal", 1L, "root"));

        assertTrue(error.getMessage().contains("trabajos en proceso"));
        verify(jdbcTemplate, never()).update(startsWith("DELETE"), anyLong());
    }

    @Test
    void eliminaTodosLosDatosYFinalmenteElTenant() {
        when(tenantRepository.findById(8L)).thenReturn(Optional.of(tenant(8L, "sucursal", false)));
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(8L))).thenReturn(0);
        when(jdbcTemplate.update(anyString(), eq(8L))).thenReturn(1);

        String nombre = service.eliminar(8L, "SUCURSAL", 1L, "root");

        assertEquals("Sucursal", nombre);
        verify(jdbcTemplate).update("DELETE FROM tenant WHERE id = ?", 8L);
        verify(jdbcTemplate, atLeast(20)).update(startsWith("DELETE"), eq(8L));
    }

    private Tenant tenant(Long id, String codigo, boolean activo) {
        Tenant tenant = new Tenant();
        tenant.setId(id);
        tenant.setCodigo(codigo);
        tenant.setNombre("Sucursal");
        tenant.setActivo(activo);
        return tenant;
    }
}

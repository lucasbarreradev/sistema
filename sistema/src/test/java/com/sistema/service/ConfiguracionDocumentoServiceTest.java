package com.sistema.service;

import com.sistema.model.ConfiguracionDocumento;
import com.sistema.repository.ConfiguracionDocumentoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ConfiguracionDocumentoServiceTest {
    private ConfiguracionDocumentoRepository repository;
    private ConfiguracionDocumentoService service;

    @BeforeEach
    void setUp() {
        repository = mock(ConfiguracionDocumentoRepository.class);
        service = new ConfiguracionDocumentoService(repository);
        when(repository.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));
    }

    @Test
    void creaLaConfiguracionConDatosNormalizadosYLogo() {
        when(repository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
        ConfiguracionDocumento formulario = new ConfiguracionDocumento();
        formulario.setNombreEmpresa("  Mi negocio  ");
        formulario.setDireccion(" Calle 1 ");
        MockMultipartFile logo = new MockMultipartFile(
                "logo", "logo.png", "image/png", new byte[]{1, 2, 3});

        ConfiguracionDocumento guardada = service.guardar(formulario, logo, false);

        assertEquals("Mi negocio", guardada.getNombreEmpresa());
        assertEquals("Calle 1", guardada.getDireccion());
        assertArrayEquals(new byte[]{1, 2, 3}, guardada.getLogoContenido());
        assertEquals("image/png", guardada.getLogoTipoContenido());
        verify(repository).save(guardada);
    }

    @Test
    void conservaElLogoExistenteCuandoNoSeEnviaUnoNuevo() {
        ConfiguracionDocumento existente = new ConfiguracionDocumento();
        existente.setNombreEmpresa("Anterior");
        existente.setLogoContenido(new byte[]{9});
        existente.setLogoTipoContenido("image/jpeg");
        when(repository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(existente));

        ConfiguracionDocumento formulario = new ConfiguracionDocumento();
        formulario.setNombreEmpresa("Nuevo nombre");
        ConfiguracionDocumento guardada = service.guardar(formulario, null, false);

        assertEquals("Nuevo nombre", guardada.getNombreEmpresa());
        assertArrayEquals(new byte[]{9}, guardada.getLogoContenido());
    }

    @Test
    void rechazaLogoQueNoSeaImagenAdmitida() {
        when(repository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
        ConfiguracionDocumento formulario = new ConfiguracionDocumento();
        formulario.setNombreEmpresa("Mi negocio");
        MockMultipartFile archivo = new MockMultipartFile(
                "logo", "logo.svg", "image/svg+xml", new byte[]{1});

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.guardar(formulario, archivo, false));

        assertEquals("El logo debe ser JPG o PNG", error.getMessage());
        verify(repository, never()).save(any());
    }
}

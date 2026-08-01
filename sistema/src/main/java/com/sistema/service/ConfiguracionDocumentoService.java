package com.sistema.service;

import com.sistema.model.ConfiguracionDocumento;
import com.sistema.repository.ConfiguracionDocumentoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Service
public class ConfiguracionDocumentoService {
    private static final long MAX_LOGO = 5L * 1024 * 1024;
    private static final List<String> TIPOS_LOGO =
            List.of("image/jpeg", "image/png");

    private final ConfiguracionDocumentoRepository repository;

    public ConfiguracionDocumentoService(
            ConfiguracionDocumentoRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Optional<ConfiguracionDocumento> obtener() {
        return repository.findFirstByOrderByIdAsc();
    }

    @Transactional(readOnly = true)
    public boolean configurada() {
        return obtener().isPresent();
    }

    @Transactional(readOnly = true)
    public ConfiguracionDocumento obtenerRequerida() {
        return obtener().orElseThrow(() -> new IllegalStateException(
                "Configure los datos de la empresa antes de generar documentos"));
    }

    @Transactional
    public ConfiguracionDocumento guardar(ConfiguracionDocumento formulario,
                                           MultipartFile logo,
                                           boolean quitarLogo) {
        String nombre = limpiar(formulario.getNombreEmpresa());
        if (nombre == null) {
            throw new IllegalArgumentException("Ingrese el nombre de la empresa");
        }

        ConfiguracionDocumento destino = obtener()
                .orElseGet(ConfiguracionDocumento::new);
        destino.setNombreEmpresa(nombre);
        destino.setNombreContacto(limpiar(formulario.getNombreContacto()));
        destino.setCuit(limpiar(formulario.getCuit()));
        destino.setDireccion(limpiar(formulario.getDireccion()));
        destino.setCodigoPostal(limpiar(formulario.getCodigoPostal()));
        destino.setLocalidad(limpiar(formulario.getLocalidad()));
        destino.setProvincia(limpiar(formulario.getProvincia()));
        destino.setPais(limpiar(formulario.getPais()));
        destino.setTelefono(limpiar(formulario.getTelefono()));
        destino.setEmail(limpiar(formulario.getEmail()));

        if (quitarLogo) {
            destino.setLogoContenido(null);
            destino.setLogoNombre(null);
            destino.setLogoTipoContenido(null);
        }
        if (logo != null && !logo.isEmpty()) {
            guardarLogo(destino, logo);
        }
        return repository.save(destino);
    }

    private void guardarLogo(ConfiguracionDocumento destino, MultipartFile logo) {
        String tipo = logo.getContentType();
        if (tipo == null || !TIPOS_LOGO.contains(tipo.toLowerCase())) {
            throw new IllegalArgumentException("El logo debe ser JPG o PNG");
        }
        if (logo.getSize() > MAX_LOGO) {
            throw new IllegalArgumentException("El logo no puede superar los 5 MB");
        }
        try {
            destino.setLogoContenido(logo.getBytes());
            destino.setLogoNombre(logo.getOriginalFilename());
            destino.setLogoTipoContenido(tipo);
        } catch (IOException e) {
            throw new IllegalArgumentException("No se pudo leer el logo", e);
        }
    }

    private String limpiar(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}

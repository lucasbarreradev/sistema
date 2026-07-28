package com.sistema.service;

import com.sistema.dto.ResultadoImportacionCanal;
import com.sistema.dto.ResultadoPublicacionLote;
import com.sistema.model.CanalVenta;
import com.sistema.model.EstadoTrabajoSincronizacion;
import com.sistema.model.TrabajoSincronizacion;
import com.sistema.repository.TrabajoSincronizacionRepository;
import com.sistema.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class ProcesadorTrabajoSincronizacionService {
    private static final Logger log = LoggerFactory.getLogger(ProcesadorTrabajoSincronizacionService.class);

    private final TrabajoSincronizacionRepository repository;
    private final SincronizacionCanalesService sincronizacionCanalesService;

    public ProcesadorTrabajoSincronizacionService(TrabajoSincronizacionRepository repository,
                                                  SincronizacionCanalesService sincronizacionCanalesService) {
        this.repository = repository;
        this.sincronizacionCanalesService = sincronizacionCanalesService;
    }

    @Async("sincronizacionTaskExecutor")
    public void ejecutar(Long trabajoId, long tenantId, CanalVenta origen, List<CanalVenta> destinos) {
        try (TenantContext.Scope ignored = TenantContext.use(tenantId)) {
            actualizarAProcesando(trabajoId);
            SincronizacionCanalesService.Resultado resultado = sincronizacionCanalesService.sincronizar(origen, destinos);
            guardarResultado(trabajoId, resultado);
        } catch (Exception e) {
            log.error("Falló el trabajo de sincronización {} del tenant {}", trabajoId, tenantId, e);
            try (TenantContext.Scope ignored = TenantContext.use(tenantId)) {
                guardarError(trabajoId, mensajeExcepcion(e));
            } catch (Exception errorGuardando) {
                log.error("No se pudo guardar el error del trabajo de sincronización {}", trabajoId, errorGuardando);
            }
        }
    }

    private void actualizarAProcesando(Long trabajoId) {
        TrabajoSincronizacion trabajo = buscar(trabajoId);
        trabajo.setEstado(EstadoTrabajoSincronizacion.PROCESANDO);
        trabajo.setIniciadoEn(LocalDateTime.now());
        trabajo.setResumen("Importando productos desde " + trabajo.getOrigenDescripcion() + "...");
        repository.saveAndFlush(trabajo);
    }

    private void guardarResultado(Long trabajoId, SincronizacionCanalesService.Resultado resultado) {
        ResultadoImportacionCanal importacion = resultado.importacion();
        ResultadoPublicacionLote publicacion = resultado.publicacion();
        List<String> errores = new ArrayList<>(importacion.getErrores());
        errores.addAll(publicacion.getErrores());

        TrabajoSincronizacion trabajo = buscar(trabajoId);
        trabajo.setFinalizadoEn(LocalDateTime.now());
        trabajo.setResumen("Importación: " + importacion.resumen()
                + ". Publicaciones procesadas correctamente: " + publicacion.getExitosas() + ".");
        trabajo.setDetalle(errores.isEmpty() ? null : String.join("\n", errores));
        trabajo.setEstado(errores.isEmpty()
                ? EstadoTrabajoSincronizacion.COMPLETADA
                : EstadoTrabajoSincronizacion.COMPLETADA_CON_ERRORES);
        repository.save(trabajo);
    }

    private void guardarError(Long trabajoId, String mensaje) {
        TrabajoSincronizacion trabajo = buscar(trabajoId);
        trabajo.setEstado(EstadoTrabajoSincronizacion.ERROR);
        trabajo.setFinalizadoEn(LocalDateTime.now());
        trabajo.setResumen("La sincronización no pudo completarse.");
        trabajo.setDetalle(mensaje);
        repository.save(trabajo);
    }

    private TrabajoSincronizacion buscar(Long trabajoId) {
        return repository.findById(trabajoId)
                .orElseThrow(() -> new IllegalStateException("No se encontró el trabajo de sincronización " + trabajoId));
    }

    private String mensajeExcepcion(Exception e) {
        Throwable actual = e;
        while (actual.getCause() != null && actual.getCause() != actual) actual = actual.getCause();
        String mensaje = actual.getMessage();
        if (mensaje == null || mensaje.isBlank()) mensaje = e.getMessage();
        return mensaje == null || mensaje.isBlank() ? "Error inesperado durante la sincronización" : mensaje;
    }
}

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
    private final PublicacionService publicacionService;

    public ProcesadorTrabajoSincronizacionService(TrabajoSincronizacionRepository repository,
                                                  SincronizacionCanalesService sincronizacionCanalesService,
                                                  PublicacionService publicacionService) {
        this.repository = repository;
        this.sincronizacionCanalesService = sincronizacionCanalesService;
        this.publicacionService = publicacionService;
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

    @Async("sincronizacionTaskExecutor")
    public void ejecutarPublicacion(Long trabajoId, long tenantId, List<Long> productoIds,
                                    List<CanalVenta> canales) {
        try (TenantContext.Scope ignored = TenantContext.use(tenantId)) {
            actualizarAProcesando(trabajoId,
                    "Publicando " + productoIds.size() + " producto(s) en "
                            + descripcionCanales(canales) + "...");
            ResultadoPublicacionLote resultado = publicacionService.publicar(productoIds, canales);
            guardarResultadoPublicacion(trabajoId, resultado);
        } catch (Exception e) {
            log.error("Falló el trabajo de publicación {} del tenant {}", trabajoId, tenantId, e);
            try (TenantContext.Scope ignored = TenantContext.use(tenantId)) {
                guardarError(trabajoId, mensajeExcepcion(e));
            } catch (Exception errorGuardando) {
                log.error("No se pudo guardar el error del trabajo de publicación {}", trabajoId, errorGuardando);
            }
        }
    }

    private void actualizarAProcesando(Long trabajoId) {
        TrabajoSincronizacion trabajo = buscar(trabajoId);
        actualizarAProcesando(trabajoId,
                "Importando productos desde " + trabajo.getOrigenDescripcion() + "...");
    }

    private void actualizarAProcesando(Long trabajoId, String resumen) {
        TrabajoSincronizacion trabajo = buscar(trabajoId);
        trabajo.setEstado(EstadoTrabajoSincronizacion.PROCESANDO);
        trabajo.setIniciadoEn(LocalDateTime.now());
        trabajo.setResumen(resumen);
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

    private void guardarResultadoPublicacion(Long trabajoId, ResultadoPublicacionLote resultado) {
        TrabajoSincronizacion trabajo = buscar(trabajoId);
        trabajo.setFinalizadoEn(LocalDateTime.now());
        trabajo.setResumen("Publicaciones procesadas correctamente: " + resultado.getExitosas() + ".");
        trabajo.setDetalle(resultado.getErrores().isEmpty()
                ? null : String.join("\n", resultado.getErrores()));
        trabajo.setEstado(resultado.getErrores().isEmpty()
                ? EstadoTrabajoSincronizacion.COMPLETADA
                : EstadoTrabajoSincronizacion.COMPLETADA_CON_ERRORES);
        repository.save(trabajo);
    }

    private void guardarError(Long trabajoId, String mensaje) {
        TrabajoSincronizacion trabajo = buscar(trabajoId);
        trabajo.setEstado(EstadoTrabajoSincronizacion.ERROR);
        trabajo.setFinalizadoEn(LocalDateTime.now());
        trabajo.setResumen("El trabajo no pudo completarse.");
        trabajo.setDetalle(mensaje);
        repository.save(trabajo);
    }

    private TrabajoSincronizacion buscar(Long trabajoId) {
        return repository.findById(trabajoId)
                .orElseThrow(() -> new IllegalStateException("No se encontró el trabajo de sincronización " + trabajoId));
    }

    private String descripcionCanales(List<CanalVenta> canales) {
        return canales.stream().map(CanalVenta::getDescripcion)
                .collect(java.util.stream.Collectors.joining(", "));
    }

    private String mensajeExcepcion(Exception e) {
        Throwable actual = e;
        while (actual.getCause() != null && actual.getCause() != actual) actual = actual.getCause();
        String mensaje = actual.getMessage();
        if (mensaje == null || mensaje.isBlank()) mensaje = e.getMessage();
        return mensaje == null || mensaje.isBlank() ? "Error inesperado durante la sincronización" : mensaje;
    }
}

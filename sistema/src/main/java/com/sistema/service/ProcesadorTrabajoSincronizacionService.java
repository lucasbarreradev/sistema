package com.sistema.service;

import com.sistema.dto.ResultadoImportacionCanal;
import com.sistema.dto.ResultadoPublicacionLote;
import com.sistema.dto.ProductoCanalImportado;
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
import java.util.function.BooleanSupplier;

@Service
public class ProcesadorTrabajoSincronizacionService {
    private static final Logger log = LoggerFactory.getLogger(ProcesadorTrabajoSincronizacionService.class);

    private final TrabajoSincronizacionRepository repository;
    private final SincronizacionCanalesService sincronizacionCanalesService;
    private final PublicacionService publicacionService;
    private final ImportacionCanalService importacionCanalService;
    private final RevisionPublicacionService revisionPublicacionService;

    public ProcesadorTrabajoSincronizacionService(TrabajoSincronizacionRepository repository,
                                                  SincronizacionCanalesService sincronizacionCanalesService,
                                                  PublicacionService publicacionService,
                                                  ImportacionCanalService importacionCanalService,
                                                  RevisionPublicacionService revisionPublicacionService) {
        this.repository = repository;
        this.sincronizacionCanalesService = sincronizacionCanalesService;
        this.publicacionService = publicacionService;
        this.importacionCanalService = importacionCanalService;
        this.revisionPublicacionService = revisionPublicacionService;
    }

    @Async("sincronizacionTaskExecutor")
    public void ejecutarPreparacionPublicacion(
            Long trabajoId, long tenantId, List<Long> productoIds,
            List<CanalVenta> canales) {
        try (TenantContext.Scope ignored = TenantContext.use(tenantId)) {
            if (!actualizarAProcesando(trabajoId,
                    "Detectando categorías y atributos de " + productoIds.size()
                            + " producto(s)...")) return;
            int procesados = revisionPublicacionService.prepararEnSegundoPlano(
                    trabajoId, productoIds, canales,
                    verificadorCancelacion(trabajoId));
            if (finalizarCanceladoSiSolicitado(trabajoId,
                    "Preparación cancelada por el usuario después de revisar "
                            + procesados + " producto(s).", List.of())) return;
            TrabajoSincronizacion trabajo = buscar(trabajoId);
            trabajo.setEstado(EstadoTrabajoSincronizacion.COMPLETADA);
            trabajo.setFinalizadoEn(LocalDateTime.now());
            trabajo.setResumen("Revisión preparada: " + procesados
                    + " producto(s) analizados.");
            trabajo.setDetalle(null);
            repository.save(trabajo);
        } catch (Exception e) {
            registrarFallo(trabajoId, tenantId, "preparación de publicación", e);
        }
    }

    @Async("sincronizacionTaskExecutor")
    public void ejecutar(Long trabajoId, long tenantId, CanalVenta origen, List<CanalVenta> destinos) {
        try (TenantContext.Scope ignored = TenantContext.use(tenantId)) {
            if (!actualizarAProcesando(trabajoId)) return;
            SincronizacionCanalesService.Resultado resultado = sincronizacionCanalesService
                    .sincronizar(origen, destinos, verificadorCancelacion(trabajoId));
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
            if (!actualizarAProcesando(trabajoId,
                    "Publicando " + productoIds.size() + " producto(s) en "
                            + descripcionCanales(canales) + "...")) return;
            ResultadoPublicacionLote resultado = publicacionService
                    .publicar(productoIds, canales, verificadorCancelacion(trabajoId));
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

    @Async("sincronizacionTaskExecutor")
    public void ejecutarImportacionCompleta(Long trabajoId, long tenantId, CanalVenta canal,
                                            boolean incluirInactivas) {
        try (TenantContext.Scope ignored = TenantContext.use(tenantId)) {
            if (!actualizarAProcesando(trabajoId,
                    "Trayendo productos " + (incluirInactivas ? "activos e inactivos" : "activos")
                            + " desde " + canal.getDescripcion() + "...")) return;
            BooleanSupplier cancelacion = verificadorCancelacion(trabajoId);
            List<ProductoCanalImportado> productos = importacionCanalService
                    .obtenerProductos(canal, incluirInactivas, cancelacion);
            if (finalizarCanceladoSiSolicitado(trabajoId,
                    "Cancelado por el usuario antes de importar los productos descargados.", List.of())) return;
            ResultadoImportacionCanal resultado = importacionCanalService
                    .importar(canal, productos, cancelacion);
            guardarResultadoImportacion(trabajoId, resultado);
        } catch (Exception e) {
            registrarFallo(trabajoId, tenantId, "importación completa", e);
        }
    }

    @Async("sincronizacionTaskExecutor")
    public void ejecutarImportacionFiltradaMercadoLibre(
            Long trabajoId, long tenantId, int cantidad, String categoria,
            boolean incluirInactivas) {
        try (TenantContext.Scope ignored = TenantContext.use(tenantId)) {
            String alcance = categoria == null || categoria.isBlank()
                    ? ""
                    : " de la categoría " + categoria;
            if (!actualizarAProcesando(trabajoId,
                    "Trayendo las últimas " + cantidad + " publicaciones" + alcance
                            + " desde Mercado Libre...")) return;
            BooleanSupplier cancelacion = verificadorCancelacion(trabajoId);
            List<ProductoCanalImportado> productos = importacionCanalService
                    .obtenerUltimasPublicacionesMercadoLibre(
                            cantidad, categoria, incluirInactivas, cancelacion);
            if (finalizarCanceladoSiSolicitado(trabajoId,
                    "Cancelado por el usuario antes de importar las publicaciones descargadas.",
                    List.of())) return;
            ResultadoImportacionCanal resultado = importacionCanalService
                    .importar(CanalVenta.MERCADO_LIBRE, productos, cancelacion);
            guardarResultadoImportacion(trabajoId, resultado);
        } catch (Exception e) {
            registrarFallo(trabajoId, tenantId,
                    "importación filtrada de Mercado Libre", e);
        }
    }

    private boolean actualizarAProcesando(Long trabajoId) {
        TrabajoSincronizacion trabajo = buscar(trabajoId);
        return actualizarAProcesando(trabajoId,
                "Importando productos desde " + trabajo.getOrigenDescripcion() + "...");
    }

    private boolean actualizarAProcesando(Long trabajoId, String resumen) {
        TrabajoSincronizacion trabajo = buscar(trabajoId);
        if (trabajo.isCancelacionSolicitada()
                || trabajo.getEstado() == EstadoTrabajoSincronizacion.CANCELADO) {
            aplicarCancelacion(trabajo, "Cancelado por el usuario antes de comenzar.", List.of());
            repository.save(trabajo);
            return false;
        }
        trabajo.setEstado(EstadoTrabajoSincronizacion.PROCESANDO);
        trabajo.setIniciadoEn(LocalDateTime.now());
        trabajo.setResumen(resumen);
        repository.saveAndFlush(trabajo);
        return true;
    }

    private void guardarResultado(Long trabajoId, SincronizacionCanalesService.Resultado resultado) {
        ResultadoImportacionCanal importacion = resultado.importacion();
        ResultadoPublicacionLote publicacion = resultado.publicacion();
        List<String> errores = new ArrayList<>(importacion.getErrores());
        errores.addAll(publicacion.getErrores());

        TrabajoSincronizacion trabajo = buscar(trabajoId);
        if (trabajo.isCancelacionSolicitada()) {
            aplicarCancelacion(trabajo,
                    "Cancelado por el usuario. Importación parcial: " + importacion.resumen()
                            + ". Publicaciones procesadas correctamente: " + publicacion.getExitosas() + ".",
                    errores);
            repository.save(trabajo);
            return;
        }
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
        if (trabajo.isCancelacionSolicitada()) {
            aplicarCancelacion(trabajo,
                    "Cancelado por el usuario. Publicaciones procesadas correctamente: "
                            + resultado.getExitosas() + ".",
                    resultado.getErrores());
            repository.save(trabajo);
            return;
        }
        trabajo.setFinalizadoEn(LocalDateTime.now());
        trabajo.setResumen("Publicaciones procesadas correctamente: " + resultado.getExitosas() + ".");
        trabajo.setDetalle(resultado.getErrores().isEmpty()
                ? null : String.join("\n", resultado.getErrores()));
        trabajo.setEstado(resultado.getErrores().isEmpty()
                ? EstadoTrabajoSincronizacion.COMPLETADA
                : EstadoTrabajoSincronizacion.COMPLETADA_CON_ERRORES);
        repository.save(trabajo);
    }

    private void guardarResultadoImportacion(Long trabajoId, ResultadoImportacionCanal resultado) {
        TrabajoSincronizacion trabajo = buscar(trabajoId);
        if (trabajo.isCancelacionSolicitada()) {
            aplicarCancelacion(trabajo,
                    "Cancelado por el usuario. Importación parcial: " + resultado.resumen() + ".",
                    resultado.getErrores());
            repository.save(trabajo);
            return;
        }
        trabajo.setFinalizadoEn(LocalDateTime.now());
        trabajo.setResumen("Importación: " + resultado.resumen() + ".");
        trabajo.setDetalle(resultado.getErrores().isEmpty()
                ? null : String.join("\n", resultado.getErrores()));
        trabajo.setEstado(resultado.getErrores().isEmpty()
                ? EstadoTrabajoSincronizacion.COMPLETADA
                : EstadoTrabajoSincronizacion.COMPLETADA_CON_ERRORES);
        repository.save(trabajo);
    }

    private void registrarFallo(Long trabajoId, long tenantId, String operacion, Exception error) {
        log.error("Falló el trabajo de {} {} del tenant {}", operacion, trabajoId, tenantId, error);
        try (TenantContext.Scope ignored = TenantContext.use(tenantId)) {
            guardarError(trabajoId, mensajeExcepcion(error));
        } catch (Exception errorGuardando) {
            log.error("No se pudo guardar el error del trabajo {}", trabajoId, errorGuardando);
        }
    }

    private void guardarError(Long trabajoId, String mensaje) {
        TrabajoSincronizacion trabajo = buscar(trabajoId);
        if (trabajo.isCancelacionSolicitada()
                || trabajo.getEstado() == EstadoTrabajoSincronizacion.CANCELADO) {
            aplicarCancelacion(trabajo, "Cancelado por el usuario.", List.of());
            repository.save(trabajo);
            return;
        }
        trabajo.setEstado(EstadoTrabajoSincronizacion.ERROR);
        trabajo.setFinalizadoEn(LocalDateTime.now());
        trabajo.setResumen("El trabajo no pudo completarse.");
        trabajo.setDetalle(mensaje);
        repository.save(trabajo);
    }

    private BooleanSupplier verificadorCancelacion(Long trabajoId) {
        return () -> cancelacionSolicitada(trabajoId);
    }

    private boolean cancelacionSolicitada(Long trabajoId) {
        return repository.findById(trabajoId)
                .map(trabajo -> trabajo.isCancelacionSolicitada()
                        || trabajo.getEstado() == EstadoTrabajoSincronizacion.CANCELADO)
                .orElse(true);
    }

    private boolean finalizarCanceladoSiSolicitado(Long trabajoId, String resumen,
                                                    List<String> errores) {
        TrabajoSincronizacion trabajo = buscar(trabajoId);
        if (!trabajo.isCancelacionSolicitada()
                && trabajo.getEstado() != EstadoTrabajoSincronizacion.CANCELADO) return false;
        aplicarCancelacion(trabajo, resumen, errores);
        repository.save(trabajo);
        return true;
    }

    private void aplicarCancelacion(TrabajoSincronizacion trabajo, String resumen,
                                    List<String> errores) {
        trabajo.setCancelacionSolicitada(true);
        trabajo.setEstado(EstadoTrabajoSincronizacion.CANCELADO);
        trabajo.setFinalizadoEn(LocalDateTime.now());
        trabajo.setResumen(resumen);
        trabajo.setDetalle(errores == null || errores.isEmpty() ? null : String.join("\n", errores));
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

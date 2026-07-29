package com.sistema.service;

import com.sistema.dto.ProductoCanalImportado;
import com.sistema.model.CanalVenta;
import com.sistema.model.EstadoTrabajoSincronizacion;
import com.sistema.model.TipoTrabajoSincronizacion;
import com.sistema.model.TrabajoSincronizacion;
import com.sistema.repository.TrabajoSincronizacionRepository;
import com.sistema.tenant.TenantContext;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TrabajoSincronizacionService {
    private static final EnumSet<EstadoTrabajoSincronizacion> ESTADOS_ACTIVOS =
            EnumSet.of(EstadoTrabajoSincronizacion.PENDIENTE, EstadoTrabajoSincronizacion.PROCESANDO);

    private final TrabajoSincronizacionRepository repository;
    private final ProcesadorTrabajoSincronizacionService procesador;
    private final LocalDateTime inicioInstancia = LocalDateTime.now();

    public TrabajoSincronizacionService(TrabajoSincronizacionRepository repository,
                                        ProcesadorTrabajoSincronizacionService procesador) {
        this.repository = repository;
        this.procesador = procesador;
    }

    public TrabajoSincronizacion iniciar(CanalVenta origen, Collection<CanalVenta> destinos) {
        if (origen == null) throw new IllegalArgumentException("Seleccione un canal de origen");
        if (destinos == null) throw new IllegalArgumentException("Seleccione al menos un canal de destino");

        List<CanalVenta> destinosValidos = destinos.stream()
                .filter(canal -> canal != null && canal != origen)
                .distinct()
                .toList();
        if (destinosValidos.isEmpty()) {
            throw new IllegalArgumentException("Seleccione al menos un destino diferente del origen");
        }
        verificarDisponibilidad();

        long tenantId = TenantContext.require();
        TrabajoSincronizacion trabajo = new TrabajoSincronizacion();
        trabajo.setTenantId(tenantId);
        trabajo.setOrigen(origen);
        trabajo.setTipoTrabajo(TipoTrabajoSincronizacion.SINCRONIZACION_CANALES);
        trabajo.setDestinos(destinosValidos.stream().map(Enum::name).collect(Collectors.joining(",")));
        trabajo.setEstado(EstadoTrabajoSincronizacion.PENDIENTE);
        trabajo.setCreadoEn(LocalDateTime.now());
        trabajo.setResumen("Esperando para comenzar...");
        trabajo = repository.save(trabajo);

        procesador.ejecutar(trabajo.getId(), tenantId, origen, List.copyOf(destinosValidos));
        return trabajo;
    }

    public TrabajoSincronizacion iniciarPublicacion(Collection<Long> productoIds,
                                                    Collection<CanalVenta> canales) {
        if (productoIds == null) throw new IllegalArgumentException("Seleccione al menos un producto");
        if (canales == null) throw new IllegalArgumentException("Seleccione al menos un canal");
        List<Long> productosValidos = productoIds.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        List<CanalVenta> canalesValidos = canales.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (productosValidos.isEmpty()) throw new IllegalArgumentException("Seleccione al menos un producto");
        if (canalesValidos.isEmpty()) throw new IllegalArgumentException("Seleccione al menos un canal");
        verificarDisponibilidad();

        long tenantId = TenantContext.require();
        TrabajoSincronizacion trabajo = new TrabajoSincronizacion();
        trabajo.setTenantId(tenantId);
        // La columna origen ya existía como obligatoria. Para este tipo de trabajo el flujo
        // visible comienza en Sistema y el primer destino sólo mantiene compatibilidad del esquema.
        trabajo.setOrigen(canalesValidos.get(0));
        trabajo.setTipoTrabajo(TipoTrabajoSincronizacion.PUBLICACION_SELECCIONADA);
        trabajo.setDestinos(canalesValidos.stream().map(Enum::name).collect(Collectors.joining(",")));
        trabajo.setEstado(EstadoTrabajoSincronizacion.PENDIENTE);
        trabajo.setCreadoEn(LocalDateTime.now());
        trabajo.setResumen("Esperando para publicar " + productosValidos.size() + " producto(s)...");
        trabajo = repository.save(trabajo);

        procesador.ejecutarPublicacion(trabajo.getId(), tenantId,
                List.copyOf(productosValidos), List.copyOf(canalesValidos));
        return trabajo;
    }

    public TrabajoSincronizacion iniciarImportacionCompleta(CanalVenta canal) {
        TrabajoSincronizacion trabajo = crearTrabajoImportacion(
                canal, TipoTrabajoSincronizacion.IMPORTACION_COMPLETA,
                "Esperando para traer todos los productos...");
        procesador.ejecutarImportacionCompleta(
                trabajo.getId(), trabajo.getTenantId(), canal);
        return trabajo;
    }

    public TrabajoSincronizacion iniciarPreparacionImportacion(CanalVenta canal) {
        TrabajoSincronizacion trabajo = crearTrabajoImportacion(
                canal, TipoTrabajoSincronizacion.PREPARACION_IMPORTACION,
                "Esperando para actualizar la lista de productos...");
        procesador.ejecutarPreparacionImportacion(
                trabajo.getId(), trabajo.getTenantId(), canal);
        return trabajo;
    }

    public TrabajoSincronizacion iniciarImportacionSeleccionada(
            CanalVenta canal, Collection<ProductoCanalImportado> productos) {
        if (productos == null || productos.isEmpty()) {
            throw new IllegalArgumentException("Seleccione al menos un producto para importar");
        }
        List<ProductoCanalImportado> seleccionados = productos.stream()
                .filter(java.util.Objects::nonNull)
                .toList();
        if (seleccionados.isEmpty()) {
            throw new IllegalArgumentException("Seleccione al menos un producto para importar");
        }
        TrabajoSincronizacion trabajo = crearTrabajoImportacion(
                canal, TipoTrabajoSincronizacion.IMPORTACION_SELECCIONADA,
                "Esperando para importar " + seleccionados.size() + " producto(s)...");
        procesador.ejecutarImportacionSeleccionada(
                trabajo.getId(), trabajo.getTenantId(), canal, List.copyOf(seleccionados));
        return trabajo;
    }

    public List<TrabajoSincronizacion> ultimos() {
        cerrarTrabajosInterrumpidos();
        return repository.findTop10ByOrderByCreadoEnDesc();
    }

    public boolean hayTrabajoActivo() {
        cerrarTrabajosInterrumpidos();
        return repository.existsByEstadoIn(ESTADOS_ACTIVOS);
    }

    private void verificarDisponibilidad() {
        cerrarTrabajosInterrumpidos();
        if (repository.existsByEstadoIn(ESTADOS_ACTIVOS)) {
            throw new IllegalStateException("Ya hay un trabajo en proceso para este negocio");
        }
    }

    private TrabajoSincronizacion crearTrabajoImportacion(
            CanalVenta canal, TipoTrabajoSincronizacion tipo, String resumen) {
        if (canal == null) throw new IllegalArgumentException("Seleccione un canal de origen");
        verificarDisponibilidad();
        TrabajoSincronizacion trabajo = new TrabajoSincronizacion();
        trabajo.setTenantId(TenantContext.require());
        trabajo.setOrigen(canal);
        trabajo.setTipoTrabajo(tipo);
        trabajo.setDestinos("SISTEMA");
        trabajo.setEstado(EstadoTrabajoSincronizacion.PENDIENTE);
        trabajo.setCreadoEn(LocalDateTime.now());
        trabajo.setResumen(resumen);
        return repository.save(trabajo);
    }

    private void cerrarTrabajosInterrumpidos() {
        List<TrabajoSincronizacion> interrumpidos = repository.findByEstadoIn(ESTADOS_ACTIVOS).stream()
                .filter(trabajo -> trabajo.getCreadoEn() != null && trabajo.getCreadoEn().isBefore(inicioInstancia))
                .toList();
        if (interrumpidos.isEmpty()) return;
        LocalDateTime ahora = LocalDateTime.now();
        for (TrabajoSincronizacion trabajo : interrumpidos) {
            trabajo.setEstado(EstadoTrabajoSincronizacion.ERROR);
            trabajo.setFinalizadoEn(ahora);
            trabajo.setResumen("El trabajo fue interrumpido por un reinicio de la aplicación.");
            trabajo.setDetalle("Puede iniciar un nuevo trabajo.");
        }
        repository.saveAll(interrumpidos);
    }
}

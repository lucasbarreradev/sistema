package com.sistema.service;

import com.sistema.model.CanalVenta;
import com.sistema.model.EstadoTrabajoSincronizacion;
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
        cerrarTrabajosInterrumpidos();
        if (repository.existsByEstadoIn(ESTADOS_ACTIVOS)) {
            throw new IllegalStateException("Ya hay una sincronización en proceso para este negocio");
        }

        long tenantId = TenantContext.require();
        TrabajoSincronizacion trabajo = new TrabajoSincronizacion();
        trabajo.setTenantId(tenantId);
        trabajo.setOrigen(origen);
        trabajo.setDestinos(destinosValidos.stream().map(Enum::name).collect(Collectors.joining(",")));
        trabajo.setEstado(EstadoTrabajoSincronizacion.PENDIENTE);
        trabajo.setCreadoEn(LocalDateTime.now());
        trabajo.setResumen("Esperando para comenzar...");
        trabajo = repository.save(trabajo);

        procesador.ejecutar(trabajo.getId(), tenantId, origen, List.copyOf(destinosValidos));
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

    private void cerrarTrabajosInterrumpidos() {
        List<TrabajoSincronizacion> interrumpidos = repository.findByEstadoIn(ESTADOS_ACTIVOS).stream()
                .filter(trabajo -> trabajo.getCreadoEn() != null && trabajo.getCreadoEn().isBefore(inicioInstancia))
                .toList();
        if (interrumpidos.isEmpty()) return;
        LocalDateTime ahora = LocalDateTime.now();
        for (TrabajoSincronizacion trabajo : interrumpidos) {
            trabajo.setEstado(EstadoTrabajoSincronizacion.ERROR);
            trabajo.setFinalizadoEn(ahora);
            trabajo.setResumen("La sincronización fue interrumpida por un reinicio de la aplicación.");
            trabajo.setDetalle("Puede iniciar una nueva sincronización.");
        }
        repository.saveAll(interrumpidos);
    }
}

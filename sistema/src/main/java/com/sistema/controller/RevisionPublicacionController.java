package com.sistema.controller;

import com.sistema.dto.RevisionProductoPublicacionDto;
import com.sistema.dto.RevisionPublicacionSesion;
import com.sistema.model.CanalVenta;
import com.sistema.service.ProductoService;
import com.sistema.service.PublicacionService;
import com.sistema.service.RevisionPublicacionService;
import com.sistema.service.TrabajoSincronizacionService;
import com.sistema.tenant.TenantContext;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Controller
@RequestMapping("/canales/publicar")
public class RevisionPublicacionController {
    private static final Logger log = LoggerFactory.getLogger(
            RevisionPublicacionController.class);
    static final String ATRIBUTO_SESION = "revisionPublicacion";

    private final ProductoService productoService;
    private final PublicacionService publicacionService;
    private final RevisionPublicacionService revisionService;
    private final TrabajoSincronizacionService trabajoService;

    public RevisionPublicacionController(
            ProductoService productoService,
            PublicacionService publicacionService,
            RevisionPublicacionService revisionService,
            TrabajoSincronizacionService trabajoService) {
        this.productoService = productoService;
        this.publicacionService = publicacionService;
        this.revisionService = revisionService;
        this.trabajoService = trabajoService;
    }

    @PostMapping("/revisar")
    public String revisarSeleccion(
            @RequestParam(required = false) List<Long> productoIds,
            @RequestParam(required = false) List<CanalVenta> canales,
            @RequestParam(defaultValue = "false") boolean seleccionarTodosResultados,
            @RequestParam(defaultValue = "") String productoQ,
            HttpSession session,
            RedirectAttributes ra) {
        try {
            if (canales == null || canales.isEmpty()) {
                throw new IllegalArgumentException("Seleccione al menos un canal");
            }
            var configuracion = publicacionService.estadoConfiguracion();
            List<CanalVenta> canalesValidos = canales.stream().distinct().toList();
            for (CanalVenta canal : canalesValidos) {
                if (!Boolean.TRUE.equals(configuracion.get(canal))) {
                    throw new IllegalArgumentException(canal.getDescripcion() + " no está configurado");
                }
            }
            List<Long> seleccionados = seleccionarTodosResultados
                    ? productoService.getIdsProductosListado(productoQ)
                    : productoIds;
            if (seleccionados == null || seleccionados.isEmpty()) {
                throw new IllegalArgumentException("Seleccione al menos un producto");
            }
            RevisionPublicacionSesion revision = new RevisionPublicacionSesion(
                    TenantContext.require(),
                    seleccionados.stream().distinct().toList(), canalesValidos);
            session.setAttribute(ATRIBUTO_SESION, revision);
            return "redirect:/canales/publicar/revision";
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/canales#productos-publicacion";
        }
    }

    @GetMapping("/revision")
    public String revision(HttpSession session, Model model, RedirectAttributes ra) {
        RevisionPublicacionSesion seleccion = seleccion(session);
        if (seleccion == null) {
            ra.addFlashAttribute("error", "Seleccione los productos que desea revisar");
            return "redirect:/canales#productos-publicacion";
        }
        try {
            List<RevisionProductoPublicacionDto> productos = revisionService.revisar(
                    seleccion.productoIds(), seleccion.canales());
            long listos = productos.stream().filter(RevisionProductoPublicacionDto::isListo).count();
            model.addAttribute("revisionProductos", productos);
            model.addAttribute("revisionCanales", seleccion.canales());
            model.addAttribute("cantidadListos", listos);
            model.addAttribute("cantidadPendientes", productos.size() - listos);
            model.addAttribute("todosListos", !productos.isEmpty() && listos == productos.size());
            return "canales/revision-publicacion";
        } catch (RuntimeException e) {
            log.error("No se pudo preparar la revisión de {} producto(s) para el tenant {}",
                    seleccion.productoIds().size(), seleccion.tenantId(), e);
            ra.addFlashAttribute("error",
                    "No se pudo preparar la revisión. Intente nuevamente; si continúa, revise el registro del servidor.");
            return "redirect:/canales#productos-publicacion";
        }
    }

    @PostMapping("/revision/guardar")
    public String guardar(
            @RequestParam Long productoId,
            @RequestParam String titulo,
            @RequestParam(defaultValue = "") String descripcionMercadoLibre,
            @RequestParam(defaultValue = "") String categoriaMercadoLibre,
            @RequestParam(defaultValue = "") String marca,
            @RequestParam(defaultValue = "") String modelo,
            @RequestParam(required = false) Integer stock,
            @RequestParam(defaultValue = "") String precio,
            @RequestParam(defaultValue = "") String modoEnvio,
            @RequestParam(defaultValue = "false") boolean envioGratis,
            @RequestParam(defaultValue = "false") boolean retiroPersonal,
            @RequestParam(defaultValue = "") String tipoPublicacion,
            @RequestParam(required = false) List<Long> varianteIds,
            @RequestParam(required = false) List<Integer> stocksVariantes,
            @RequestParam(required = false) List<String> preciosVariantes,
            @RequestParam Map<String, String> parametros,
            HttpSession session,
            RedirectAttributes ra) {
        if (seleccion(session) == null) return "redirect:/canales#productos-publicacion";
        try {
            revisionService.actualizar(
                    productoId, titulo, descripcionMercadoLibre, categoriaMercadoLibre,
                    marca, modelo, stock, decimal(precio), modoEnvio, envioGratis,
                    retiroPersonal, tipoPublicacion, varianteIds, stocksVariantes,
                    preciosVariantes == null ? List.of()
                            : preciosVariantes.stream().map(this::decimal).toList(),
                    parametros);
            ra.addFlashAttribute("mensaje", "Cambios guardados. El producto se volvió a validar.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/canales/publicar/revision#producto-" + productoId;
    }

    @PostMapping("/revision/confirmar")
    public String confirmar(
            @RequestParam(required = false) List<Long> productoIds,
            HttpSession session,
            RedirectAttributes ra) {
        RevisionPublicacionSesion seleccion = seleccion(session);
        if (seleccion == null) return "redirect:/canales#productos-publicacion";
        try {
            List<Long> elegidos = productoIds == null ? List.of()
                    : productoIds.stream().filter(java.util.Objects::nonNull)
                    .distinct().toList();
            if (elegidos.isEmpty()) {
                throw new IllegalArgumentException(
                        "Seleccione al menos un producto listo para publicar");
            }
            Set<Long> permitidos = new LinkedHashSet<>(seleccion.productoIds());
            if (!permitidos.containsAll(elegidos)) {
                throw new IllegalArgumentException(
                        "La selección contiene productos que no pertenecen a esta revisión");
            }
            List<RevisionProductoPublicacionDto> revision = revisionService.revisar(
                    elegidos, seleccion.canales());
            if (revision.size() != elegidos.size()) {
                throw new IllegalArgumentException(
                        "Uno de los productos seleccionados ya no está disponible");
            }
            List<RevisionProductoPublicacionDto> pendientes = revision.stream()
                    .filter(producto -> !producto.isListo()).toList();
            if (!pendientes.isEmpty()) {
                throw new IllegalArgumentException("Todavía hay " + pendientes.size()
                        + " producto(s) seleccionados con datos pendientes. Corríjalos o desmárquelos.");
            }
            var trabajo = trabajoService.iniciarPublicacion(
                    elegidos, seleccion.canales());
            session.removeAttribute(ATRIBUTO_SESION);
            ra.addFlashAttribute("mensaje", "Publicación iniciada en segundo plano (trabajo #"
                    + trabajo.getId() + ") para " + revision.size() + " producto(s).");
            return "redirect:/canales";
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/canales/publicar/revision";
        }
    }

    @PostMapping("/revision/quitar")
    public String quitarDeRevision(
            @RequestParam Long productoId,
            HttpSession session,
            RedirectAttributes ra) {
        RevisionPublicacionSesion seleccion = seleccion(session);
        if (seleccion == null) return "redirect:/canales#productos-publicacion";
        List<Long> restantes = seleccion.productoIds().stream()
                .filter(id -> !id.equals(productoId))
                .toList();
        if (restantes.size() == seleccion.productoIds().size()) {
            ra.addFlashAttribute("error", "El producto no pertenece a esta revisión");
            return "redirect:/canales/publicar/revision";
        }
        if (restantes.isEmpty()) {
            session.removeAttribute(ATRIBUTO_SESION);
            ra.addFlashAttribute("mensaje", "Se quitaron todos los productos de la revisión");
            return "redirect:/canales#productos-publicacion";
        }
        session.setAttribute(ATRIBUTO_SESION, new RevisionPublicacionSesion(
                seleccion.tenantId(), restantes, seleccion.canales()));
        ra.addFlashAttribute("mensaje",
                "Producto quitado de esta revisión. No fue eliminado del sistema.");
        return "redirect:/canales/publicar/revision";
    }

    @PostMapping("/revision/cancelar")
    public String cancelar(HttpSession session) {
        session.removeAttribute(ATRIBUTO_SESION);
        return "redirect:/canales#productos-publicacion";
    }

    private RevisionPublicacionSesion seleccion(HttpSession session) {
        Object valor = session.getAttribute(ATRIBUTO_SESION);
        if (!(valor instanceof RevisionPublicacionSesion revision)) return null;
        Long tenantActual = TenantContext.get();
        if (tenantActual == null || revision.tenantId() != tenantActual) {
            session.removeAttribute(ATRIBUTO_SESION);
            return null;
        }
        return revision;
    }

    private BigDecimal decimal(String valor) {
        if (valor == null || valor.isBlank()) return null;
        try {
            return new BigDecimal(valor.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Ingrese un precio válido");
        }
    }
}

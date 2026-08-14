<div class="modal fade" id="modalNuevoCliente" tabindex="-1" role="dialog"
     aria-labelledby="tituloModalNuevoCliente" aria-hidden="true">
    <div class="modal-dialog modal-lg" role="document">
        <div class="modal-content">
            <div class="modal-header">
                <h5 class="modal-title" id="tituloModalNuevoCliente">Nuevo cliente</h5>
                <button type="button" class="close" data-dismiss="modal" aria-label="Cerrar">
                    <span aria-hidden="true">&times;</span>
                </button>
            </div>
            <form id="formClienteRapido"
                  action="${pageContext.request.contextPath}/clientes/crear-rapido"
                  method="post">
                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}">
                <div class="modal-body">
                    <div id="errorClienteRapido" class="alert alert-danger d-none"></div>
                    <div class="row">
                        <div class="col-md-6 mb-3">
                            <label for="clienteRapidoNombre">Nombre *</label>
                            <input id="clienteRapidoNombre" name="nombre" class="form-control"
                                   maxlength="120" required autocomplete="off">
                        </div>
                        <div class="col-md-6 mb-3">
                            <label for="clienteRapidoApellido">Apellido</label>
                            <input id="clienteRapidoApellido" name="apellido" class="form-control"
                                   maxlength="120" autocomplete="off">
                        </div>
                        <div class="col-md-6 mb-3">
                            <label for="clienteRapidoTelefono">Tel&eacute;fono</label>
                            <input id="clienteRapidoTelefono" name="telefono" class="form-control"
                                   maxlength="100">
                        </div>
                        <div class="col-md-6 mb-3">
                            <label for="clienteRapidoDni">DNI / CUIT</label>
                            <input id="clienteRapidoDni" name="dni" class="form-control"
                                   maxlength="40">
                        </div>
                        <div class="col-md-6 mb-3">
                            <label for="clienteRapidoEmail">Email</label>
                            <input type="email" id="clienteRapidoEmail" name="email"
                                   class="form-control" maxlength="180">
                        </div>
                        <div class="col-md-6 mb-3">
                            <label for="clienteRapidoDireccion">Direcci&oacute;n</label>
                            <input id="clienteRapidoDireccion" name="direccion" class="form-control"
                                   maxlength="250">
                        </div>
                        <div class="col-md-6 mb-3">
                            <label for="clienteRapidoCondicionIva">Condici&oacute;n IVA</label>
                            <select id="clienteRapidoCondicionIva" name="condicionIva"
                                    class="form-control" required>
                                <option value="RESPONSABLE_INSCRIPTO">Responsable Inscripto</option>
                                <option value="IVA_SUJETO_EXENTO">IVA Sujeto Exento</option>
                                <option value="CONSUMIDOR_FINAL" selected>Consumidor Final</option>
                                <option value="RESPONSABLE_MONOTRIBUTO">Responsable Monotributo</option>
                                <option value="SUJETO_NO_CATEGORIZADO">Sujeto No Categorizado</option>
                                <option value="PROVEEDOR_DEL_EXTERIOR">Proveedor del Exterior</option>
                                <option value="CLIENTE_DEL_EXTERIOR">Cliente del Exterior</option>
                                <option value="IVA_LIBERADO_LEY_19640">IVA Liberado - Ley N.&deg; 19.640</option>
                                <option value="MONOTRIBUTISTA_SOCIAL">Monotributista Social</option>
                                <option value="IVA_NO_ALCANZADO">IVA No Alcanzado</option>
                                <option value="MONOTRIBUTO_TRABAJADOR_INDEPENDIENTE_PROMOVIDO">
                                    Monotributo Trabajador Independiente Promovido
                                </option>
                            </select>
                        </div>
                    </div>
                </div>
                <div class="modal-footer">
                    <button type="button" class="btn btn-secondary" data-dismiss="modal">Cancelar</button>
                    <button type="submit" class="btn btn-success" id="guardarClienteRapido">
                        Guardar y seleccionar
                    </button>
                </div>
            </form>
        </div>
    </div>
</div>

<script>
document.addEventListener('DOMContentLoaded', function () {
    const formulario = document.getElementById('formClienteRapido');
    if (!formulario) return;
    const modal = document.getElementById('modalNuevoCliente');
    let fondoModal = null;

    function usaBootstrapModal() {
        return window.jQuery && window.jQuery.fn
            && typeof window.jQuery.fn.modal === 'function';
    }

    function abrirModal() {
        if (usaBootstrapModal()) return;
        modal.style.display = 'block';
        modal.classList.add('show');
        modal.setAttribute('aria-modal', 'true');
        modal.removeAttribute('aria-hidden');
        document.body.classList.add('modal-open');
        fondoModal = document.createElement('div');
        fondoModal.className = 'modal-backdrop fade show';
        document.body.appendChild(fondoModal);
        document.getElementById('clienteRapidoNombre').focus();
    }

    function cerrarModal() {
        if (usaBootstrapModal()) {
            window.jQuery('#modalNuevoCliente').modal('hide');
            return;
        }
        modal.style.display = 'none';
        modal.classList.remove('show');
        modal.setAttribute('aria-hidden', 'true');
        modal.removeAttribute('aria-modal');
        document.body.classList.remove('modal-open');
        if (fondoModal) fondoModal.remove();
        fondoModal = null;
    }

    if (!usaBootstrapModal()) {
        document.querySelectorAll('[data-target="#modalNuevoCliente"]').forEach(function (boton) {
            boton.addEventListener('click', abrirModal);
        });
        modal.querySelectorAll('[data-dismiss="modal"]').forEach(function (boton) {
            boton.addEventListener('click', cerrarModal);
        });
        modal.addEventListener('click', function (evento) {
            if (evento.target === modal) cerrarModal();
        });
    }

    formulario.addEventListener('submit', async function (evento) {
        evento.preventDefault();
        const boton = document.getElementById('guardarClienteRapido');
        const error = document.getElementById('errorClienteRapido');
        boton.disabled = true;
        error.classList.add('d-none');

        try {
            const respuesta = await fetch(formulario.action, {
                method: 'POST',
                body: new FormData(formulario),
                headers: {'Accept': 'application/json'}
            });
            const datos = await respuesta.json();
            if (!respuesta.ok) {
                throw new Error(datos.error || 'No se pudo crear el cliente');
            }

            if (typeof seleccionarCliente === 'function') {
                seleccionarCliente(datos.id, datos.nombre || '', datos.apellido || '');
            } else {
                document.getElementById('clienteId').value = datos.id;
                document.getElementById('buscarCliente').value =
                    [datos.nombre, datos.apellido].filter(Boolean).join(' ');
            }
            formulario.reset();
            cerrarModal();
        } catch (e) {
            error.textContent = e.message;
            error.classList.remove('d-none');
        } finally {
            boton.disabled = false;
        }
    });
});
</script>

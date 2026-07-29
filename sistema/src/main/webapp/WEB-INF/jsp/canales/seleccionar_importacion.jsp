<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html lang="es">
<head><jsp:include page="/WEB-INF/jsp/head.jsp"/></head>
<body id="page-top">
<div id="wrapper">
    <jsp:include page="/WEB-INF/jsp/nav_bar.jsp"/>
    <div id="content-wrapper" class="d-flex flex-column">
        <div id="content">
            <div class="container-fluid py-4">
                <div class="d-flex align-items-center justify-content-between mb-4">
                    <div>
                        <h1 class="h3 mb-1 text-gray-800">Traer productos desde ${canal.descripcion}</h1>
                        <p class="text-muted mb-0">
                            Elegí únicamente los productos que querés crear o actualizar en el sistema.
                        </p>
                    </div>
                    <a class="btn btn-outline-secondary"
                       href="${pageContext.request.contextPath}/canales">Volver</a>
                </div>

                <form method="post"
                      action="${pageContext.request.contextPath}/canales/importar/${canal}/seleccionados"
                      id="formImportacion">
                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}">
                    <div class="card shadow">
                        <div class="card-header py-3">
                            <div class="row align-items-center">
                                <div class="col-md-5 mb-2 mb-md-0">
                                    <input id="buscarRemoto" class="form-control"
                                           placeholder="Buscar por SKU, nombre o ID externo">
                                </div>
                                <div class="col-md-7 text-md-right">
                                    <button type="button" class="btn btn-sm btn-outline-primary"
                                            id="seleccionarVisibles">Seleccionar visibles</button>
                                    <button type="button" class="btn btn-sm btn-outline-secondary"
                                            id="quitarSeleccion">Quitar selección</button>
                                    <span class="badge badge-info ml-2" id="cantidadSeleccionada">0 seleccionados</span>
                                </div>
                            </div>
                        </div>
                        <div class="card-body p-0">
                            <div class="table-responsive" style="max-height:620px;overflow:auto">
                                <table class="table table-bordered table-hover mb-0" id="tablaRemotos">
                                    <thead class="thead-dark">
                                    <tr>
                                        <th style="width:42px"></th>
                                        <th>Foto</th>
                                        <th>ID externo</th>
                                        <th>SKU</th>
                                        <th>Producto</th>
                                        <th>Stock</th>
                                        <th>Precio</th>
                                        <th>Variantes</th>
                                    </tr>
                                    </thead>
                                    <tbody>
                                    <c:forEach items="${productosRemotos}" var="producto">
                                        <tr>
                                            <td>
                                                <input type="checkbox" class="producto-remoto"
                                                       name="idsExternos" value="${producto.idExterno}">
                                            </td>
                                            <td style="width:64px">
                                                <c:if test="${not empty producto.fotoUrl}">
                                                    <img src="${producto.fotoUrl}" alt=""
                                                         style="width:48px;height:48px;object-fit:contain;background:#fff;border-radius:5px">
                                                </c:if>
                                            </td>
                                            <td><c:out value="${producto.idExterno}"/></td>
                                            <td><c:out value="${producto.sku}"/></td>
                                            <td><c:out value="${producto.descripcion}"/></td>
                                            <td><c:out value="${producto.stock}"/></td>
                                            <td><c:out value="${producto.precio}"/></td>
                                            <td><c:out value="${producto.variantes}"/></td>
                                        </tr>
                                    </c:forEach>
                                    <c:if test="${empty productosRemotos}">
                                        <tr><td colspan="8" class="text-center text-muted py-4">
                                            El canal no devolvió productos.
                                        </td></tr>
                                    </c:if>
                                    </tbody>
                                </table>
                            </div>
                        </div>
                        <div class="card-footer d-flex align-items-center justify-content-between">
                            <small class="text-muted">
                                Esta lista queda guardada. Use “Actualizar lista” únicamente cuando haya cambios en el canal.
                            </small>
                            <button class="btn btn-success" type="submit" id="importarSeleccionados">
                                <i class="fa-solid fa-file-import mr-1"></i>Importar seleccionados
                            </button>
                        </div>
                    </div>
                </form>
            </div>
        </div>
    </div>
</div>
<script>
(function () {
    const filas = Array.from(document.querySelectorAll('#tablaRemotos tbody tr'))
        .filter(fila => fila.querySelector('.producto-remoto'));
    const checks = filas.map(fila => fila.querySelector('.producto-remoto'));
    const contador = document.getElementById('cantidadSeleccionada');
    const formulario = document.getElementById('formImportacion');

    function actualizarContador() {
        const cantidad = checks.filter(check => check.checked).length;
        contador.textContent = cantidad + (cantidad === 1 ? ' seleccionado' : ' seleccionados');
    }

    document.getElementById('buscarRemoto').addEventListener('input', function () {
        const consulta = this.value.trim().toLowerCase();
        filas.forEach(fila => {
            fila.style.display = fila.textContent.toLowerCase().includes(consulta) ? '' : 'none';
        });
    });

    document.getElementById('seleccionarVisibles').addEventListener('click', function () {
        filas.filter(fila => fila.style.display !== 'none')
            .forEach(fila => fila.querySelector('.producto-remoto').checked = true);
        actualizarContador();
    });

    document.getElementById('quitarSeleccion').addEventListener('click', function () {
        checks.forEach(check => check.checked = false);
        actualizarContador();
    });

    checks.forEach(check => check.addEventListener('change', actualizarContador));
    formulario.addEventListener('submit', function (evento) {
        if (!checks.some(check => check.checked)) {
            evento.preventDefault();
            window.alert('Seleccione al menos un producto para importar.');
            return;
        }
        const boton = document.getElementById('importarSeleccionados');
        boton.disabled = true;
        boton.innerHTML = '<i class="fa-solid fa-spinner fa-spin mr-1"></i>Iniciando...';
    });
})();
</script>
</body>
</html>

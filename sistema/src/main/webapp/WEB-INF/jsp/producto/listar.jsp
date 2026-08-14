<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>

<!DOCTYPE html>
<html lang="en">

<head>
    <jsp:include page="/WEB-INF/jsp/head.jsp"/>
</head>

<body id="page-top">

<div id="wrapper">

    <jsp:include page="/WEB-INF/jsp/nav_bar.jsp"/>

    <div id="content-wrapper" class="d-flex flex-column">

        <div id="content">

            <div class="container-fluid">

                <!-- Mensajes -->
                <c:if test="${not empty mensaje}">
                    <div class="alert alert-success">${mensaje}</div>
                </c:if>

                <c:if test="${not empty error}">
                    <div class="alert alert-danger">${error}</div>
                </c:if>

                <!-- Header -->
                <div class="d-sm-flex align-items-center justify-content-between mb-4 mt-4">
                    <h1 class="h3 mb-0 text-gray-800">Productos</h1>

                    <a href="${pageContext.request.contextPath}/productos/nuevo/limpio"
                       class="btn btn-primary">
                        + Nuevo Producto
                    </a>
                    <a href="${pageContext.request.contextPath}/canales"
                       class="btn btn-info ml-2">
                        Importar / Publicar
                    </a>

                </div>

                <div class="card border-left-primary shadow mb-4">
                    <div class="card-body">
                        <form id="formEdicionMasiva" method="post"
                              action="${pageContext.request.contextPath}/productos/ajustar-precios"
                              class="row align-items-end">
                            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}">
                            <div class="col-md-4 mb-3 mb-md-0">
                                <label for="porcentajeMasivo" class="font-weight-bold">
                                    Ajustar precios seleccionados (%)
                                </label>
                                <input id="porcentajeMasivo" name="porcentaje" type="number"
                                       class="form-control" step="0.01" min="-99.99" max="1000"
                                       placeholder="-15 o 10" required>
                            </div>
                            <div class="col-md-5 mb-3 mb-md-0">
                                <small class="text-muted">
                                    Use -15 para reducir 15% o 10 para aumentar 10%. Se modifican
                                    Contado, Tarjeta y Cuenta Corriente, también en las variantes.
                                    El precio de compra y los canales externos no se modifican.
                                </small>
                            </div>
                            <div class="col-md-3 text-md-right">
                                <span id="cantidadProductosSeleccionados"
                                      class="badge badge-info mr-2">0 seleccionados</span>
                                <button class="btn btn-primary" type="submit">Aplicar ajuste</button>
                            </div>
                        </form>
                    </div>
                </div>

                <!-- Card -->
                <div class="card shadow mb-4">

                    <div class="card-header py-3">
                        <h6 class="m-0 font-weight-bold text-primary">
                            Listado de Productos
                        </h6>
                    </div>
                    <form method="get" action="${pageContext.request.contextPath}/productos"
                          class="form-inline m-4">
                        <input type="hidden" name="size" value="${tamanioPagina}">
                        <input type="search" name="q" class="form-control mr-2"
                               style="width:300px" value="${fn:escapeXml(busquedaProductos)}"
                               placeholder="Buscar por SKU o producto...">
                        <button class="btn btn-outline-primary" type="submit">Buscar</button>
                        <c:if test="${not empty busquedaProductos}">
                            <a class="btn btn-link" href="${pageContext.request.contextPath}/productos">Limpiar</a>
                        </c:if>
                    </form>

                    <div class="card-body">
                        <div class="table-responsive">

                            <table class="table table-bordered table-hover"
                                   id="dataTable"
                                   width="100%" cellspacing="0">

                                <thead class="table-dark">
                                <tr>
                                    <th style="width:42px">
                                        <input type="checkbox" id="seleccionarTodosProductos"
                                               title="Seleccionar todos los productos visibles">
                                    </th>
                                    <th>Foto</th>
                                    <th>SKU</th>
                                    <th>Descripción</th>
                                    <th>Cantidad</th>
                                    <th>Precio Contado</th>
                                    <th>Precio Cuenta Corriente</th>
                                    <th>Precio Tarjeta</th>
                                    <th>Proveedor</th>
                                    <th>Acciones</th>
                                </tr>
                                </thead>

                                <tbody>
                                <c:forEach items="${productos}" var="p">
                                    <tr>
                                        <td class="text-center">
                                            <input type="checkbox" class="producto-masivo"
                                                   form="formEdicionMasiva"
                                                   name="productoIds" value="${p.id}">
                                        </td>
                                        <td class="text-center">
                                            <c:choose>
                                                <c:when test="${p.tieneFoto}">
                                                    <img src="${pageContext.request.contextPath}/productos/${p.id}/foto"
                                                         loading="lazy" decoding="async"
                                                         alt="Foto" style="width:52px;height:52px;object-fit:contain;background:#fff;border-radius:6px;">
                                                </c:when>
                                                <c:otherwise><span class="text-muted">Sin foto</span></c:otherwise>
                                            </c:choose>
                                        </td>
                                        <td>${p.sku}</td>
                                        <td>${p.descripcion}</td>
                                        <td>${p.stockTotal}<c:if test="${p.tieneVariantes}"><br><span class="badge badge-info">Variantes</span></c:if></td>
                                        <td>${p.precioContadoListado}</td>
                                        <td>${p.precioCuentaCorrienteListado}</td>
                                        <td>${p.precioTarjetaListado}</td>
                                        <td>
                                            <c:choose>
                                                <c:when test="${not empty p.proveedorNombre}">
                                                    <c:out value="${p.proveedorNombre}"/>
                                                </c:when>
                                                <c:otherwise>
                                                    <span class="text-muted">Sin proveedor</span>
                                                </c:otherwise>
                                            </c:choose>
                                        </td>

                                        <td class="text-center">
                                            <a class="btn btn-sm btn-info"
                                               href="${pageContext.request.contextPath}/productos/${p.id}/variantes">Variantes</a>
                                            <a class="btn btn-sm btn-warning"
                                               href="${pageContext.request.contextPath}/productos/editar/${p.id}">
                                                Editar
                                            </a>
                                            <form method="post"
                                                  action="${pageContext.request.contextPath}/productos/eliminar/${p.id}"
                                                  style="display:inline;"
                                                  onsubmit="return confirm('¿Eliminar producto?');">

                                                <input type="hidden"
                                                       name="${_csrf.parameterName}"
                                                       value="${_csrf.token}" />

                                                <button type="submit" class="btn btn-sm btn-danger">
                                                    Eliminar
                                                </button>
                                            </form>

                                        </td>
                                    </tr>
                                </c:forEach>
                                </tbody>

                            </table>

                        </div>
                        <div class="d-flex flex-wrap justify-content-between align-items-center mt-3">
                            <small class="text-muted">
                                ${paginaProductos.totalElements} producto(s) &middot;
                                P&aacute;gina ${paginaProductos.number + 1} de
                                ${paginaProductos.totalPages == 0 ? 1 : paginaProductos.totalPages}
                            </small>
                            <c:if test="${paginaProductos.totalPages > 1}">
                                <c:set var="paginaInicio" value="${paginaProductos.number > 2 ? paginaProductos.number - 2 : 0}"/>
                                <c:set var="paginaFin" value="${paginaProductos.number + 2 < paginaProductos.totalPages - 1 ? paginaProductos.number + 2 : paginaProductos.totalPages - 1}"/>
                                <nav aria-label="Paginaci&oacute;n de productos">
                                    <ul class="pagination pagination-sm mb-0">
                                        <c:url var="urlAnterior" value="/productos">
                                            <c:param name="page" value="${paginaProductos.number - 1}"/>
                                            <c:param name="size" value="${tamanioPagina}"/>
                                            <c:param name="q" value="${busquedaProductos}"/>
                                        </c:url>
                                        <li class="page-item ${paginaProductos.first ? 'disabled' : ''}">
                                            <a class="page-link" href="${paginaProductos.first ? '#' : urlAnterior}">Anterior</a>
                                        </li>
                                        <c:forEach begin="${paginaInicio}" end="${paginaFin}" var="numeroPagina">
                                            <c:url var="urlPagina" value="/productos">
                                                <c:param name="page" value="${numeroPagina}"/>
                                                <c:param name="size" value="${tamanioPagina}"/>
                                                <c:param name="q" value="${busquedaProductos}"/>
                                            </c:url>
                                            <li class="page-item ${numeroPagina == paginaProductos.number ? 'active' : ''}">
                                                <a class="page-link" href="${urlPagina}">${numeroPagina + 1}</a>
                                            </li>
                                        </c:forEach>
                                        <c:url var="urlSiguiente" value="/productos">
                                            <c:param name="page" value="${paginaProductos.number + 1}"/>
                                            <c:param name="size" value="${tamanioPagina}"/>
                                            <c:param name="q" value="${busquedaProductos}"/>
                                        </c:url>
                                        <li class="page-item ${paginaProductos.last ? 'disabled' : ''}">
                                            <a class="page-link" href="${paginaProductos.last ? '#' : urlSiguiente}">Siguiente</a>
                                        </li>
                                    </ul>
                                </nav>
                            </c:if>
                        </div>
                    </div>
                </div>

            </div> <!-- container-fluid -->
        </div> <!-- content -->

        <!-- Footer -->
        <footer class="sticky-footer bg-white">
            <div class="container my-auto">
                <div class="copyright text-center my-auto">
                    <span>Copyright &copy;</span>
                </div>
            </div>
        </footer>

    </div> <!-- content-wrapper -->
</div> <!-- wrapper -->
<script>
(function () {
    const checks = Array.from(document.querySelectorAll('.producto-masivo'));
    const seleccionarTodos = document.getElementById('seleccionarTodosProductos');
    const contador = document.getElementById('cantidadProductosSeleccionados');
    const formulario = document.getElementById('formEdicionMasiva');

    function actualizarContador() {
        const cantidad = checks.filter(check => check.checked).length;
        contador.textContent = cantidad + (cantidad === 1 ? ' seleccionado' : ' seleccionados');
        seleccionarTodos.checked = checks.length > 0 && checks.every(check => check.checked);
        seleccionarTodos.indeterminate = checks.some(check => check.checked)
            && !seleccionarTodos.checked;
    }

    seleccionarTodos.addEventListener('change', function () {
        checks.forEach(check => {
            const fila = check.closest('tr');
            if (fila.style.display !== 'none') check.checked = this.checked;
        });
        actualizarContador();
    });
    checks.forEach(check => check.addEventListener('change', actualizarContador));
    formulario.addEventListener('submit', function (evento) {
        if (!checks.some(check => check.checked)) {
            evento.preventDefault();
            window.alert('Seleccione al menos un producto.');
            return;
        }
        const porcentaje = document.getElementById('porcentajeMasivo').value;
        if (!window.confirm('¿Aplicar un ajuste de ' + porcentaje
                + '% a los precios de venta seleccionados?')) {
            evento.preventDefault();
        }
    });
})();
</script>

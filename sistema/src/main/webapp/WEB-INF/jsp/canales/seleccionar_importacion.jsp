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
                        <h1 class="h3 mb-1 text-gray-800">Transferir productos desde ${canal.descripcion}</h1>
                        <p class="text-muted mb-0">
                            Filtrá por categoría, elegí los productos y seleccioná las plataformas de destino.
                            Los productos siempre quedan actualizados también en el sistema.
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
                                <div class="col-md-4 mb-2 mb-md-0">
                                    <input id="buscarRemoto" class="form-control"
                                           placeholder="Buscar por SKU, nombre o ID externo">
                                </div>
                                <div class="col-md-3 mb-2 mb-md-0">
                                    <select id="categoriaRemota" class="form-control">
                                        <option value="">Todas las categorías</option>
                                        <c:forEach items="${categoriasRemotas}" var="categoria">
                                            <option value="${categoria.id}"><c:out value="${categoria.nombre}"/></option>
                                        </c:forEach>
                                    </select>
                                </div>
                                <div class="col-md-5 text-md-right">
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
                                        <th>Categoría</th>
                                        <th>Estado</th>
                                        <th>Stock</th>
                                        <th>Precio</th>
                                        <th>Variantes</th>
                                    </tr>
                                    </thead>
                                    <tbody>
                                    <c:forEach items="${productosRemotos}" var="producto">
                                        <tr data-categorias="<c:out value='${producto.categoriaIdsFiltro}'/>">
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
                                            <td>
                                                <c:forEach items="${producto.categorias}" var="categoria" varStatus="estado">
                                                    <c:if test="${not estado.first}"><br></c:if>
                                                    <c:out value="${categoria.nombre}"/>
                                                </c:forEach>
                                                <c:if test="${empty producto.categorias}">
                                                    <span class="text-muted">Sin categoría</span>
                                                </c:if>
                                            </td>
                                            <td>
                                                <span class="badge ${producto.estadoClase}">
                                                    <c:out value="${producto.estadoDescripcion}"/>
                                                </span>
                                            </td>
                                            <td><c:out value="${producto.stock}"/></td>
                                            <td><c:out value="${producto.precio}"/></td>
                                            <td><c:out value="${producto.variantes}"/></td>
                                        </tr>
                                    </c:forEach>
                                    <c:if test="${empty productosRemotos}">
                                        <tr><td colspan="10" class="text-center text-muted py-4">
                                            El canal no devolvió productos.
                                        </td></tr>
                                    </c:if>
                                    </tbody>
                                </table>
                            </div>
                        </div>
                        <div class="card-body border-top">
                            <div class="row align-items-start">
                                <div class="col-md-5">
                                    <label for="ajustePrecioPorcentaje" class="font-weight-bold">
                                        Ajuste de precio al importar (%)
                                    </label>
                                    <input id="ajustePrecioPorcentaje" name="ajustePrecioPorcentaje"
                                           type="number" class="form-control" value="0"
                                           step="0.01" min="-99.99" max="1000">
                                    <small class="text-muted">
                                        Use -15 para reducir 15% o 10 para aumentar 10%. Solo modifica
                                        los precios guardados en el sistema, incluidas las variantes.
                                    </small>
                                </div>
                                <div class="col-md-7">
                                    <label class="font-weight-bold d-block">Enviar también a</label>
                                    <div class="border rounded p-3 bg-light">
                                        <span class="badge badge-primary mr-3 mb-2">Sistema incluido</span>
                                        <c:forEach items="${canales}" var="destino">
                                            <c:if test="${destino != canal}">
                                                <label class="mr-3 mb-2">
                                                    <input type="checkbox" name="destinos" value="${destino}"
                                                           ${configuracion[destino] ? '' : 'disabled'}>
                                                    ${destino.descripcion}
                                                    <c:if test="${not configuracion[destino]}">
                                                        <small class="text-muted">(sin conectar)</small>
                                                    </c:if>
                                                </label>
                                            </c:if>
                                        </c:forEach>
                                    </div>
                                    <small class="text-muted">
                                        Si no seleccionás una plataforma, solamente se crearán o actualizarán en el sistema.
                                    </small>
                                </div>
                            </div>
                        </div>
                        <div class="card-footer d-flex align-items-center justify-content-between">
                            <small class="text-muted">
                                La lista queda guardada. Usá “Actualizar lista” cuando cambien productos o categorías en el canal.
                            </small>
                            <button class="btn btn-success" type="submit" id="importarSeleccionados">
                                <i class="fa-solid fa-arrow-right-arrow-left mr-1"></i>Transferir seleccionados
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
    const buscador = document.getElementById('buscarRemoto');
    const filtroCategoria = document.getElementById('categoriaRemota');

    function actualizarContador() {
        const cantidad = checks.filter(check => check.checked).length;
        contador.textContent = cantidad + (cantidad === 1 ? ' seleccionado' : ' seleccionados');
    }

    function aplicarFiltros() {
        const consulta = buscador.value.trim().toLowerCase();
        const categoria = filtroCategoria.value;
        filas.forEach(fila => {
            const coincideTexto = fila.textContent.toLowerCase().includes(consulta);
            const categorias = (fila.dataset.categorias || '').split('|');
            const coincideCategoria = !categoria || categorias.includes(categoria);
            fila.style.display = coincideTexto && coincideCategoria ? '' : 'none';
        });
    }

    buscador.addEventListener('input', aplicarFiltros);
    filtroCategoria.addEventListener('change', aplicarFiltros);

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

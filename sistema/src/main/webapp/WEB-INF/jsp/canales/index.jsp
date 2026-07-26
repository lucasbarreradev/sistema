<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="sec" uri="http://www.springframework.org/security/tags" %>
<!DOCTYPE html>
<html lang="es">
<head><jsp:include page="/WEB-INF/jsp/head.jsp"/></head>
<body id="page-top">
<div id="wrapper">
    <jsp:include page="/WEB-INF/jsp/nav_bar.jsp"/>
    <div id="content-wrapper" class="d-flex flex-column">
        <div id="content">
            <div class="container-fluid py-4">
                <h1 class="h3 mb-4 text-gray-800">Canales de venta</h1>

                <c:if test="${not empty mensaje}"><div class="alert alert-success">${mensaje}</div></c:if>
                <c:if test="${not empty error}"><div class="alert alert-danger">${error}</div></c:if>
                <c:if test="${not empty erroresImportacion}">
                    <div class="alert alert-warning"><strong>Filas que no se importaron:</strong><ul class="mb-0">
                        <c:forEach items="${erroresImportacion}" var="e"><li><c:out value="${e}"/></li></c:forEach>
                    </ul></div>
                </c:if>
                <c:if test="${not empty erroresPublicacion}">
                    <div class="alert alert-warning"><strong>Publicaciones con error:</strong><ul class="mb-0">
                        <c:forEach items="${erroresPublicacion}" var="e"><li><c:out value="${e}"/></li></c:forEach>
                    </ul></div>
                </c:if>


                <div class="row mb-4">
                    <div class="col-lg-5 mb-3">
                        <div class="card shadow h-100">
                            <div class="card-header py-3"><h6 class="m-0 font-weight-bold text-primary">Importar archivo de Mercado Libre</h6></div>
                            <div class="card-body">
                                <p class="text-muted">Crea y actualiza por SKU o número de publicación.</p>
                                <form method="post" enctype="multipart/form-data" action="${pageContext.request.contextPath}/canales/importar/mercadolibre"
                                      onsubmit="var boton=this.querySelector('button[type=submit]'); boton.disabled=true; boton.innerHTML='Importando...';">
                                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}">
                                    <input type="file" name="archivo" class="form-control mb-3"
                                           accept=".xlsx,.xls,.csv,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,application/vnd.ms-excel,text/csv" required>
                                    <button class="btn btn-primary" type="submit"><i class="fa-solid fa-file-import mr-1"></i> Importar productos</button>
                                </form>
                            </div>
                        </div>
                    </div>
                    <div class="col-lg-7 mb-3">
                        <div class="card shadow h-100">
                            <div class="card-header py-3"><h6 class="m-0 font-weight-bold text-primary">Estado de conexiones</h6></div>
                            <div class="card-body">
                                <div class="row">
                                    <c:forEach items="${canales}" var="canal">
                                        <div class="col-md-4 mb-3">
                                            <div class="border rounded p-3 h-100">
                                                <strong>${canal.descripcion}</strong><br>
                                                <c:choose>
                                                    <c:when test="${configuracion[canal]}"><span class="badge badge-success mt-2">Configurado</span></c:when>
                                                    <c:otherwise><span class="badge badge-secondary mt-2">Sin configurar</span></c:otherwise>
                                                </c:choose>
                                                <c:if test="${canal == 'MERCADO_LIBRE'}">
                                                    <sec:authorize access="hasRole('ADMIN')">
                                                        <div class="mt-3">
                                                            <c:choose>
                                                                <c:when test="${mercadoLibreOAuthDisponible}">
                                                                    <a class="btn btn-sm ${mercadoLibreConectado ? 'btn-outline-secondary' : 'btn-warning'}"
                                                                       href="${pageContext.request.contextPath}/canales/mercadolibre/conectar">
                                                                        <i class="fa-solid fa-link mr-1"></i>${mercadoLibreConectado ? 'Reconectar cuenta' : 'Conectar cuenta'}
                                                                    </a>
                                                                    <c:if test="${mercadoLibreConectado}">
                                                                        <form method="post" class="d-inline" action="${pageContext.request.contextPath}/canales/mercadolibre/desconectar"
                                                                              onsubmit="return confirm('¿Desconectar la cuenta de Mercado Libre?');">
                                                                            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}">
                                                                            <button class="btn btn-sm btn-outline-danger" type="submit">Desconectar</button>
                                                                        </form>
                                                                    </c:if>
                                                                    <small class="text-muted d-block mt-2">Ingresá con la cuenta principal de Mercado Libre y aceptá los permisos. No se solicita la contraseña dentro del sistema.</small>
                                                                </c:when>
                                                                <c:otherwise>
                                                                    <small class="text-danger d-block">El administrador debe configurar APP ID, Secret Key, clave de cifrado y URL de retorno.</small>
                                                                </c:otherwise>
                                                            </c:choose>
                                                        </div>
                                                    </sec:authorize>
                                                </c:if>
                                                <c:if test="${canal == 'WOOCOMMERCE'}">
                                                    <sec:authorize access="hasRole('ADMIN')">
                                                        <div class="mt-3">
                                                            <c:choose>
                                                                <c:when test="${wooCommerceConexionDisponible}">
                                                                    <form method="post" action="${pageContext.request.contextPath}/canales/woocommerce/conectar">
                                                                        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}">
                                                                        <label class="small mb-1">URL de la tienda</label>
                                                                        <input type="url" name="tiendaUrl" class="form-control form-control-sm mb-2"
                                                                               value="<c:out value="${wooCommerceUrl}"/>"
                                                                               placeholder="https://mitienda.com" required>
                                                                        <button class="btn btn-sm ${wooCommerceConectado ? 'btn-outline-secondary' : 'btn-warning'}" type="submit">
                                                                            <i class="fa-solid fa-link mr-1"></i>${wooCommerceConectado ? 'Reconectar cuenta' : 'Conectar cuenta'}
                                                                        </button>
                                                                    </form>
                                                                    <c:if test="${wooCommerceConectado}">
                                                                        <form method="post" class="mt-2" action="${pageContext.request.contextPath}/canales/woocommerce/desconectar"
                                                                              onsubmit="return confirm('¿Desconectar WooCommerce?');">
                                                                            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}">
                                                                            <button class="btn btn-sm btn-outline-danger" type="submit">Desconectar</button>
                                                                        </form>
                                                                    </c:if>
                                                                    <small class="text-muted d-block mt-2">Se abrirá WooCommerce para que ingrese a la cuenta y acepte los permisos.</small>
                                                                </c:when>
                                                                <c:otherwise>
                                                                    <small class="text-danger d-block">El administrador debe configurar PUBLIC_BASE_URL y la clave de cifrado.</small>
                                                                </c:otherwise>
                                                            </c:choose>
                                                        </div>
                                                    </sec:authorize>
                                                </c:if>
                                                <c:if test="${canal == 'TIENDANUBE'}">
                                                    <sec:authorize access="hasRole('ADMIN')">
                                                        <div class="mt-3">
                                                            <c:choose>
                                                                <c:when test="${tiendanubeOAuthDisponible}">
                                                                    <a class="btn btn-sm ${tiendanubeConectado ? 'btn-outline-secondary' : 'btn-warning'}"
                                                                       href="${pageContext.request.contextPath}/canales/tiendanube/conectar">
                                                                        <i class="fa-solid fa-link mr-1"></i>${tiendanubeConectado ? 'Reconectar cuenta' : 'Conectar cuenta'}
                                                                    </a>
                                                                    <c:if test="${tiendanubeConectado}">
                                                                        <form method="post" class="d-inline" action="${pageContext.request.contextPath}/canales/tiendanube/desconectar"
                                                                              onsubmit="return confirm('¿Desconectar Tiendanube?');">
                                                                            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}">
                                                                            <button class="btn btn-sm btn-outline-danger" type="submit">Desconectar</button>
                                                                        </form>
                                                                    </c:if>
                                                                    <small class="text-muted d-block mt-2">Ingresá en Tiendanube, seleccioná la tienda y aceptá los permisos. La aplicación del Portal de Socios debe tener <strong>Lectura de órdenes</strong> y <strong>Escritura de productos</strong>.</small>
                                                                </c:when>
                                                                <c:otherwise>
                                                                    <small class="text-danger d-block">El administrador debe configurar APP ID, Client Secret, clave de cifrado y esta URL de retorno:
                                                                        <code><c:out value="${tiendanubeRedirectUri}"/></code>
                                                                    </small>
                                                                </c:otherwise>
                                                            </c:choose>
                                                        </div>
                                                    </sec:authorize>
                                                </c:if>
                                                <form method="post" class="mt-3" action="${pageContext.request.contextPath}/canales/importar/${canal}">
                                                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}">
                                                    <button type="submit" class="btn btn-sm btn-outline-primary"
                                                            ${configuracionImportacion[canal] ? '' : 'disabled'}>
                                                        Traer al sistema
                                                    </button>
                                                </form>
                                            </div>
                                        </div>
                                    </c:forEach>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>

                <div class="card shadow mb-4">
                    <div class="card-header py-3"><h6 class="m-0 font-weight-bold text-primary">Sincronizar entre canales</h6></div>
                    <div class="card-body">
                        <p class="text-muted">Trae todo el catálogo activo del origen al sistema y luego crea o actualiza esos productos en los destinos seleccionados.</p>
                        <form method="post" action="${pageContext.request.contextPath}/canales/sincronizar">
                            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}">
                            <div class="row align-items-end">
                                <div class="col-md-4 mb-3">
                                    <label>Canal de origen</label>
                                    <select name="origen" class="form-control" required>
                                        <c:forEach items="${canales}" var="canal">
                                            <option value="${canal}" ${configuracionImportacion[canal] ? '' : 'disabled'}>${canal.descripcion}</option>
                                        </c:forEach>
                                    </select>
                                </div>
                                <div class="col-md-6 mb-3">
                                    <label class="d-block">Canales de destino</label>
                                    <c:forEach items="${canales}" var="canal">
                                        <label class="mr-3"><input type="checkbox" name="destinos" value="${canal}" ${configuracion[canal] ? '' : 'disabled'}> ${canal.descripcion}</label>
                                    </c:forEach>
                                </div>
                                <div class="col-md-2 mb-3">
                                    <button class="btn btn-success btn-block" type="submit">Sincronizar</button>
                                </div>
                            </div>
                        </form>
                        <small class="text-muted">Si un SKU ya existe se actualiza. Si no existe, se crea. El origen seleccionado define nombre, precio, stock y foto.</small>
                    </div>
                </div>

                <form method="post" action="${pageContext.request.contextPath}/canales/publicar">
                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}">
                    <div class="card shadow mb-4">
                        <div class="card-header py-3 d-flex align-items-center justify-content-between">
                            <h6 class="m-0 font-weight-bold text-primary">Seleccionar productos para publicar o sincronizar</h6>
                            <div>
                                <c:forEach items="${canales}" var="canal">
                                    <label class="mr-3 mb-0">
                                        <input type="checkbox" name="canales" value="${canal}" ${configuracion[canal] ? '' : 'disabled'}>
                                        ${canal.descripcion}
                                    </label>
                                </c:forEach>
                            </div>
                        </div>
                        <div class="card-body">
                            <div class="d-flex mb-3">
                                <button type="button" class="btn btn-sm btn-outline-secondary mr-2" id="seleccionarTodos">Seleccionar todos</button>
                                <input id="buscarProducto" class="form-control ml-auto" style="max-width:300px" placeholder="Buscar por SKU o nombre">
                            </div>
                            <div class="table-responsive" style="max-height:460px;overflow:auto">
                                <table class="table table-bordered table-hover" id="tablaProductos">
                                    <thead class="thead-dark"><tr><th></th><th>Foto</th><th>SKU</th><th>Producto</th><th>Stock</th><th>Precio</th></tr></thead>
                                    <tbody>
                                    <c:forEach items="${productos}" var="p">
                                        <tr>
                                            <td><input type="checkbox" class="producto-check" name="productoIds" value="${p.id}"></td>
                                            <td><c:if test="${p.tieneFoto()}"><img src="${pageContext.request.contextPath}/productos/${p.id}/foto" alt="" style="width:44px;height:44px;object-fit:cover;border-radius:5px"></c:if></td>
                                            <td><c:out value="${p.sku}"/></td><td><c:out value="${p.descripcion}"/></td><td>${p.stockTotal}</td><td>${p.precioContado}</td>
                                        </tr>
                                    </c:forEach>
                                    </tbody>
                                </table>
                            </div>
                            <button type="submit" class="btn btn-success mt-3"><i class="fa-solid fa-cloud-arrow-up mr-1"></i> Publicar seleccionados</button>
                        </div>
                    </div>
                </form>

                <div class="card shadow mb-4">
                    <div class="card-header py-3"><h6 class="m-0 font-weight-bold text-primary">Últimas sincronizaciones</h6></div>
                    <div class="card-body table-responsive">
                        <table class="table table-sm table-bordered">
                            <thead><tr><th>Producto</th><th>Canal</th><th>Estado</th><th>ID externo</th><th>Fecha</th><th>Detalle</th></tr></thead>
                            <tbody>
                            <c:forEach items="${publicaciones}" var="pub">
                                <tr><td><c:out value="${pub.producto.descripcion}"/></td><td>${pub.canal.descripcion}</td>
                                    <td><span class="badge ${pub.estado == 'PUBLICADO' ? 'badge-success' : 'badge-danger'}">${pub.estado}</span></td>
                                    <td><c:out value="${pub.idExterno}"/></td><td>${pub.fechaActualizacionFormateada}</td><td><c:out value="${pub.ultimoError}"/></td></tr>
                            </c:forEach>
                            <c:if test="${empty publicaciones}"><tr><td colspan="6" class="text-center text-muted">Todavía no hay publicaciones.</td></tr></c:if>
                            </tbody>
                        </table>
                    </div>
                </div>
            </div>
        </div>
    </div>
</div>
<script>
document.getElementById('seleccionarTodos').addEventListener('click', function () {
    const visibles = Array.from(document.querySelectorAll('#tablaProductos tbody tr')).filter(r => r.style.display !== 'none');
    const marcar = visibles.some(r => !r.querySelector('.producto-check').checked);
    visibles.forEach(r => r.querySelector('.producto-check').checked = marcar);
});
document.getElementById('buscarProducto').addEventListener('input', function () {
    const q = this.value.toLowerCase();
    document.querySelectorAll('#tablaProductos tbody tr').forEach(r => r.style.display = r.textContent.toLowerCase().includes(q) ? '' : 'none');
});
</script>
</body>
</html>

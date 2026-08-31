<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="sec" uri="http://www.springframework.org/security/tags" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
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

                <c:if test="${not empty erroresUltimaSincronizacion}">
                    <div class="card shadow mb-4 border-left-warning" id="errores-ultima-sincronizacion">
                        <div class="card-header py-3 d-flex flex-wrap align-items-center justify-content-between">
                            <div>
                                <h6 class="m-0 font-weight-bold text-warning">
                                    Errores de la última sincronización #${ultimaSincronizacion.id}
                                </h6>
                                <small class="text-muted">
                                    ${fn:length(erroresUltimaSincronizacion)} producto(s) para revisar. Se muestran todos los errores guardados.
                                </small>
                            </div>
                            <input id="filtroErroresSincronizacion" type="search" class="form-control form-control-sm mt-2 mt-md-0"
                                   style="max-width:320px" placeholder="Buscar SKU, color, material, GTIN...">
                        </div>
                        <div class="card-body table-responsive p-0">
                            <table class="table table-sm table-striped table-bordered mb-0" id="tablaErroresSincronizacion">
                                <thead class="thead-light">
                                <tr><th>Producto / SKU</th><th>Canal</th><th>Qué corregir</th><th>Detalle completo</th><th>Acción</th></tr>
                                </thead>
                                <tbody>
                                <c:forEach items="${erroresUltimaSincronizacion}" var="errorSinc">
                                    <tr class="fila-error-sincronizacion">
                                        <td class="font-weight-bold"><c:out value="${errorSinc.referencia}"/></td>
                                        <td><c:out value="${errorSinc.canal}"/></td>
                                        <td style="min-width:190px">
                                            <c:forEach items="${errorSinc.correcciones}" var="correccion">
                                                <span class="badge badge-warning mr-1 mb-1"><c:out value="${correccion}"/></span>
                                            </c:forEach>
                                        </td>
                                        <td class="small" style="min-width:380px; white-space:normal">
                                            <c:out value="${errorSinc.mensaje}"/>
                                        </td>
                                        <td class="text-nowrap">
                                            <c:choose>
                                                <c:when test="${not empty errorSinc.productoId}">
                                                    <a class="btn btn-sm btn-primary"
                                                       href="${pageContext.request.contextPath}/productos/editar/${errorSinc.productoId}">
                                                        <i class="fa-solid fa-pen mr-1"></i>Editar producto
                                                    </a>
                                                </c:when>
                                                <c:otherwise><span class="text-muted small">Sin producto local vinculado</span></c:otherwise>
                                            </c:choose>
                                        </td>
                                    </tr>
                                </c:forEach>
                                </tbody>
                            </table>
                        </div>
                    </div>
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
                                                                        <div class="small text-success mt-2">
                                                                            <i class="fa-solid fa-user-check mr-1"></i>
                                                                            Cuenta: <strong><c:out value="${mercadoLibreCuentaNombre}"/></strong>
                                                                        </div>
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
                                                                        <div class="small text-success mt-2">
                                                                            <i class="fa-solid fa-store mr-1"></i>
                                                                            Tienda: <strong><c:out value="${wooCommerceCuentaNombre}"/></strong>
                                                                        </div>
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
                                                                        <div class="small text-success mt-2">
                                                                            <i class="fa-solid fa-store mr-1"></i>
                                                                            Tienda: <strong><c:out value="${tiendanubeCuentaNombre}"/></strong>
                                                                        </div>
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
                                                <div class="mt-3">
                                                    <form method="post" action="${pageContext.request.contextPath}/canales/importar/${canal}">
                                                        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}">
                                                        <c:if test="${canal == 'MERCADO_LIBRE'}">
                                                            <label class="d-block small mb-2">
                                                                <input type="checkbox" name="incluirInactivas" value="true">
                                                                Incluir publicaciones inactivas
                                                            </label>
                                                        </c:if>
                                                        <button type="submit" class="btn btn-sm btn-outline-primary"
                                                                ${(configuracionImportacion[canal] && !sincronizacionActiva) ? '' : 'disabled'}>
                                                            Traer todo
                                                        </button>
                                                    </form>
                                                </div>
                                                <c:if test="${canal == 'MERCADO_LIBRE'}">
                                                    <div class="border rounded p-3 mt-3 bg-light">
                                                        <div class="font-weight-bold small mb-2">Traer publicaciones recientes</div>
                                                        <form method="post"
                                                              action="${pageContext.request.contextPath}/canales/importar/mercadolibre/ultimas">
                                                            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}">
                                                            <div class="form-row">
                                                                <div class="form-group col-sm-4">
                                                                    <label class="small" for="cantidadMlRecientes">Cantidad</label>
                                                                    <input id="cantidadMlRecientes" name="cantidad" type="number"
                                                                           class="form-control form-control-sm"
                                                                           min="1" max="100" value="5" required>
                                                                </div>
                                                                <div class="form-group col-sm-8">
                                                                    <label class="small" for="categoriaMlRecientes">Categoría (opcional)</label>
                                                                    <input id="categoriaMlRecientes" name="categoria" type="text"
                                                                           class="form-control form-control-sm" maxlength="120"
                                                                           placeholder="Ej.: neumáticos o MLA...">
                                                                </div>
                                                            </div>
                                                            <label class="d-block small mb-2">
                                                                <input type="checkbox" name="incluirInactivas" value="true">
                                                                Incluir publicaciones inactivas
                                                            </label>
                                                            <small class="text-muted d-block mb-2">
                                                                Se toman primero las publicaciones más nuevas. Deje la categoría vacía para traer cualquiera.
                                                            </small>
                                                            <button type="submit" class="btn btn-sm btn-primary"
                                                                    ${(configuracionImportacion[canal] && !sincronizacionActiva) ? '' : 'disabled'}>
                                                                Traer publicaciones
                                                            </button>
                                                        </form>
                                                    </div>
                                                </c:if>
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
                        <c:if test="${sincronizacionActiva}">
                            <div class="alert alert-info">
                                <i class="fa-solid fa-spinner fa-spin mr-1"></i>
                                Hay un trabajo ejecutándose en segundo plano. Esta página se actualizará automáticamente.
                            </div>
                        </c:if>
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
                                    <button class="btn btn-success btn-block" type="submit" ${sincronizacionActiva ? 'disabled' : ''}>
                                        ${sincronizacionActiva ? 'Procesando...' : 'Sincronizar'}
                                    </button>
                                </div>
                            </div>
                        </form>
                        <small class="text-muted">La tarea continúa aunque cierre esta página. Si un SKU ya existe se actualiza; si no existe, se crea.</small>
                    </div>
                </div>

                <div class="card shadow mb-4">
                    <div class="card-header py-3 d-flex align-items-center justify-content-between">
                        <h6 class="m-0 font-weight-bold text-primary">Trabajos en segundo plano</h6>
                        <button type="button" class="btn btn-sm btn-outline-secondary" onclick="window.location.reload()">
                            <i class="fa-solid fa-rotate mr-1"></i>Actualizar
                        </button>
                    </div>
                    <div class="card-body table-responsive">
                        <table class="table table-sm table-bordered">
                            <thead>
                            <tr><th>#</th><th>Flujo</th><th>Estado</th><th>Creado</th><th>Finalizado</th><th>Resultado</th><th>Acciones</th></tr>
                            </thead>
                            <tbody>
                            <c:forEach items="${trabajosSincronizacion}" var="trabajo">
                                <tr>
                                    <td>${trabajo.id}</td>
                                    <td>
                                        <c:out value="${trabajo.flujoDescripcion}"/>
                                    </td>
                                    <td>
                                        <c:choose>
                                            <c:when test="${trabajo.activo and trabajo.cancelacionSolicitada}">
                                                <span class="badge badge-warning">Cancelando...</span>
                                            </c:when>
                                            <c:when test="${trabajo.estado == 'COMPLETADA'}">
                                                <span class="badge badge-success">${trabajo.estado.descripcion}</span>
                                            </c:when>
                                            <c:when test="${trabajo.estado == 'COMPLETADA_CON_ERRORES'}">
                                                <span class="badge badge-warning">${trabajo.estado.descripcion}</span>
                                            </c:when>
                                            <c:when test="${trabajo.estado == 'ERROR'}">
                                                <span class="badge badge-danger">${trabajo.estado.descripcion}</span>
                                            </c:when>
                                            <c:when test="${trabajo.estado == 'CANCELADO'}">
                                                <span class="badge badge-secondary">${trabajo.estado.descripcion}</span>
                                            </c:when>
                                            <c:otherwise>
                                                <span class="badge badge-info">${trabajo.estado.descripcion}</span>
                                            </c:otherwise>
                                        </c:choose>
                                    </td>
                                    <td><c:out value="${trabajo.creadoEnFormateado}"/></td>
                                    <td><c:out value="${trabajo.finalizadoEnFormateado}"/></td>
                                    <td style="min-width:280px">
                                        <c:out value="${trabajo.resumen}"/>
                                        <c:if test="${not empty trabajo.detalle}">
                                            <details class="mt-2">
                                                <summary class="text-warning" style="cursor:pointer">Ver detalle</summary>
                                                <pre class="small mt-2 mb-0 text-wrap"><c:out value="${trabajo.detalle}"/></pre>
                                            </details>
                                        </c:if>
                                    </td>
                                    <td class="text-nowrap">
                                        <c:if test="${trabajo.activo}">
                                            <form method="post"
                                                  action="${pageContext.request.contextPath}/canales/trabajos/${trabajo.id}/cancelar"
                                                  onsubmit="return confirm('Se detendrán los productos restantes. Lo que ya fue enviado no se revierte. ¿Desea continuar?');">
                                                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                                                <button type="submit" class="btn btn-sm btn-outline-danger"
                                                        <c:if test="${trabajo.cancelacionSolicitada}">disabled</c:if>>
                                                    <i class="fa-solid fa-ban mr-1"></i>
                                                    ${trabajo.cancelacionSolicitada ? 'Cancelando...' : 'Cancelar'}
                                                </button>
                                            </form>
                                        </c:if>
                                    </td>
                                </tr>
                            </c:forEach>
                            <c:if test="${empty trabajosSincronizacion}">
                                <tr><td colspan="7" class="text-center text-muted">Todavía no hay trabajos de sincronización.</td></tr>
                            </c:if>
                            </tbody>
                        </table>
                    </div>
                </div>

                <form method="get" action="${pageContext.request.contextPath}/canales#productos-publicacion"
                      class="form-inline mb-3">
                    <input type="hidden" name="productoSize" value="${tamanioPagina}">
                    <input type="search" name="productoQ" class="form-control mr-2"
                           style="width:320px" value="${fn:escapeXml(busquedaProductos)}"
                           placeholder="Buscar por SKU o producto...">
                    <button class="btn btn-outline-primary" type="submit">Buscar productos</button>
                    <c:if test="${not empty busquedaProductos}">
                        <a class="btn btn-link" href="${pageContext.request.contextPath}/canales#productos-publicacion">Limpiar</a>
                    </c:if>
                </form>

                <form method="post" action="${pageContext.request.contextPath}/canales/publicar">
                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}">
                    <input type="hidden" name="productoQ" value="${fn:escapeXml(busquedaProductos)}">
                    <input type="hidden" id="seleccionarTodosResultadosInput"
                           name="seleccionarTodosResultados"
                           value="${seleccionarTodosResultados ? 'true' : 'false'}">
                    <div class="card shadow mb-4" id="productos-publicacion">
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
                            <div class="d-flex flex-wrap align-items-center mb-3">
                                <button type="button" class="btn btn-sm btn-outline-secondary mr-2 mb-1"
                                        id="seleccionarTodos">Seleccionar esta p&aacute;gina</button>
                                <c:if test="${paginaProductos.totalElements > paginaProductos.numberOfElements}">
                                    <button type="button" class="btn btn-sm btn-outline-primary mr-2 mb-1"
                                            id="seleccionarTodosResultados">
                                        <c:choose>
                                            <c:when test="${seleccionarTodosResultados}">Cancelar selección de todas las páginas</c:when>
                                            <c:otherwise>Seleccionar los ${paginaProductos.totalElements} resultados</c:otherwise>
                                        </c:choose>
                                    </button>
                                </c:if>
                                <small class="text-muted ml-auto align-self-center">
                                    ${paginaProductos.totalElements} producto(s) encontrados
                                </small>
                            </div>
                            <div id="avisoSeleccionTotal"
                                 class="alert alert-info py-2 ${seleccionarTodosResultados ? '' : 'd-none'}">
                                Se seleccionaron los <strong>${paginaProductos.totalElements} productos</strong>
                                de todas las páginas<c:if test="${not empty busquedaProductos}"> que coinciden con la búsqueda</c:if>.
                            </div>
                            <div class="table-responsive" style="max-height:460px;overflow:auto">
                                <table class="table table-bordered table-hover" id="tablaProductos">
                                    <thead class="thead-dark"><tr><th></th><th>Foto</th><th>SKU</th><th>Producto</th><th>Stock</th><th>Precio</th></tr></thead>
                                    <tbody>
                                    <c:forEach items="${productos}" var="p">
                                        <tr>
                                            <td><input type="checkbox" class="producto-check" name="productoIds"
                                                       value="${p.id}" ${seleccionarTodosResultados ? 'checked' : ''}></td>
                                            <td><c:if test="${p.tieneFoto}"><img src="${pageContext.request.contextPath}/productos/${p.id}/foto" loading="lazy" decoding="async" alt="" style="width:44px;height:44px;object-fit:contain;background:#fff;border-radius:5px"></c:if></td>
                                            <td><c:out value="${p.sku}"/></td><td><c:out value="${p.descripcion}"/></td><td>${p.stockTotal}</td><td>${p.precioContadoListado}</td>
                                        </tr>
                                    </c:forEach>
                                    </tbody>
                                </table>
                            </div>
                            <div class="d-flex flex-wrap justify-content-between align-items-center mt-3">
                                <small class="text-muted">
                                    P&aacute;gina ${paginaProductos.number + 1} de
                                    ${paginaProductos.totalPages == 0 ? 1 : paginaProductos.totalPages}
                                </small>
                                <c:if test="${paginaProductos.totalPages > 1}">
                                    <c:set var="paginaInicio" value="${paginaProductos.number > 2 ? paginaProductos.number - 2 : 0}"/>
                                    <c:set var="paginaFin" value="${paginaProductos.number + 2 < paginaProductos.totalPages - 1 ? paginaProductos.number + 2 : paginaProductos.totalPages - 1}"/>
                                    <nav aria-label="Paginaci&oacute;n para publicar productos">
                                        <ul class="pagination pagination-sm mb-0">
                                            <c:url var="urlAnteriorProductos" value="/canales">
                                                <c:param name="productoPage" value="${paginaProductos.number - 1}"/>
                                                <c:param name="productoSize" value="${tamanioPagina}"/>
                                                <c:param name="productoQ" value="${busquedaProductos}"/>
                                                <c:param name="seleccionarTodosResultados" value="${seleccionarTodosResultados}"/>
                                            </c:url>
                                            <li class="page-item ${paginaProductos.first ? 'disabled' : ''}">
                                                <a class="page-link pagina-productos-link" href="${paginaProductos.first ? '#' : urlAnteriorProductos}#productos-publicacion">Anterior</a>
                                            </li>
                                            <c:forEach begin="${paginaInicio}" end="${paginaFin}" var="numeroPagina">
                                                <c:url var="urlPaginaProductos" value="/canales">
                                                    <c:param name="productoPage" value="${numeroPagina}"/>
                                                    <c:param name="productoSize" value="${tamanioPagina}"/>
                                                    <c:param name="productoQ" value="${busquedaProductos}"/>
                                                    <c:param name="seleccionarTodosResultados" value="${seleccionarTodosResultados}"/>
                                                </c:url>
                                                <li class="page-item ${numeroPagina == paginaProductos.number ? 'active' : ''}">
                                                    <a class="page-link pagina-productos-link" href="${urlPaginaProductos}#productos-publicacion">${numeroPagina + 1}</a>
                                                </li>
                                            </c:forEach>
                                            <c:url var="urlSiguienteProductos" value="/canales">
                                                <c:param name="productoPage" value="${paginaProductos.number + 1}"/>
                                                <c:param name="productoSize" value="${tamanioPagina}"/>
                                                <c:param name="productoQ" value="${busquedaProductos}"/>
                                                <c:param name="seleccionarTodosResultados" value="${seleccionarTodosResultados}"/>
                                            </c:url>
                                            <li class="page-item ${paginaProductos.last ? 'disabled' : ''}">
                                                <a class="page-link pagina-productos-link" href="${paginaProductos.last ? '#' : urlSiguienteProductos}#productos-publicacion">Siguiente</a>
                                            </li>
                                        </ul>
                                    </nav>
                                </c:if>
                            </div>
                            <button type="submit" class="btn btn-success mt-3" ${sincronizacionActiva ? 'disabled' : ''}>
                                <i class="fa-solid ${sincronizacionActiva ? 'fa-spinner fa-spin' : 'fa-cloud-arrow-up'} mr-1"></i>
                                ${sincronizacionActiva ? 'Hay un trabajo en proceso' : 'Publicar seleccionados'}
                            </button>
                            <small class="text-muted d-block mt-2">La publicación continúa en segundo plano aunque cierre esta página. El resultado aparecerá en la tabla de trabajos.</small>
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
                                <tr><td><c:out value="${pub.productoDescripcion}"/></td><td>${pub.canal.descripcion}</td>
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
const checksProductos = Array.from(document.querySelectorAll('.producto-check'));
const seleccionTotalInput = document.getElementById('seleccionarTodosResultadosInput');
const botonSeleccionTotal = document.getElementById('seleccionarTodosResultados');
const avisoSeleccionTotal = document.getElementById('avisoSeleccionTotal');

function cancelarSeleccionTotal() {
    seleccionTotalInput.value = 'false';
    avisoSeleccionTotal.classList.add('d-none');
    if (botonSeleccionTotal) {
        botonSeleccionTotal.textContent = 'Seleccionar los ${paginaProductos.totalElements} resultados';
    }
}

document.getElementById('seleccionarTodos').addEventListener('click', function () {
    const marcar = checksProductos.some(check => !check.checked);
    cancelarSeleccionTotal();
    checksProductos.forEach(check => check.checked = marcar);
});
checksProductos.forEach(check => check.addEventListener('change', function () {
    if (seleccionTotalInput.value === 'true' && !this.checked) cancelarSeleccionTotal();
}));
if (botonSeleccionTotal) {
    botonSeleccionTotal.addEventListener('click', function () {
        const activar = seleccionTotalInput.value !== 'true';
        seleccionTotalInput.value = activar ? 'true' : 'false';
        checksProductos.forEach(check => check.checked = activar);
        avisoSeleccionTotal.classList.toggle('d-none', !activar);
        this.textContent = activar
            ? 'Cancelar selección de todas las páginas'
            : 'Seleccionar los ${paginaProductos.totalElements} resultados';
    });
}
document.querySelectorAll('.pagina-productos-link').forEach(link => {
    link.addEventListener('click', function () {
        if (seleccionTotalInput.value !== 'true' || this.getAttribute('href') === '#') return;
        const destino = new URL(this.href);
        destino.searchParams.set('seleccionarTodosResultados', 'true');
        this.href = destino.toString();
    });
});
const filtroErrores = document.getElementById('filtroErroresSincronizacion');
if (filtroErrores) {
    filtroErrores.addEventListener('input', function () {
        const consulta = this.value.trim().toLocaleLowerCase('es');
        document.querySelectorAll('.fila-error-sincronizacion').forEach(fila => {
            fila.style.display = fila.textContent.toLocaleLowerCase('es').includes(consulta) ? '' : 'none';
        });
    });
}
<c:if test="${sincronizacionActiva}">
window.setTimeout(function () { window.location.reload(); }, 8000);
</c:if>
</script>
</body>
</html>

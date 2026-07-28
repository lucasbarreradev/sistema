<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html><html lang="es"><head><jsp:include page="/WEB-INF/jsp/head.jsp"/></head>
<body id="page-top"><div id="wrapper"><jsp:include page="/WEB-INF/jsp/nav_bar.jsp"/>
<div id="content-wrapper" class="d-flex flex-column"><div class="container-fluid py-4">
    <div class="d-flex justify-content-between align-items-center mb-4">
        <div><h1 class="h3 text-gray-800 mb-1">Presentaciones y variantes</h1><div class="text-muted"><c:out value="${producto.descripcion}"/></div></div>
        <a class="btn btn-secondary" href="${pageContext.request.contextPath}/productos">Volver</a>
    </div>
    <c:if test="${not empty mensaje}"><div class="alert alert-success">${mensaje}</div></c:if>
    <c:if test="${not empty error}"><div class="alert alert-danger">${error}</div></c:if>
    <c:if test="${not empty advertenciaAtributos}"><div class="alert alert-warning"><c:out value="${advertenciaAtributos}"/></div></c:if>
    <div class="alert alert-info">Cargá aquí stock, precios, foto, GTIN y características de la categoría. Con una sola presentación el producto se publica como simple; con dos o más, como producto con variantes.</div>
    <div class="card shadow mb-4"><div class="card-header"><strong>${empty variante.id ? 'Agregar presentación' : 'Editar presentación'}</strong></div><div class="card-body">
        <form method="post" enctype="multipart/form-data" class="row" action="${pageContext.request.contextPath}/productos/${producto.id}/variantes">
            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
            <input type="hidden" name="id" value="${variante.id}">
            <c:choose>
                <c:when test="${not empty atributosVariante}">
                    <c:forEach items="${atributosVariante}" var="atributo">
                        <div class="col-md-3 mb-3">
                            <label><c:out value="${atributo.nombre}"/></label>
                            <c:choose>
                                <c:when test="${atributo.tipo == 'number_unit'}">
                                    <div class="input-group">
                                        <input name="atributo_${atributo.id}" value="${valoresAtributos[atributo.id]}"
                                               class="form-control" inputmode="decimal" <c:if test="${atributo.obligatorio}">required</c:if>>
                                        <div class="input-group-append">
                                            <select name="unidad_${atributo.id}" class="form-control">
                                                <c:forEach items="${atributo.unidades}" var="unidad">
                                                    <option value="${unidad}" <c:if test="${unidadesAtributos[atributo.id] == unidad}">selected</c:if>><c:out value="${unidad}"/></option>
                                                </c:forEach>
                                            </select>
                                        </div>
                                    </div>
                                </c:when>
                                <c:when test="${not empty atributo.valores}">
                                    <select name="atributo_${atributo.id}" class="form-control" <c:if test="${atributo.obligatorio}">required</c:if>>
                                        <option value="">Seleccionar</option>
                                        <c:forEach items="${atributo.valores}" var="opcion">
                                            <option value="${opcion}" <c:if test="${valoresAtributos[atributo.id] == opcion}">selected</c:if>><c:out value="${opcion}"/></option>
                                        </c:forEach>
                                    </select>
                                </c:when>
                                <c:otherwise>
                                    <input name="atributo_${atributo.id}" value="${valoresAtributos[atributo.id]}"
                                           class="form-control" <c:if test="${atributo.obligatorio}">required</c:if>>
                                </c:otherwise>
                            </c:choose>
                            <c:if test="${atributo.id == 'EMPTY_GTIN_REASON'}"><small class="form-text text-muted">Elegir solamente si esta variante no tiene GTIN.</small></c:if>
                        </div>
                    </c:forEach>
                </c:when>
                <c:otherwise>
                    <div class="col-md-3 mb-3"><label>Talle</label><input name="talle" value="${variante.talle}" class="form-control" placeholder="M, 42..."></div>
                    <div class="col-md-3 mb-3"><label>Color</label><input name="color" value="${variante.color}" class="form-control"></div>
                </c:otherwise>
            </c:choose>
            <div class="col-md-3 mb-3">
                <label>GTIN / código universal</label>
                <input name="mercadoLibreGtin" value="${variante.mercadoLibreGtin}" class="form-control" placeholder="EAN, UPC o ISBN">
                <small class="form-text text-muted">Si no posee GTIN, elegí el motivo entre los campos de la categoría.</small>
            </div>
            <div class="col-md-3 mb-3">
                <label>SKU</label>
                <input name="sku" value="${variante.sku}" class="form-control">
                <small class="form-text text-muted">Al dejar vacío se genera automáticamente.</small>
            </div>
            <div class="col-md-4 mb-3">
                <label>Foto de la presentación</label>
                <input type="file" name="foto" class="form-control-file" accept="image/jpeg,image/png,image/webp,image/gif">
                <small class="form-text text-muted">JPG, PNG, WebP o GIF. Máximo 5 MB.</small>
                <small class="form-text text-warning">Si cambia el color, diseño o piedra, la foto debe mostrar esa variante. Una foto general puede compartirse cuando solo cambia el talle.</small>
                <c:if test="${variante.tieneFoto}">
                    <img class="img-thumbnail mt-2" style="max-width:140px;max-height:140px" alt="Foto de la variante"
                         src="${pageContext.request.contextPath}/productos/${producto.id}/variantes/${variante.id}/foto">
                    <div class="small text-muted">La foto actual se conserva si no seleccionás otra.</div>
                </c:if>
            </div>
            <div class="col-md-2 mb-3"><label>Stock</label><input type="number" min="0" name="stock" value="${empty variante.stock ? 0 : variante.stock}" class="form-control" required></div>
            <div class="col-md-2 mb-3"><label>Precio compra</label><input name="precioCompra" value="${variante.precioCompra}" class="form-control" oninput="this.value = this.value.replace(',', '.')" pattern="^\d+(\.\d{0,2})?$" <c:if test="${empty producto.precioCompra}">required</c:if>></div>
            <div class="col-md-2 mb-3"><label>Precio contado</label><input name="precioContado" value="${variante.precioContado}" class="form-control" oninput="this.value = this.value.replace(',', '.')" pattern="^\d+(\.\d{0,2})?$" <c:if test="${empty producto.precioContado}">required</c:if>></div>
            <div class="col-md-2 mb-3"><label>Precio tarjeta</label><input name="precioTarjeta" value="${variante.precioTarjeta}" class="form-control" oninput="this.value = this.value.replace(',', '.')" pattern="^\d+(\.\d{0,2})?$" <c:if test="${empty producto.precioTarjeta}">required</c:if>></div>
            <div class="col-md-2 mb-3"><label>Precio C/C</label><input name="precioCuentaCorriente" value="${variante.precioCuentaCorriente}" class="form-control" oninput="this.value = this.value.replace(',', '.')" pattern="^\d+(\.\d{0,2})?$" <c:if test="${empty producto.precioCuentaCorriente}">required</c:if>></div>
            <div class="col-md-1 mb-3 d-flex align-items-end"><button class="btn btn-success">${empty variante.id ? 'Agregar' : 'Guardar'}</button></div>
        </form>
        <small class="text-muted">Completá el stock y los precios de cada presentación.</small>
    </div></div>
    <div class="card shadow"><div class="card-body table-responsive"><table class="table table-bordered">
        <thead><tr><th>Foto</th><th>Presentación</th><th>SKU</th><th>Código</th><th>Stock</th><th>Contado</th><th></th></tr></thead><tbody>
        <c:forEach items="${variantes}" var="v"><tr>
            <td><c:if test="${v.tieneFoto}"><img class="img-thumbnail" style="width:64px;height:64px;object-fit:contain;background:#fff" alt="Foto" src="${pageContext.request.contextPath}/productos/${producto.id}/variantes/${v.id}/foto"></c:if><c:if test="${not v.tieneFoto}"><span class="text-muted">General</span></c:if></td>
            <td><c:out value="${v.nombreMostrar}"/></td><td><c:out value="${v.sku}"/></td><td><c:out value="${v.codigoBarras}"/></td>
            <td>${v.stock}</td><td>${empty v.precioContado ? producto.precioContado : v.precioContado}</td>
            <td><a class="btn btn-sm btn-warning" href="${pageContext.request.contextPath}/productos/${producto.id}/variantes/${v.id}/editar">Editar</a>
            <form class="d-inline" method="post" action="${pageContext.request.contextPath}/productos/${producto.id}/variantes/${v.id}/eliminar">
                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/><button class="btn btn-sm btn-danger">Eliminar</button>
            </form></td></tr></c:forEach>
        <c:if test="${empty variantes}"><tr><td colspan="7" class="text-center text-muted">Todavía no hay presentaciones. Agregá al menos una para completar el producto.</td></tr></c:if>
        </tbody></table></div></div>
</div></div></div></body></html>

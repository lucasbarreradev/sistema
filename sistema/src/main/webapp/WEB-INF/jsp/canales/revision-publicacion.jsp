<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<!DOCTYPE html>
<html lang="es">
<head><jsp:include page="/WEB-INF/jsp/head.jsp"/></head>
<body id="page-top">
<div id="wrapper">
    <jsp:include page="/WEB-INF/jsp/nav_bar.jsp"/>
    <div id="content-wrapper" class="d-flex flex-column">
        <div class="container-fluid py-4">
            <div class="d-flex flex-wrap justify-content-between align-items-center mb-3">
                <div>
                    <h1 class="h3 text-gray-800 mb-1">Revisar antes de publicar</h1>
                    <div class="text-muted">En esta pantalla todavía no se publica nada.</div>
                </div>
                <div class="mt-2 mt-md-0">
                    <c:forEach items="${revisionCanales}" var="canal">
                        <span class="badge badge-primary p-2 mr-1">${canal.descripcion}</span>
                    </c:forEach>
                </div>
            </div>

            <c:if test="${not empty mensaje}"><div class="alert alert-success"><c:out value="${mensaje}"/></div></c:if>
            <c:if test="${not empty error}"><div class="alert alert-danger"><c:out value="${error}"/></div></c:if>

            <div class="row mb-3">
                <div class="col-md-6 mb-2">
                    <div class="card border-left-success shadow-sm"><div class="card-body py-3">
                        <strong class="text-success">${cantidadListos} listos para publicar</strong>
                    </div></div>
                </div>
                <div class="col-md-6 mb-2">
                    <div class="card border-left-warning shadow-sm"><div class="card-body py-3">
                        <strong class="text-warning">${cantidadPendientes} necesitan correcciones</strong>
                    </div></div>
                </div>
            </div>

            <div class="card shadow mb-4">
                <div class="card-body table-responsive p-0">
                    <table class="table table-bordered mb-0">
                        <thead class="thead-dark">
                        <tr><th style="width:115px">Estado</th><th>Artículo</th><th>Resultado de la revisión</th><th style="width:190px">Acciones</th></tr>
                        </thead>
                        <tbody>
                        <c:forEach items="${revisionProductos}" var="revision">
                            <c:set var="p" value="${revision.producto}"/>
                            <tr id="producto-${p.id}" class="${revision.listo ? 'table-success' : 'table-warning'}">
                                <td>
                                    <span class="badge ${revision.listo ? 'badge-success' : 'badge-warning'} p-2">
                                        ${revision.listo ? 'LISTO' : 'PENDIENTE'}
                                    </span>
                                </td>
                                <td>
                                    <strong><c:out value="${p.descripcion}"/></strong>
                                    <div class="small text-muted"><c:out value="${p.sku}"/></div>
                                </td>
                                <td>
                                    <c:choose>
                                        <c:when test="${revision.listo}">
                                            No se detectaron datos faltantes.
                                        </c:when>
                                        <c:otherwise>
                                            <c:forEach items="${revision.faltantes}" var="faltante">
                                                <span class="badge badge-warning mr-1 mb-1"><c:out value="${faltante}"/></span>
                                            </c:forEach>
                                        </c:otherwise>
                                    </c:choose>
                                </td>
                                <td class="text-nowrap">
                                    <button type="button" class="btn btn-sm btn-primary"
                                            data-toggle="collapse" data-target="#editar-${p.id}">
                                        <i class="fa-solid fa-pen mr-1"></i>Editar aquí
                                    </button>
                                    <c:url var="urlVariantes" value="/productos/${p.id}/variantes">
                                        <c:param name="volver" value="/canales/publicar/revision"/>
                                    </c:url>
                                    <c:if test="${not empty revision.atributosFaltantes}">
                                        <a class="btn btn-sm btn-warning mt-1" href="${urlVariantes}">
                                            Completar atributos
                                        </a>
                                    </c:if>
                                </td>
                            </tr>
                            <tr class="collapse ${revision.listo ? '' : 'show'}" id="editar-${p.id}">
                                <td colspan="4" class="bg-light p-3">
                                    <form method="post" action="${pageContext.request.contextPath}/canales/publicar/revision/guardar">
                                        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}">
                                        <input type="hidden" name="productoId" value="${p.id}">
                                        <div class="row">
                                            <div class="col-lg-6 mb-3">
                                                <label>Título</label>
                                                <input name="titulo" class="form-control" maxlength="255"
                                                       value="${fn:escapeXml(p.descripcion)}" required>
                                            </div>
                                            <div class="col-lg-3 mb-3">
                                                <label>Categoría de Mercado Libre</label>
                                                <input name="categoriaMercadoLibre" class="form-control"
                                                       value="${fn:escapeXml(p.mercadoLibreCategoriaId)}"
                                                       placeholder="Ej.: MLA417282">
                                            </div>
                                            <div class="col-lg-3 mb-3">
                                                <label>Tipo de publicación</label>
                                                <select name="tipoPublicacion" class="form-control">
                                                    <option value="">Configuración general</option>
                                                    <option value="gold_special" <c:if test="${p.mercadoLibreListingTypeId == 'gold_special'}">selected</c:if>>Clásica</option>
                                                    <option value="gold_pro" <c:if test="${p.mercadoLibreListingTypeId == 'gold_pro'}">selected</c:if>>Premium</option>
                                                </select>
                                            </div>
                                            <div class="col-lg-6 mb-3">
                                                <label>Descripción para Mercado Libre</label>
                                                <textarea name="descripcionMercadoLibre" class="form-control" rows="4"><c:out value="${p.mercadoLibreDescripcion}"/></textarea>
                                            </div>
                                            <div class="col-lg-3 mb-3">
                                                <label>Marca<c:if test="${revision.marcaObligatoria}"> <span class="text-danger font-weight-bold" title="Obligatorio para Mercado Libre">*</span></c:if></label>
                                                <input name="marca" class="form-control" value="${fn:escapeXml(p.mercadoLibreMarca)}">
                                            </div>
                                            <div class="col-lg-3 mb-3">
                                                <label>Modelo<c:if test="${revision.modeloObligatorio}"> <span class="text-danger font-weight-bold" title="Obligatorio para Mercado Libre">*</span></c:if></label>
                                                <input name="modelo" class="form-control" value="${fn:escapeXml(p.mercadoLibreModelo)}">
                                            </div>
                                            <div class="col-md-4 mb-3">
                                                <label>Modalidad de envío</label>
                                                <select name="modoEnvio" class="form-control">
                                                    <option value="">Automática</option>
                                                    <option value="me2" <c:if test="${p.mercadoLibreModoEnvio == 'me2'}">selected</c:if>>Mercado Envíos</option>
                                                    <option value="not_specified" <c:if test="${p.mercadoLibreModoEnvio == 'not_specified'}">selected</c:if>>Acordar con el vendedor</option>
                                                </select>
                                            </div>
                                            <div class="col-md-4 mb-3 d-flex align-items-end">
                                                <div class="form-check mb-2">
                                                    <input type="checkbox" class="form-check-input" id="gratis-${p.id}"
                                                           name="envioGratis" value="true" <c:if test="${p.mercadoLibreEnvioGratis}">checked</c:if>>
                                                    <label class="form-check-label" for="gratis-${p.id}">Envío gratis</label>
                                                </div>
                                            </div>
                                            <div class="col-md-4 mb-3 d-flex align-items-end">
                                                <div class="form-check mb-2">
                                                    <input type="checkbox" class="form-check-input" id="retiro-${p.id}"
                                                           name="retiroPersonal" value="true" <c:if test="${p.mercadoLibreRetiroPersonal}">checked</c:if>>
                                                    <label class="form-check-label" for="retiro-${p.id}">Retiro en persona</label>
                                                </div>
                                            </div>
                                        </div>

                                        <div class="border rounded bg-white p-3 mb-3">
                                            <h6 class="font-weight-bold">Stock y precio</h6>
                                            <c:choose>
                                                <c:when test="${empty revision.variantes}">
                                                    <div class="form-row">
                                                        <div class="col-md-3 mb-2"><label>Stock</label><input type="number" min="0" name="stock" class="form-control" value="${p.cantidad}" required></div>
                                                        <div class="col-md-3 mb-2"><label>Precio</label><input name="precio" class="form-control precio-decimal" value="${p.precioContado}" required></div>
                                                    </div>
                                                </c:when>
                                                <c:otherwise>
                                                    <div class="table-responsive">
                                                        <table class="table table-sm table-bordered mb-0">
                                                            <thead><tr><th>Variante</th><th>SKU</th><th>Stock</th><th>Precio</th></tr></thead>
                                                            <tbody>
                                                            <c:forEach items="${revision.variantes}" var="v">
                                                                <tr>
                                                                    <td><c:out value="${v.nombreMostrar}"/></td>
                                                                    <td><c:out value="${v.sku}"/></td>
                                                                    <td><input type="hidden" name="varianteIds" value="${v.id}"><input type="number" min="0" name="stocksVariantes" class="form-control form-control-sm" value="${v.stock}" required></td>
                                                                    <td><input name="preciosVariantes" class="form-control form-control-sm precio-decimal" value="${v.precioContado}" placeholder="Usar precio general"></td>
                                                                </tr>
                                                            </c:forEach>
                                                            </tbody>
                                                        </table>
                                                    </div>
                                                    <div class="form-row mt-2">
                                                        <div class="col-md-3"><label>Precio general de respaldo</label><input name="precio" class="form-control precio-decimal" value="${p.precioContado}"></div>
                                                    </div>
                                                </c:otherwise>
                                            </c:choose>
                                        </div>

                                        <button class="btn btn-success" type="submit"><i class="fa-solid fa-floppy-disk mr-1"></i>Guardar y volver a validar</button>
                                        <a class="btn btn-outline-secondary" href="${urlVariantes}">Editar atributos y fotos</a>
                                    </form>
                                </td>
                            </tr>
                        </c:forEach>
                        </tbody>
                    </table>
                </div>
            </div>

            <div class="d-flex flex-wrap justify-content-between mb-4">
                <form method="post" action="${pageContext.request.contextPath}/canales/publicar/revision/cancelar">
                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}">
                    <button class="btn btn-secondary" type="submit">Volver a seleccionar</button>
                </form>
                <form method="post" action="${pageContext.request.contextPath}/canales/publicar/revision/confirmar">
                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}">
                    <button class="btn btn-success btn-lg" type="submit" <c:if test="${not todosListos}">disabled</c:if>>
                        <i class="fa-solid fa-cloud-arrow-up mr-1"></i>Publicar todos
                    </button>
                    <c:if test="${not todosListos}"><div class="small text-warning mt-1">Complete los pendientes para habilitar la publicación.</div></c:if>
                </form>
            </div>
        </div>
    </div>
</div>
<script>
document.querySelectorAll('.precio-decimal').forEach(function (campo) {
    campo.addEventListener('input', function () { this.value = this.value.replace(',', '.'); });
});
</script>
</body>
</html>

<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html><html lang="es"><head><jsp:include page="/WEB-INF/jsp/head.jsp"/></head>
<body id="page-top"><div id="wrapper"><jsp:include page="/WEB-INF/jsp/nav_bar.jsp"/>
<div id="content-wrapper" class="d-flex flex-column"><div class="container-fluid py-4">
    <h1 class="h3 text-gray-800">Guías de talles</h1>
    <p class="text-muted">Construí o buscá una guía compatible con la categoría del producto en Mercado Libre.</p>
    <c:if test="${not empty mensaje}"><div class="alert alert-success"><c:out value="${mensaje}"/></div></c:if>
    <c:if test="${not empty error}"><div class="alert alert-danger"><c:out value="${error}"/></div></c:if>

    <div class="card shadow mb-4"><div class="card-body">
        <form method="get" action="${pageContext.request.contextPath}/guias-talles" class="row align-items-end">
            <div class="col-md-8 mb-3"><label>Producto</label>
                <select name="productoId" class="form-control" required>
                    <option value="">Seleccionar producto</option>
                    <c:forEach items="${productos}" var="p">
                        <option value="${p.id}" <c:if test="${not empty contexto && contexto.producto.id == p.id}">selected</c:if>>
                            <c:out value="${p.descripcion}"/> — <c:out value="${p.sku}"/>
                        </option>
                    </c:forEach>
                </select>
            </div>
            <div class="col-md-4 mb-3"><button class="btn btn-primary">Continuar</button></div>
        </form>
    </div></div>

    <c:if test="${not empty contexto}">
        <div class="card shadow mb-4"><div class="card-header"><strong>Producto seleccionado</strong></div><div class="card-body">
            <div class="row mb-3">
                <div class="col-md-4"><strong>Categoría</strong><br><c:out value="${contexto.categoriaNombre}"/> (<c:out value="${contexto.producto.mercadoLibreCategoriaId}"/>)</div>
                <div class="col-md-4"><strong>Dominio</strong><br><c:out value="${contexto.dominioCompleto}"/></div>
                <div class="col-md-4"><strong>Guía asignada</strong><br>
                    <c:choose><c:when test="${not empty contexto.producto.mercadoLibreGuiaTallesId}">
                        <a href="${pageContext.request.contextPath}/guias-talles/${contexto.producto.mercadoLibreGuiaTallesId}?productoId=${contexto.producto.id}"><c:out value="${contexto.producto.mercadoLibreGuiaTallesId}"/></a>
                    </c:when><c:otherwise>Ninguna</c:otherwise></c:choose>
                </div>
            </div>
            <form method="post" class="row">
                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                <input type="hidden" name="productoId" value="${contexto.producto.id}"/>
                <c:forEach items="${contexto.filtros}" var="campo">
                    <div class="col-md-4 mb-3"><label><c:out value="${campo.nombre}"/> *</label>
                        <c:choose><c:when test="${not empty campo.valores}">
                            <select name="filtro_${campo.id}" class="form-control" required><option value="">Seleccionar</option>
                                <c:forEach items="${campo.valores}" var="opcion">
                                    <option value="${opcion.id}|||${opcion.nombre}"
                                        <c:if test="${(campo.id == 'GENDER' && contexto.producto.mercadoLibreGenero == opcion.nombre) || (campo.id == 'BRAND' && contexto.producto.mercadoLibreMarca == opcion.nombre)}">selected</c:if>><c:out value="${opcion.nombre}"/></option>
                                </c:forEach>
                            </select>
                        </c:when><c:otherwise>
                            <input name="filtro_${campo.id}" class="form-control" required
                                   value="${campo.id == 'BRAND' ? contexto.producto.mercadoLibreMarca : (campo.id == 'GENDER' ? contexto.producto.mercadoLibreGenero : '')}">
                        </c:otherwise></c:choose>
                    </div>
                </c:forEach>
                <div class="col-md-5 mb-3"><label>Nombre de la nueva guía</label><input name="nombre" class="form-control" maxlength="60" placeholder="Guia remeras hombre"></div>
                <div class="col-md-4 mb-3"><label>Tipo de medidas</label><select name="tipoMedida" class="form-control">
                    <option value="BODY_MEASURE">Medidas corporales</option><option value="CLOTHING_MEASURE">Medidas de la prenda</option>
                </select></div>
                <div class="col-12">
                    <button formaction="${pageContext.request.contextPath}/guias-talles/buscar" class="btn btn-outline-primary">Buscar guías disponibles</button>
                    <button formaction="${pageContext.request.contextPath}/guias-talles/preparar" class="btn btn-success">Construir nueva guía</button>
                </div>
            </form>
        </div></div>
    </c:if>

    <c:if test="${not empty guias}"><div class="card shadow"><div class="card-header"><strong>Guías compatibles</strong></div><div class="card-body table-responsive">
        <table class="table table-bordered"><thead><tr><th>Nombre</th><th>Tipo</th><th>Atributo principal</th><th>Filas</th><th></th></tr></thead><tbody>
        <c:forEach items="${guias}" var="g"><tr><td><c:out value="${g.nombre}"/></td><td><c:out value="${g.tipo}"/></td><td><c:out value="${g.atributoPrincipal}"/></td><td>${g.cantidadFilas}</td>
            <td><a class="btn btn-sm btn-primary" href="${pageContext.request.contextPath}/guias-talles/${g.id}?productoId=${contexto.producto.id}">Ver y asignar</a></td></tr></c:forEach>
        </tbody></table>
    </div></div></c:if>
</div></div></div></body></html>

<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html><html lang="es"><head><jsp:include page="/WEB-INF/jsp/head.jsp"/></head>
<body id="page-top"><div id="wrapper"><jsp:include page="/WEB-INF/jsp/nav_bar.jsp"/>
<div id="content-wrapper" class="d-flex flex-column"><div class="container-fluid py-4">
    <div class="d-flex justify-content-between"><h1 class="h3 text-gray-800">Detalle de guía</h1>
        <c:choose><c:when test="${not empty producto}"><a class="btn btn-secondary" href="${pageContext.request.contextPath}/guias-talles?productoId=${producto.id}">Volver</a></c:when>
            <c:otherwise><a class="btn btn-secondary" href="${pageContext.request.contextPath}/guias-talles">Volver</a></c:otherwise></c:choose>
    </div>
    <c:if test="${not empty mensaje}"><div class="alert alert-success"><c:out value="${mensaje}"/></div></c:if>
    <c:if test="${not empty error}"><div class="alert alert-danger"><c:out value="${error}"/></div></c:if>
    <c:if test="${not empty guia}"><div class="card shadow mb-4"><div class="card-body">
        <h4><c:out value="${guia.nombre}"/></h4><p>ID <strong><c:out value="${guia.id}"/></strong> · <c:out value="${guia.tipo}"/> · <c:out value="${guia.dominio}"/></p>
        <c:forEach items="${guia.atributos}" var="a"><span class="badge badge-secondary mr-2"><c:out value="${a.key}"/>: <c:out value="${a.value}"/></span></c:forEach>
    </div></div>
    <div class="card shadow mb-4"><div class="card-header"><strong>Talles y medidas</strong></div><div class="card-body table-responsive">
        <table class="table table-bordered"><thead><tr><th>Fila</th><th>Datos</th></tr></thead><tbody>
            <c:forEach items="${guia.filas}" var="fila"><tr><td><c:out value="${fila.id}"/></td><td>
                <c:forEach items="${fila.atributos}" var="a"><div><strong><c:out value="${a.key}"/>:</strong> <c:out value="${a.value}"/></div></c:forEach>
            </td></tr></c:forEach>
        </tbody></table>
    </div></div></c:if>
    <c:if test="${not empty guia && not empty producto}"><div class="card shadow"><div class="card-header"><strong>Asignar a <c:out value="${producto.descripcion}"/></strong></div><div class="card-body">
        <form method="post" action="${pageContext.request.contextPath}/guias-talles/asignar" class="row align-items-end">
            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/><input type="hidden" name="productoId" value="${producto.id}"/><input type="hidden" name="chartId" value="${guia.id}"/>
            <c:if test="${not producto.usaVariantes}"><div class="col-md-8 mb-3"><label>Fila correspondiente al producto *</label><select name="rowId" class="form-control" required><option value="">Seleccionar</option>
                <c:forEach items="${guia.filas}" var="fila"><option value="${fila.id}"><c:forEach items="${fila.atributos}" var="a"><c:out value="${a.key}"/>: <c:out value="${a.value}"/> · </c:forEach></option></c:forEach>
            </select></div></c:if>
            <div class="col-md-4 mb-3"><button class="btn btn-success">Asignar guía</button></div>
        </form>
    </div></div></c:if>
</div></div></div></body></html>

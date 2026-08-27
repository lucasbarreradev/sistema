<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html lang="es">
<head><jsp:include page="/WEB-INF/jsp/head.jsp"/></head>
<body id="page-top">
<div id="wrapper">
    <jsp:include page="/WEB-INF/jsp/nav_bar.jsp"/>
    <div id="content-wrapper" class="d-flex flex-column">
        <div class="container-fluid py-4">
            <div class="d-flex justify-content-between align-items-center mb-4">
                <div>
                    <h1 class="h3 text-gray-800 mb-1">Negocios</h1>
                    <p class="text-muted mb-0">Cada negocio mantiene sus productos, ventas y conexiones completamente separados.</p>
                </div>
                <a class="btn btn-primary" href="${pageContext.request.contextPath}/tenants/nuevo">
                    <i class="fa-solid fa-plus mr-1"></i> Nuevo negocio
                </a>
            </div>
            <c:if test="${not empty mensaje}"><div class="alert alert-success"><c:out value="${mensaje}"/></div></c:if>
            <c:if test="${not empty error}"><div class="alert alert-danger"><c:out value="${error}"/></div></c:if>
            <div class="card shadow">
                <div class="card-body table-responsive">
                    <table class="table table-bordered">
                        <thead class="thead-dark"><tr><th>ID</th><th>Negocio</th><th>Código</th><th>Estado</th><th></th></tr></thead>
                        <tbody>
                        <c:forEach items="${tenants}" var="tenant">
                            <tr>
                                <td>${tenant.id}</td>
                                <td><c:out value="${tenant.nombre}"/></td>
                                <td><code><c:out value="${tenant.codigo}"/></code></td>
                                <td><span class="badge ${tenant.activo ? 'badge-success' : 'badge-secondary'}">${tenant.activo ? 'Activo' : 'Inactivo'}</span></td>
                                <td class="text-nowrap">
                                    <form method="post" class="d-inline-block mr-1" action="${pageContext.request.contextPath}/tenants/${tenant.id}/estado">
                                        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}">
                                        <button class="btn btn-sm btn-outline-${tenant.activo ? 'danger' : 'success'}" type="submit"
                                                ${tenant.id == 1 && tenant.activo ? 'disabled' : ''}>
                                            ${tenant.activo ? 'Desactivar' : 'Activar'}
                                        </button>
                                    </form>
                                    <c:if test="${tenant.id != 1}">
                                        <c:choose>
                                            <c:when test="${not tenant.activo}">
                                                <form method="post" class="d-inline-block"
                                                      action="${pageContext.request.contextPath}/tenants/${tenant.id}/eliminar"
                                                      onsubmit="return confirmarEliminacionNegocio(this, '${tenant.codigo}');">
                                                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}">
                                                    <input type="hidden" name="confirmacion" value="">
                                                    <button class="btn btn-sm btn-danger" type="submit">
                                                        <i class="fa-solid fa-trash mr-1"></i>Borrar
                                                    </button>
                                                </form>
                                            </c:when>
                                            <c:otherwise>
                                                <button class="btn btn-sm btn-outline-danger" type="button" disabled
                                                        title="Primero desactive el negocio">Borrar</button>
                                            </c:otherwise>
                                        </c:choose>
                                    </c:if>
                                </td>
                            </tr>
                        </c:forEach>
                        </tbody>
                    </table>
                </div>
            </div>
        </div>
    </div>
</div>
<script>
function confirmarEliminacionNegocio(formulario, codigo) {
    const ingresado = window.prompt(
        'Esta acción elimina definitivamente usuarios, productos, ventas y conexiones del negocio.\n\n' +
        'Escribí el código "' + codigo + '" para confirmar:'
    );
    if (ingresado === null) return false;
    formulario.querySelector('input[name="confirmacion"]').value = ingresado;
    return true;
}
</script>
</body>
</html>

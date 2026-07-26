<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>

<!DOCTYPE html>
<html lang="es">
<head>
    <jsp:include page="/WEB-INF/jsp/head.jsp"/>
</head>

<body id="page-top">
<div id="wrapper">

<jsp:include page="/WEB-INF/jsp/nav_bar.jsp"/>

    <div class="container mt-4">

    <!-- MENSAJES -->
    <c:if test="${not empty mensaje}">
        <div class="alert alert-success">${mensaje}</div>
    </c:if>

    <c:if test="${not empty error}">
        <div class="alert alert-danger">${error}</div>
    </c:if>

    <c:if test="${not empty venta}">



                <div class="d-flex justify-content-between align-items-center mb-3">
                    <h4 class="mb-0">💰 Detalle de Venta</h4>

                    <div class="d-flex gap-2">
                        <a href="${pageContext.request.contextPath}/ventas?accion=nueva"
                           class="btn btn-primary btn-sm mr-2">
                            + Nueva Venta
                        </a>
                        <a href="${pageContext.request.contextPath}/ventas?accion=listar"
                           class="btn btn-outline-secondary btn-sm">
                            📋 Volver al listado
                        </a>
                    </div>
                </div>

                <div class="card">

                    <!-- HEADER -->
                    <div class="card-header bg-dark text-white d-flex justify-content-between align-items-center">
                        <div class="fs-5 fw-bold">
                            ${venta.codigo}
                        </div>

                        <div class="d-flex gap-2 align-items-center">

                            <span class="badge
                                ${venta.origen == 'DIRECTA'
                                    ? 'bg-primary'
                                    : 'bg-info text-dark'}">
                                ${venta.origen == 'DIRECTA'
                                    ? '📝 Venta directa'
                                    : '📄 Desde presupuesto'}
                            </span>

                            <span class="badge
                                ${venta.estado == 'COMPLETADA'
                                    ? 'bg-success'
                                    : 'bg-danger'} fs-6">
                                ${venta.estado}
                            </span>

                        </div>
                    </div>

                    <!-- BODY -->
                    <div class="card-body">

                        <div class="row mb-3 pb-3 border-bottom">

                            <div class="col-md-3">
                                <div class="text-muted">Fecha</div>
                                <div class="small">
                                    ${fechaVentaFmt}
                                </div>
                            </div>

                            <div class="col-md-3">
                                <div class="text-muted">Cliente</div>
                                <div class="small">
                                    <c:choose>
                                                                                <c:when test="${venta.cliente != null}">
                                                                                    ${venta.cliente.nombre} ${venta.cliente.apellido}
                                                                                </c:when>
                                                                                <c:otherwise>
                                                                                    <span class="text-muted">Consumidor Final</span>
                                                                                </c:otherwise>
                                                                            </c:choose>
                                </div>
                            </div>

                            <div class="col-md-3">
                                <div class="text-muted">Forma de pago</div>
                                <div class="small">
                                    <c:choose>
                                        <c:when test="${not empty venta.formaPago}">
                                            ${venta.formaPago}
                                        </c:when>
                                        <c:otherwise>-</c:otherwise>
                                    </c:choose>
                                </div>
                            </div>

                            <div class="col-md-3">
                                <c:if test="${not empty venta.presupuestoCodigo}">
                                    <div class="text-muted">Presupuesto</div>
                                    <a class="small text-decoration-none"
                                       href="${pageContext.request.contextPath}/presupuestos?accion=detalle&codigo=${venta.presupuestoCodigo}">
                                        📄 ${venta.presupuestoCodigo}
                                    </a>
                                </c:if>
                            </div>

                        </div>

                        <!-- ITEMS -->
                        <table class="table table-hover table-striped">
                            <thead class="table-dark">
                            <tr>
                                <th>Producto</th>
                                <th class="text-center">Cantidad</th>
                                <th class="text-end">Precio Unit.</th>
                                <th class="text-end">Subtotal</th>
                            </tr>
                            </thead>

                            <tbody>
                            <c:forEach items="${venta.items}" var="item">
                                <tr>
                                    <td>${item.descripcionProducto}</td>
                                    <td class="text-center">${item.cantidad}</td>
                                    <td class="text-end">
                                        $<fmt:formatNumber value="${item.precioUnitario}"
                                                           minFractionDigits="2"/>
                                    </td>
                                    <td class="text-end fw-semibold">
                                        $<fmt:formatNumber value="${item.subtotal}"
                                                           minFractionDigits="2"/>
                                    </td>
                                </tr>
                            </c:forEach>
                            </tbody>

                            <tfoot class="table-secondary">
                            <c:choose>
                                <c:when test="${venta.cliente != null
                                               and venta.cliente.condicionIva == 'RESPONSABLE_INSCRIPTO'}">

                                    <tr>
                                        <td colspan="3" class="text-end fw-bold">Neto</td>
                                        <td class="text-end">
                                            $<fmt:formatNumber value="${totales.totalNeto}" minFractionDigits="2"/>
                                        </td>
                                    </tr>

                                    <c:forEach var="entry" items="${totales.ivasMap}">
                                        <tr>
                                            <td colspan="3" class="text-end fw-bold">
                                                IVA <c:out value="${entry.key}"/>%
                                            </td>
                                            <td class="text-end">
                                                $<fmt:formatNumber value="${entry.value}" minFractionDigits="2"/>
                                            </td>
                                        </tr>
                                    </c:forEach>

                                    <tr>
                                        <td colspan="3" class="text-end fw-bold fs-5">TOTAL</td>
                                        <td class="text-end fw-bold fs-5 text-success">
                                            $<fmt:formatNumber value="${totales.total}" minFractionDigits="2"/>
                                        </td>
                                    </tr>


                                </c:when>

                                <c:otherwise>
                                    <tr>
                                        <td colspan="3" class="fw-bold">TOTAL</td>
                                        <td class="text-end fw-bold fs-5 text-success">
                                            $<fmt:formatNumber value="${venta.total}" minFractionDigits="2"/>
                                        </td>
                                    </tr>
                                </c:otherwise>
                            </c:choose>


                            </tfoot>
                        </table>

                        <!-- NOTA -->
                        <c:if test="${not empty venta.nota}">
                            <div class="text-muted small">
                                📝 ${venta.nota}
                            </div>
                        </c:if>

                        <!-- ANULADA -->
                        <c:if test="${venta.estado == 'ANULADA' and not empty venta.fechaAnulacion}">
                            <div class="alert alert-danger small mt-2">
                                ⚠️ Anulada el
                                    ${fechaAnulacionFmt}
                            </div>
                        </c:if>

                        <c:if test="${venta.estado == 'COMPLETADA'}">
                            <div class="d-flex gap-2 mt-2">

                                <!-- ANULAR -->
                                <form action="${pageContext.request.contextPath}/ventas/anular"
                                      method="post"
                                      onsubmit="return confirm('¿Anular esta venta? Se devolverá el stock.')">
                                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                                    <input type="hidden" name="id" value="${venta.id}"/>

                                    <button type="submit" class="btn btn-outline-danger">
                                        ❌ Anular venta
                                    </button>
                                </form>

                                <a href="${pageContext.request.contextPath}/remitos/venta/${venta.id}/pdf"
                                   class="btn btn-outline-primary"
                                   target="_blank">
                                   📄 Generar / Descargar remito
                                </a>

                            </div>
                        </c:if>
</c:if>
</div>

<!-- Footer -->
<footer class="sticky-footer bg-white">
    <div class="container my-auto">
        <div class="copyright text-center my-auto">
            <span>Copyright &copy;</span>
        </div>
    </div>
</footer>

<script>
document.addEventListener("DOMContentLoaded", function () {

    const params = new URLSearchParams(window.location.search);
    const remitoId = params.get('remitoId');

    if (remitoId) {

        const url = '${pageContext.request.contextPath}/remitos/' + remitoId + '/pdf';

        const win = window.open(url, '_blank');

        if (!win) {
            window.location.href = url;
        }
    }

});
</script>
</body>
</html>

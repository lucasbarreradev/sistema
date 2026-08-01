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
                                                        <h1 class="h3 mb-0 text-gray-800">Presupuestos</h1>

                                                        <a href="${pageContext.request.contextPath}/presupuestos/nuevo"
                                                           class="btn btn-primary">
                                                            + Nuevo Presupuesto
                                                        </a>
                                                    </div>

                                                    <!-- Card -->
                                                    <div class="card shadow mb-4">

                                                        <div class="card-header py-3">
                                                            <h6 class="m-0 font-weight-bold text-primary">
                                                                Listado de Presupuestos
                                                            </h6>
                                                        </div>
                                                        <div class="d-flex justify-content-between align-items-center m-4">

                                                            <!-- Buscador -->
                                                            <div style="max-width: 300px; width: 100%;">
                                                                <input type="text" id="searchInput"
                                                                       class="form-control"
                                                                       placeholder="Buscar presupuesto...">
                                                            </div>

                                                            <!-- Filtros por estado -->
                                                            <div class="btn-group">
                                                                <a href="${pageContext.request.contextPath}/presupuestos"
                                                                   class="btn btn-sm ${empty filtroEstado ? 'btn-dark' : 'btn-outline-dark'}">
                                                                    Todos
                                                                </a>
                                                                <a href="${pageContext.request.contextPath}/presupuestos?estado=PENDIENTE"
                                                                   class="btn btn-sm ${filtroEstado == 'PENDIENTE' ? 'btn-warning' : 'btn-outline-warning'}">
                                                                    Pendientes
                                                                </a>
                                                                <a href="${pageContext.request.contextPath}/presupuestos?estado=APROBADO"
                                                                   class="btn btn-sm ${filtroEstado == 'APROBADO' ? 'btn-success' : 'btn-outline-success'}">
                                                                    Aprobados
                                                                </a>
                                                                <a href="${pageContext.request.contextPath}/presupuestos?estado=RECHAZADO"
                                                                   class="btn btn-sm ${filtroEstado == 'RECHAZADO' ? 'btn-danger' : 'btn-outline-danger'}">
                                                                    Rechazados
                                                                </a>
                                                            </div>

                                                        </div>


                <!-- Tabla -->
                    <div class="card-body pt-0">
                        <div class="table-responsive">
                            <table id="dataTable" class="table table-bordered table-hover">
                                <thead class="thead-dark">
                                <tr>
                                    <th>Código</th>
                                    <th>Cliente</th>
                                    <th>Fecha</th>
                                    <th class="text-end">Total</th>
                                    <th class="text-center">Estado</th>
                                    <th class="text-center">Acciones</th>
                                </tr>
                                </thead>
                                <tbody>
                                <c:choose>
                                    <c:when test="${not empty presupuestos}">
                                        <c:forEach items="${presupuestos}" var="p">
                                            <tr>
                                                <td>
                                                    <strong>${p.codigo}</strong>
                                                </td>
                                                <td>
                                                    <c:choose>
                                                        <c:when test="${p.cliente != null}">
                                                            ${p.cliente.nombre} ${p.cliente.apellido}
                                                        </c:when>
                                                        <c:otherwise>
                                                            <span class="text-muted">Consumidor Final</span>
                                                        </c:otherwise>
                                                    </c:choose>
                                                </td>
                                                <td>
                                                    ${p.fechaFormateada}
                                                </td>
                                                <td class="text-end">

                                                    <strong>
                                                        $
                                                        <fmt:formatNumber
                                                            value="${p.total}"
                                                            minFractionDigits="2"/>
                                                    </strong>

                                                </td>
                                                <td class="text-center">
                                                    <span class="badge
                                                        ${p.estado == 'PENDIENTE' ? 'bg-warning text-dark' :
                                                          p.estado == 'APROBADO' ? 'bg-success' : 'bg-danger'}">
                                                        ${p.estado}
                                                    </span>
                                                </td>
                                                <td class="text-center">
                                                    <a class="btn btn-sm btn-info"
                                                       href="${pageContext.request.contextPath}/presupuestos/detalle/${p.id}">
                                                        Ver
                                                    </a>
                                                </td>
                                            </tr>
                                        </c:forEach>
                                    </c:when>
                                    <c:otherwise>
                                        <tr>
                                            <td colspan="6" class="text-center text-muted py-4">
                                                No hay presupuestos registrados
                                            </td>
                                        </tr>
                                    </c:otherwise>
                                </c:choose>
                                </tbody>
                            </table>
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
                    document.getElementById('searchInput').addEventListener('keyup', function () {
                        const filter = this.value.toLowerCase();
                        const rows = document.querySelectorAll('#dataTable tbody tr');

                        rows.forEach(row => {
                            row.style.display = row.textContent.toLowerCase().includes(filter)
                                ? ''
                                : 'none';
                        });
                    });
                    <c:if test="${param.pdf == 'true'}">
                            window.open(
                                '${pageContext.request.contextPath}/presupuestos/${presupuesto.id}/pdf',
                                '_blank'
                            );
                    </c:if>
                    </script>
</body>
</html>

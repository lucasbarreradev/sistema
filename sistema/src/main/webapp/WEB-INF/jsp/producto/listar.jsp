<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>

<!DOCTYPE html>
<html lang="en">

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
                    <h1 class="h3 mb-0 text-gray-800">Productos</h1>

                    <a href="${pageContext.request.contextPath}/productos/nuevo/limpio"
                       class="btn btn-primary">
                        + Nuevo Producto
                    </a>
                    <a href="${pageContext.request.contextPath}/canales"
                       class="btn btn-info ml-2">
                        Importar / Publicar
                    </a>

                </div>

                <!-- Card -->
                <div class="card shadow mb-4">

                    <div class="card-header py-3">
                        <h6 class="m-0 font-weight-bold text-primary">
                            Listado de Productos
                        </h6>
                    </div>
                    <div class="d-sm-flex align-items-center m-4">
                        <div class="ms-auto" style="max-width: 300px;">
                            <input type="text" id="searchInput"
                                   class="form-control"
                                   placeholder="Buscar producto...">
                        </div>
                    </div>

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
                    </script>

                    <div class="card-body">
                        <div class="table-responsive">

                            <table class="table table-bordered table-hover"
                                   id="dataTable"
                                   width="100%" cellspacing="0">

                                <thead class="table-dark">
                                <tr>
                                    <th>Foto</th>
                                    <th>SKU</th>
                                    <th>Descripción</th>
                                    <th>Cantidad</th>
                                    <th>Precio Contado</th>
                                    <th>Precio Cuenta Corriente</th>
                                    <th>Precio Tarjeta</th>
                                    <th>Proveedor</th>
                                    <th>Acciones</th>
                                </tr>
                                </thead>

                                <tbody>
                                <c:forEach items="${productos}" var="p">
                                    <tr>
                                        <td class="text-center">
                                            <c:choose>
                                                <c:when test="${p.tieneFoto()}">
                                                    <img src="${pageContext.request.contextPath}/productos/${p.id}/foto"
                                                         alt="Foto" style="width:52px;height:52px;object-fit:contain;background:#fff;border-radius:6px;">
                                                </c:when>
                                                <c:otherwise><span class="text-muted">Sin foto</span></c:otherwise>
                                            </c:choose>
                                        </td>
                                        <td>${p.sku}</td>
                                        <td>${p.descripcion}</td>
                                        <td>${p.stockTotal}<c:if test="${p.tieneVariantes()}"><br><span class="badge badge-info">Variantes</span></c:if></td>
                                        <td>${p.precioContado}</td>
                                        <td>${p.precioCuentaCorriente}</td>
                                        <td>${p.precioTarjeta}</td>
                                        <td>
                                            <c:choose>
                                                <c:when test="${p.proveedor != null}">
                                                    ${p.proveedor.nombreRazonSocial}
                                                </c:when>
                                                <c:otherwise>
                                                    <span class="text-muted">Sin proveedor</span>
                                                </c:otherwise>
                                            </c:choose>
                                        </td>

                                        <td class="text-center">
                                            <a class="btn btn-sm btn-info"
                                               href="${pageContext.request.contextPath}/productos/${p.id}/variantes">Variantes</a>
                                            <a class="btn btn-sm btn-warning"
                                               href="${pageContext.request.contextPath}/productos/editar/${p.id}">
                                                Editar
                                            </a>
                                            <form method="post"
                                                  action="${pageContext.request.contextPath}/productos/eliminar/${p.id}"
                                                  style="display:inline;"
                                                  onsubmit="return confirm('¿Eliminar producto?');">

                                                <input type="hidden"
                                                       name="${_csrf.parameterName}"
                                                       value="${_csrf.token}" />

                                                <button type="submit" class="btn btn-sm btn-danger">
                                                    Eliminar
                                                </button>
                                            </form>

                                        </td>
                                    </tr>
                                </c:forEach>
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

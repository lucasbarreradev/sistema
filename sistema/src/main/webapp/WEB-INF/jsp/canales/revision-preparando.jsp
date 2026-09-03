<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html lang="es">
<head>
    <jsp:include page="/WEB-INF/jsp/head.jsp"/>
    <meta http-equiv="refresh" content="2;url=${pageContext.request.contextPath}/canales/publicar/revision">
</head>
<body id="page-top">
<div id="wrapper">
    <jsp:include page="/WEB-INF/jsp/nav_bar.jsp"/>
    <div id="content-wrapper" class="d-flex flex-column">
        <div class="container-fluid py-5">
            <div class="card shadow mx-auto" style="max-width:760px">
                <div class="card-body text-center p-5">
                    <div class="spinner-border text-primary mb-4" role="status">
                        <span class="sr-only">Procesando...</span>
                    </div>
                    <h1 class="h3 text-gray-800">Preparando la revisión</h1>
                    <p class="text-muted mb-3">
                        Se están detectando las categorías y los atributos de
                        <strong>${cantidadProductosRevision}</strong> producto(s).
                    </p>
                    <p class="mb-4"><c:out value="${trabajoPreparacion.resumen}"/></p>
                    <div class="mb-4">
                        <c:forEach items="${revisionCanales}" var="canal">
                            <span class="badge badge-primary p-2 mr-1">${canal.descripcion}</span>
                        </c:forEach>
                    </div>
                    <div class="alert alert-info text-left mb-4">
                        Todavía no se está publicando ningún artículo. Cuando termine el análisis,
                        esta página abrirá automáticamente la tabla de revisión.
                    </div>

                </div>
            </div>
        </div>
    </div>
</div>
<jsp:include page="/WEB-INF/jsp/foot.jsp"/>
</body>
</html>

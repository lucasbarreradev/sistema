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
            <h1 class="h3 text-gray-800 mb-4">Crear negocio</h1>
            <c:if test="${not empty error}"><div class="alert alert-danger"><c:out value="${error}"/></div></c:if>
            <div class="card shadow">
                <div class="card-body">
                    <form method="post" action="${pageContext.request.contextPath}/tenants">
                        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}">
                        <h6 class="font-weight-bold text-primary">Negocio</h6>
                        <div class="form-row">
                            <div class="form-group col-md-7"><label>Nombre del negocio</label><input class="form-control" name="nombreNegocio" required></div>
                            <div class="form-group col-md-5"><label>Código interno</label><input class="form-control" name="codigo" placeholder="Se genera desde el nombre"></div>
                        </div>
                        <hr>
                        <h6 class="font-weight-bold text-primary">Usuario administrador inicial</h6>
                        <div class="form-row">
                            <div class="form-group col-md-6"><label>Nombre</label><input class="form-control" name="nombreAdmin" required></div>
                            <div class="form-group col-md-6"><label>Apellido</label><input class="form-control" name="apellidoAdmin" required></div>
                            <div class="form-group col-md-6"><label>Usuario</label><input class="form-control" name="username" required autocomplete="off"></div>
                            <div class="form-group col-md-6"><label>Contraseña</label><input type="password" class="form-control" name="password" minlength="8" required autocomplete="new-password"></div>
                        </div>
                        <button class="btn btn-success" type="submit">Crear negocio y administrador</button>
                        <a class="btn btn-outline-secondary" href="${pageContext.request.contextPath}/tenants">Cancelar</a>
                    </form>
                </div>
            </div>
        </div>
    </div>
</div>
</body>
</html>

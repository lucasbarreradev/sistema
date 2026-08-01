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
            <div class="d-flex justify-content-between align-items-center mb-4">
                <div>
                    <h1 class="h3 text-gray-800 mb-1">Datos de la empresa</h1>
                    <p class="text-muted mb-0">
                        Estos datos se mostrarán en los presupuestos y remitos.
                    </p>
                </div>
            </div>

            <c:if test="${primeraConfiguracion}">
                <div class="alert alert-info">
                    Antes de crear el primer presupuesto o remito, configurá
                    cómo debe aparecer tu empresa en los documentos.
                </div>
            </c:if>
            <c:if test="${not empty mensaje}">
                <div class="alert alert-success"><c:out value="${mensaje}"/></div>
            </c:if>
            <c:if test="${not empty error}">
                <div class="alert alert-danger"><c:out value="${error}"/></div>
            </c:if>

            <form method="post" enctype="multipart/form-data"
                  action="${pageContext.request.contextPath}/datos-empresa">
                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}">
                <input type="hidden" name="continuar" value="${fn:escapeXml(continuar)}">

                <div class="card shadow mb-4">
                    <div class="card-header"><strong>Identidad comercial</strong></div>
                    <div class="card-body">
                        <div class="row">
                            <div class="col-md-6 mb-3">
                                <label>Nombre de la empresa *</label>
                                <input name="nombreEmpresa" class="form-control" maxlength="180"
                                       value="${fn:escapeXml(configuracion.nombreEmpresa)}" required>
                            </div>
                            <div class="col-md-3 mb-3">
                                <label>CUIT</label>
                                <input name="cuit" class="form-control" maxlength="40"
                                       value="${fn:escapeXml(configuracion.cuit)}">
                            </div>
                            <div class="col-md-3 mb-3">
                                <label>Persona de contacto</label>
                                <input name="nombreContacto" class="form-control" maxlength="150"
                                       value="${fn:escapeXml(configuracion.nombreContacto)}">
                            </div>
                            <div class="col-md-6 mb-3">
                                <label>Logo</label>
                                <input type="file" name="logo" class="form-control-file"
                                       accept="image/jpeg,image/png">
                                <small class="form-text text-muted">JPG o PNG. Máximo 5 MB.</small>
                                <c:if test="${configuracion.tieneLogo()}">
                                    <img class="img-thumbnail mt-2"
                                         src="${pageContext.request.contextPath}/datos-empresa/logo"
                                         alt="Logo actual"
                                         style="max-width:220px;max-height:120px;object-fit:contain">
                                    <div class="form-check mt-2">
                                        <input class="form-check-input" type="checkbox"
                                               name="quitarLogo" value="true" id="quitarLogo">
                                        <label class="form-check-label" for="quitarLogo">
                                            Quitar logo actual
                                        </label>
                                    </div>
                                </c:if>
                            </div>
                        </div>
                    </div>
                </div>

                <div class="card shadow mb-4">
                    <div class="card-header"><strong>Dirección y contacto</strong></div>
                    <div class="card-body">
                        <div class="row">
                            <div class="col-md-6 mb-3">
                                <label>Dirección</label>
                                <input name="direccion" class="form-control" maxlength="250"
                                       value="${fn:escapeXml(configuracion.direccion)}">
                            </div>
                            <div class="col-md-2 mb-3">
                                <label>Código postal</label>
                                <input name="codigoPostal" class="form-control" maxlength="20"
                                       value="${fn:escapeXml(configuracion.codigoPostal)}">
                            </div>
                            <div class="col-md-4 mb-3">
                                <label>Localidad</label>
                                <input name="localidad" class="form-control" maxlength="120"
                                       value="${fn:escapeXml(configuracion.localidad)}">
                            </div>
                            <div class="col-md-4 mb-3">
                                <label>Provincia</label>
                                <input name="provincia" class="form-control" maxlength="120"
                                       value="${fn:escapeXml(configuracion.provincia)}">
                            </div>
                            <div class="col-md-4 mb-3">
                                <label>País</label>
                                <input name="pais" class="form-control" maxlength="120"
                                       value="${fn:escapeXml(configuracion.pais)}">
                            </div>
                            <div class="col-md-4 mb-3">
                                <label>Teléfono</label>
                                <input name="telefono" class="form-control" maxlength="100"
                                       value="${fn:escapeXml(configuracion.telefono)}">
                            </div>
                            <div class="col-md-6 mb-3">
                                <label>Email</label>
                                <input type="email" name="email" class="form-control" maxlength="180"
                                       value="${fn:escapeXml(configuracion.email)}">
                            </div>
                        </div>
                    </div>
                </div>

                <div class="text-right">
                    <button class="btn btn-primary" type="submit">
                        Guardar datos de la empresa
                    </button>
                </div>
            </form>
        </div>
    </div>
</div>
</body>
</html>

<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="sec" uri="http://www.springframework.org/security/tags" %>
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
                    <h1 class="h3 text-gray-800 mb-1">Facturaci&oacute;n electr&oacute;nica</h1>
                    <p class="text-muted mb-0">Ventas del sistema, Mercado Libre, Tiendanube y WooCommerce.</p>
                </div>
                <span class="badge badge-warning p-2">ARCA - Modo testing</span>
            </div>

            <c:if test="${not empty mensaje}"><div class="alert alert-success"><c:out value="${mensaje}"/></div></c:if>
            <c:if test="${not empty error}"><div class="alert alert-danger"><c:out value="${error}"/></div></c:if>

            <div class="card shadow mb-4">
                <div class="card-header d-flex justify-content-between align-items-center">
                    <strong>Conexi&oacute;n de homologaci&oacute;n</strong>
                    <c:choose>
                        <c:when test="${arcaConfigurada}"><span class="badge badge-success">Configurada</span></c:when>
                        <c:otherwise><span class="badge badge-secondary">Pendiente</span></c:otherwise>
                    </c:choose>
                </div>
                <div class="card-body">
                    <div class="alert alert-info">
                        Us&aacute; exclusivamente el certificado generado en WSASS para testing y autorizalo para el servicio
                        <strong>wsfe</strong>. Los CAE emitidos aqu&iacute; son de homologaci&oacute;n y no tienen validez fiscal.
                        <a class="alert-link ml-1" target="_blank" rel="noopener noreferrer"
                           href="https://www.arca.gob.ar/ws/WSASS/">Ver instrucciones oficiales de WSASS</a>.
                    </div>
                    <sec:authorize access="hasRole('ADMIN')">
                    <form method="post" enctype="multipart/form-data"
                          action="${pageContext.request.contextPath}/facturacion/configuracion">
                        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}">
                        <div class="row">
                            <div class="col-md-3 mb-3">
                                <label>CUIT representado *</label>
                                <input name="cuit" class="form-control" maxlength="14" required
                                       value="${configuracionArca.cuit}" placeholder="20-12345678-3">
                            </div>
                            <div class="col-md-2 mb-3">
                                <label>Punto de venta *</label>
                                <input type="number" min="1" max="99998" name="puntoVenta"
                                       class="form-control" required value="${configuracionArca.puntoVenta}">
                            </div>
                            <div class="col-md-3 mb-3">
                                <label>Condici&oacute;n fiscal *</label>
                                <select name="condicionFiscal" class="form-control" required>
                                    <c:forEach items="${condicionesFiscales}" var="condicion">
                                        <option value="${condicion}" ${configuracionArca.condicionFiscal == condicion ? 'selected' : ''}>
                                            <c:out value="${condicion.descripcion}"/>
                                        </option>
                                    </c:forEach>
                                </select>
                            </div>
                            <div class="col-md-4 mb-3">
                                <label>Ambiente</label>
                                <input class="form-control" value="Homologaci&oacute;n / testing" disabled>
                            </div>
                            <div class="col-md-6 mb-3">
                                <label>Certificado de WSASS ${arcaConfigurada ? '(dejar vac&iacute;o para conservar)' : '*'}</label>
                                <input type="file" name="certificado" class="form-control-file"
                                       accept=".crt,.cer,.pem" ${arcaConfigurada ? '' : 'required'}>
                            </div>
                            <div class="col-md-6 mb-3">
                                <label>Clave privada sin contrase&ntilde;a ${arcaConfigurada ? '(dejar vac&iacute;o para conservar)' : '*'}</label>
                                <input type="file" name="clavePrivada" class="form-control-file"
                                       accept=".key,.pem" ${arcaConfigurada ? '' : 'required'}>
                            </div>
                        </div>
                        <button class="btn btn-primary" type="submit">Guardar configuraci&oacute;n de prueba</button>
                    </form>
                    </sec:authorize>
                    <sec:authorize access="!hasRole('ADMIN')">
                        <div class="alert alert-secondary mb-0">Un administrador debe configurar los certificados de ARCA.</div>
                    </sec:authorize>
                    <c:if test="${arcaConfigurada}">
                        <hr>
                        <div class="d-flex align-items-center justify-content-between">
                            <small class="text-muted">
                                Titular: <c:out value="${configuracionArca.titular}"/> · Certificado cargado.
                            </small>
                            <sec:authorize access="hasRole('ADMIN')">
                            <form method="post" action="${pageContext.request.contextPath}/facturacion/probar">
                                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}">
                                <button class="btn btn-outline-success" type="submit">Probar conexi&oacute;n con ARCA</button>
                            </form>
                            </sec:authorize>
                        </div>
                    </c:if>
                </div>
            </div>

            <div class="card shadow mb-4">
                <div class="card-header"><strong>Ventas disponibles para facturar</strong></div>
                <div class="card-body">
                    <input id="buscarVentaFiscal" class="form-control mb-3" style="max-width:360px"
                           placeholder="Buscar por venta, cliente, canal u orden...">
                    <div class="table-responsive">
                        <table class="table table-bordered table-hover" id="tablaFacturacion">
                            <thead class="thead-dark"><tr>
                                <th>Venta</th><th>Origen</th><th>Orden externa</th><th>Cliente</th>
                                <th>Fecha</th><th class="text-right">Total</th><th>Estado fiscal</th><th>Acci&oacute;n</th>
                            </tr></thead>
                            <tbody>
                            <c:forEach items="${ventas}" var="venta">
                                <tr>
                                    <td><c:out value="${venta.codigo}"/></td>
                                    <td><c:out value="${venta.origenDescripcion}"/></td>
                                    <td><c:out value="${venta.ordenExternaId}"/></td>
                                    <td><c:out value="${venta.clienteDescripcion}"/></td>
                                    <td><c:out value="${venta.fechaFormateada}"/></td>
                                    <td class="text-right">$ <fmt:formatNumber value="${venta.total}" minFractionDigits="2"/></td>
                                    <td>
                                        <c:choose>
                                            <c:when test="${not empty venta.cae}">
                                                <span class="badge badge-success">Autorizada (testing)</span>
                                                <div class="small mt-1">CAE: <c:out value="${venta.cae}"/></div>
                                            </c:when>
                                            <c:otherwise><span class="badge badge-secondary">Pendiente</span></c:otherwise>
                                        </c:choose>
                                    </td>
                                    <td class="text-center">
                                        <c:choose>
                                            <c:when test="${not empty venta.cae}">
                                                <a target="_blank" class="btn btn-sm btn-info mb-1"
                                                   href="${pageContext.request.contextPath}/facturacion/${venta.id}/pdf">Ver PDF</a>
                                                <button type="button" class="btn btn-sm btn-outline-danger mb-1"
                                                        data-toggle="modal" data-target="#notaCredito${venta.id}"
                                                        ${saldosCredito[venta.id] <= 0 ? 'disabled' : ''}>
                                                    Nota de cr&eacute;dito
                                                </button>
                                                <button type="button" class="btn btn-sm btn-outline-primary mb-1"
                                                        data-toggle="modal" data-target="#notaDebito${venta.id}">
                                                    Nota de d&eacute;bito
                                                </button>
                                                <c:set var="ajustes" value="${comprobantesPorVenta[venta.id]}"/>
                                                <c:if test="${not empty ajustes}">
                                                    <div class="small text-left mt-2">
                                                        <strong>Comprobantes asociados:</strong>
                                                        <c:forEach items="${ajustes}" var="ajuste">
                                                            <div>
                                                                <a target="_blank"
                                                                   href="${pageContext.request.contextPath}/facturacion/comprobantes/${ajuste.id}/pdf">
                                                                    <c:out value="${ajuste.tipoComprobante.descripcion}"/>
                                                                    <c:out value="${ajuste.numeroFormateado}"/>
                                                                </a>
                                                                &middot; $ <fmt:formatNumber value="${ajuste.total}" minFractionDigits="2"/>
                                                            </div>
                                                        </c:forEach>
                                                    </div>
                                                </c:if>
                                            </c:when>
                                            <c:when test="${arcaConfigurada}">
                                                <form method="post" action="${pageContext.request.contextPath}/facturacion/emitir/${venta.id}"
                                                      onsubmit="return confirm('¿Emitir este comprobante en homologación?')">
                                                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}">
                                                    <button class="btn btn-sm btn-success">Facturar prueba</button>
                                                </form>
                                            </c:when>
                                            <c:otherwise><button class="btn btn-sm btn-secondary" disabled>Configure ARCA</button></c:otherwise>
                                        </c:choose>
                                    </td>
                                </tr>
                            </c:forEach>
                            </tbody>
                        </table>
                    </div>

                    <c:forEach items="${ventas}" var="venta">
                        <c:if test="${not empty venta.cae}">
                            <div class="modal fade" id="notaCredito${venta.id}" tabindex="-1" role="dialog"
                                 aria-labelledby="tituloNotaCredito${venta.id}" aria-hidden="true">
                                <div class="modal-dialog" role="document">
                                    <div class="modal-content">
                                        <form method="post"
                                              action="${pageContext.request.contextPath}/facturacion/nota-credito/${venta.id}">
                                            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}">
                                            <div class="modal-header">
                                                <h5 class="modal-title" id="tituloNotaCredito${venta.id}">
                                                    Nota de cr&eacute;dito de <c:out value="${venta.codigo}"/>
                                                </h5>
                                                <button type="button" class="close" data-dismiss="modal" aria-label="Cerrar">
                                                    <span aria-hidden="true">&times;</span>
                                                </button>
                                            </div>
                                            <div class="modal-body">
                                                <div class="alert alert-light border">
                                                    Saldo acreditable: <strong>$ <fmt:formatNumber
                                                        value="${saldosCredito[venta.id]}" minFractionDigits="2"/></strong>
                                                </div>
                                                <div class="form-group">
                                                    <label>Alcance *</label>
                                                    <select class="form-control js-tipo-credito" name="total"
                                                            data-importe="#importeCredito${venta.id}">
                                                        <option value="true">Total del saldo pendiente</option>
                                                        <option value="false">Parcial</option>
                                                    </select>
                                                </div>
                                                <div class="form-group js-importe-credito" id="importeCredito${venta.id}"
                                                     style="display:none">
                                                    <label>Importe parcial *</label>
                                                    <input type="number" name="importe" class="form-control"
                                                           min="0.01" max="${saldosCredito[venta.id]}" step="0.01">
                                                </div>
                                                <div class="form-group mb-0">
                                                    <label>Motivo *</label>
                                                    <textarea name="motivo" class="form-control" rows="3" maxlength="500"
                                                              required placeholder="Ej.: devoluci&oacute;n de mercader&iacute;a"></textarea>
                                                </div>
                                            </div>
                                            <div class="modal-footer">
                                                <button type="button" class="btn btn-secondary" data-dismiss="modal">Cancelar</button>
                                                <button type="submit" class="btn btn-danger"
                                                        onclick="return confirm('¿Emitir la nota de cr&eacute;dito en homologaci&oacute;n?')">
                                                    Emitir nota de cr&eacute;dito
                                                </button>
                                            </div>
                                        </form>
                                    </div>
                                </div>
                            </div>

                            <div class="modal fade" id="notaDebito${venta.id}" tabindex="-1" role="dialog"
                                 aria-labelledby="tituloNotaDebito${venta.id}" aria-hidden="true">
                                <div class="modal-dialog" role="document">
                                    <div class="modal-content">
                                        <form method="post"
                                              action="${pageContext.request.contextPath}/facturacion/nota-debito/${venta.id}">
                                            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}">
                                            <div class="modal-header">
                                                <h5 class="modal-title" id="tituloNotaDebito${venta.id}">
                                                    Nota de d&eacute;bito de <c:out value="${venta.codigo}"/>
                                                </h5>
                                                <button type="button" class="close" data-dismiss="modal" aria-label="Cerrar">
                                                    <span aria-hidden="true">&times;</span>
                                                </button>
                                            </div>
                                            <div class="modal-body">
                                                <div class="form-group">
                                                    <label>Importe adicional *</label>
                                                    <input type="number" name="importe" class="form-control"
                                                           min="0.01" step="0.01" required>
                                                </div>
                                                <div class="form-group mb-0">
                                                    <label>Motivo *</label>
                                                    <textarea name="motivo" class="form-control" rows="3" maxlength="500"
                                                              required placeholder="Ej.: diferencia de precio o intereses"></textarea>
                                                </div>
                                            </div>
                                            <div class="modal-footer">
                                                <button type="button" class="btn btn-secondary" data-dismiss="modal">Cancelar</button>
                                                <button type="submit" class="btn btn-primary"
                                                        onclick="return confirm('¿Emitir la nota de d&eacute;bito en homologaci&oacute;n?')">
                                                    Emitir nota de d&eacute;bito
                                                </button>
                                            </div>
                                        </form>
                                    </div>
                                </div>
                            </div>
                        </c:if>
                    </c:forEach>
                </div>
            </div>
        </div>
    </div>
</div>
<jsp:include page="/WEB-INF/jsp/foot.jsp"/>
<script>
document.getElementById('buscarVentaFiscal').addEventListener('input', function () {
    const filtro = this.value.toLowerCase();
    document.querySelectorAll('#tablaFacturacion tbody tr').forEach(function (fila) {
        fila.style.display = fila.textContent.toLowerCase().includes(filtro) ? '' : 'none';
    });
});
document.querySelectorAll('.js-tipo-credito').forEach(function (selector) {
    selector.addEventListener('change', function () {
        const contenedor = document.querySelector(this.dataset.importe);
        const input = contenedor.querySelector('input[name="importe"]');
        const parcial = this.value === 'false';
        contenedor.style.display = parcial ? '' : 'none';
        input.required = parcial;
        if (!parcial) input.value = '';
    });
});
</script>
</body>
</html>

<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html><html lang="es"><head><jsp:include page="/WEB-INF/jsp/head.jsp"/></head>
<body id="page-top"><div id="wrapper"><jsp:include page="/WEB-INF/jsp/nav_bar.jsp"/>
<div id="content-wrapper" class="d-flex flex-column"><div class="container-fluid py-4">
    <div class="d-flex justify-content-between"><div><h1 class="h3 text-gray-800">Construir guía de talles</h1><p class="text-muted"><c:out value="${constructor.nombre}"/> — <c:out value="${constructor.contexto.categoriaNombre}"/></p></div>
        <a class="btn btn-secondary align-self-start" href="${pageContext.request.contextPath}/guias-talles?productoId=${constructor.contexto.producto.id}">Volver</a></div>
    <c:if test="${not empty error}"><div class="alert alert-danger"><c:out value="${error}"/></div></c:if>
    <form method="post" action="${pageContext.request.contextPath}/guias-talles/crear">
        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
        <input type="hidden" name="productoId" value="${constructor.contexto.producto.id}"/>
        <input type="hidden" name="nombre" value="${constructor.nombre}"/>
        <input type="hidden" name="tipoMedida" value="${constructor.tipoMedida}"/>
        <c:forEach items="${constructor.filtros}" var="entrada">
            <input type="hidden" name="filtro_${entrada.key}" value="${entrada.value.id}|||${entrada.value.nombre}"/>
        </c:forEach>
        <div class="card shadow mb-4"><div class="card-header"><strong>Configuración</strong></div><div class="card-body row">
            <div class="col-md-4 mb-3"><label>Talle principal *</label><select name="atributoPrincipal" class="form-control" required><option value="">Seleccionar</option>
                <c:forEach items="${constructor.camposFila}" var="campo"><c:if test="${campo.candidatoPrincipal}"><option value="${campo.id}" <c:if test="${atributoPrincipalSeleccionado == campo.id}">selected</c:if>><c:out value="${campo.nombre}"/></option></c:if></c:forEach>
            </select><small class="text-muted">Es la columna que verá el comprador al elegir el talle.</small></div>
            <c:forEach items="${constructor.camposGenerales}" var="campo"><div class="col-md-4 mb-3"><label><c:out value="${campo.nombre}"/><c:if test="${campo.obligatorio}"> *</c:if></label>
                <c:choose><c:when test="${not empty campo.valores}"><select name="general_${campo.id}" class="form-control" <c:if test="${campo.obligatorio}">required</c:if>><option value="">Seleccionar</option>
                    <c:forEach items="${campo.valores}" var="opcion"><option value="${opcion.id}|||${opcion.nombre}"><c:out value="${opcion.nombre}"/></option></c:forEach></select></c:when>
                    <c:otherwise><input name="general_${campo.id}" class="form-control" <c:if test="${campo.obligatorio}">required</c:if>></c:otherwise></c:choose>
            </div></c:forEach>
        </div></div>
        <div class="card shadow mb-4"><div class="card-header d-flex justify-content-between align-items-center"><strong>Filas de la guía</strong>
            <button type="button" class="btn btn-sm btn-outline-primary" id="agregarFila">Agregar talle</button></div>
            <div class="card-body"><p class="text-muted">Completá una fila por talle. En las medidas numéricas podés escribir, por ejemplo, <em>48 cm</em>.</p>
                <div class="table-responsive"><table class="table table-bordered"><thead><tr><th>#</th>
                    <c:forEach items="${constructor.camposFila}" var="campo"><th><c:choose><c:when test="${campo.id == 'FILTRABLE_SIZE'}">Equivalencia Mercado Libre</c:when><c:otherwise><c:out value="${campo.nombre}"/></c:otherwise></c:choose><c:if test="${campo.obligatorio}"> *</c:if><c:if test="${not empty campo.unidad}"><br><small>(<c:out value="${campo.unidad}"/>)</small></c:if></th></c:forEach><th></th></tr></thead>
                    <tbody id="filas"><tr class="guia-row"><td class="numero-fila">1</td>
                        <c:forEach items="${constructor.camposFila}" var="campo"><td>
                            <c:choose><c:when test="${campo.id == 'FILTRABLE_SIZE'}">
                                <label class="small mb-1">Desde</label><select name="fila_0_${campo.id}" class="form-control mb-2" required><option value="">Seleccionar</option>
                                    <c:forEach items="${campo.valores}" var="opcion"><option value="${opcion.id}|||${opcion.nombre}"><c:out value="${opcion.nombre}"/></option></c:forEach></select>
                                <label class="small mb-1">Hasta (opcional)</label><select name="fila_0_${campo.id}" class="form-control"><option value="">Mismo talle</option>
                                    <c:forEach items="${campo.valores}" var="opcion"><option value="${opcion.id}|||${opcion.nombre}"><c:out value="${opcion.nombre}"/></option></c:forEach></select>
                            </c:when><c:when test="${not empty campo.valores}"><select name="fila_0_${campo.id}" class="form-control" <c:if test="${campo.multivalor}">multiple</c:if> <c:if test="${campo.obligatorio}">required</c:if>><option value="">Seleccionar</option>
                                <c:forEach items="${campo.valores}" var="opcion"><option value="${opcion.id}|||${opcion.nombre}"><c:out value="${opcion.nombre}"/></option></c:forEach></select></c:when>
                                <c:otherwise><input name="fila_0_${campo.id}" class="form-control" <c:if test="${campo.obligatorio}">required</c:if> placeholder="${campo.unidad}"></c:otherwise></c:choose>
                            <c:if test="${campo.id == 'FILTRABLE_SIZE'}"><small class="text-muted">Si no es un rango, dejá Hasta sin seleccionar.</small></c:if>
                        </td></c:forEach><td><button type="button" class="btn btn-sm btn-danger quitar-fila">×</button></td></tr></tbody>
                </table></div>
            </div>
        </div>
        <button class="btn btn-success btn-lg">Crear y asignar guía</button>
    </form>
</div></div></div>
<script>
(function(){
 const cuerpo=document.getElementById('filas');
 function renumerar(){[...cuerpo.querySelectorAll('.guia-row')].forEach((fila,i)=>{fila.querySelector('.numero-fila').textContent=i+1; fila.querySelectorAll('[name]').forEach(c=>c.name=c.name.replace(/fila_\d+_/, 'fila_'+i+'_'));});}
 document.getElementById('agregarFila').addEventListener('click',()=>{const fila=cuerpo.querySelector('.guia-row').cloneNode(true); fila.querySelectorAll('input').forEach(x=>x.value=''); fila.querySelectorAll('select').forEach(x=>x.selectedIndex=0); cuerpo.appendChild(fila); renumerar();});
 cuerpo.addEventListener('click',e=>{if(e.target.classList.contains('quitar-fila')&&cuerpo.children.length>1){e.target.closest('.guia-row').remove();renumerar();}});
})();
</script></body></html>

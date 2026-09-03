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

                <!-- Título -->
                <h1 class="h3 mb-4 text-gray-800 mt-4">
                    <c:choose>
                        <c:when test="${producto.id != null}">
                            Editar Producto
                        </c:when>
                        <c:otherwise>
                            Nuevo Producto
                        </c:otherwise>
                    </c:choose>
                </h1>

                <!-- Card -->
                <div class="card shadow mb-4">
                    <div class="card-body">

                        <!-- Acción del form -->
                        <c:choose>
                            <c:when test="${producto.id != null}">
                                <c:url var="formAction" value="/productos/actualizar/${producto.id}"/>
                            </c:when>
                            <c:otherwise>
                                <c:url var="formAction" value="/productos/guardar"/>
                            </c:otherwise>
                        </c:choose>

                        <form method="post" action="${formAction}" enctype="multipart/form-data">
                        <input type="hidden" name="fotoUrlExterna" value="${producto.fotoUrlExterna}">
                        <textarea name="wooCommerceAtributosJson" hidden><c:out value="${producto.wooCommerceAtributosJson}"/></textarea>
                        <input type="hidden"
                                   name="${_csrf.parameterName}"
                                   value="${_csrf.token}"/>

                            <div class="row">

                            <div class="col-md-6 mb-3">
                                                                <label>Proveedor</label>

                                                                <div class="input-group">
                                                                    <input type="text"
                                                                           class="form-control"
                                                                           value="${producto.proveedor.nombreRazonSocial}"
                                                                           readonly>

                                                                    <input type="hidden" name="proveedorId"
                                                                           value="${producto.proveedor.id}">

                                                                    <a href="${pageContext.request.contextPath}/proveedores?origen=producto&productoId=${producto.id}"
                                                                       class="btn btn-primary ml-3">
                                                                        Buscar Proveedor
                                                                    </a>
                                                                </div>
                                                            </div>

                                <div class="col-md-6 mb-3">
                                    <label>Descripción</label>
                                    <input type="text" name="descripcion"
                                           class="form-control"
                                           value="${producto.descripcion}" required>
                                </div>

                                <div class="col-md-6 mb-3">
                                    <label>Título para Tiendanube</label>
                                    <input type="text" name="tiendaNubeTitulo"
                                           class="form-control"
                                           value="${producto.tiendaNubeTitulo}"
                                           placeholder="Si se deja vacío, se usa la descripción general">
                                </div>

                                <div class="col-md-6 mb-3">
                                    <label>Título para WooCommerce</label>
                                    <input type="text" name="wooCommerceTitulo"
                                           class="form-control"
                                           value="${producto.wooCommerceTitulo}"
                                           placeholder="Si se deja vacío, se usa la descripción general">
                                </div>

                                <div class="col-12 mb-3">
                                    <label>Descripción para WooCommerce</label>
                                    <textarea name="wooCommerceDescripcion" class="form-control" rows="3"
                                              placeholder="Si se deja vacía, se usa la descripción general"><c:out value="${producto.wooCommerceDescripcion}"/></textarea>
                                </div>

                                <div class="col-12 mb-3">
                                    <div class="alert alert-light border mb-0">
                                        El stock, los precios y las características se completan en Presentaciones y variantes después de guardar.
                                        Una sola presentación corresponde a un producto simple.
                                    </div>
                                </div>
                            </div>
                            <div class="mb-3">
                                <label class="form-label">Tipo IVA</label>

                                <select name="tipoIva" class="form-select" required>

                                    <c:forEach items="${tiposIva}" var="iva" varStatus="status">
                                        <option value="${iva}"
                                            <c:if test="${producto.tipoIva == iva or status.first}">
                                                selected
                                            </c:if>
                                        >
                                            ${iva.descripcion}
                                        </option>
                                    </c:forEach>

                                </select>
                            </div>
                            <div class="mb-3">
                                <label class="form-label">Foto del producto</label>
                                <input type="file" name="foto" class="form-control"
                                       accept="image/png,image/jpeg,image/webp,image/gif">
                                <small class="form-text text-muted">JPG, PNG, WebP o GIF. Máximo 5 MB.</small>
                                <c:if test="${producto.id != null && producto.tieneFoto()}">
                                    <div class="mt-3">
                                        <img src="${pageContext.request.contextPath}/productos/${producto.id}/foto"
                                             alt="Foto de ${producto.descripcion}"
                                             style="width:140px;height:140px;object-fit:contain;background:#fff;border-radius:8px;">
                                        <div class="small text-muted mt-1">La foto actual se conserva si no selecciona otra.</div>
                                    </div>
                                </c:if>
                            </div>
                            <details class="card border-left-primary shadow-sm mt-4 mb-3">
                                <summary class="card-header bg-white py-3" style="cursor:pointer;">
                                    <span class="font-weight-bold text-primary">Configurar publicación para Mercado Libre</span>
                                    <span class="small text-muted ml-2">Abrir opciones</span>
                                </summary>
                                <div class="card-body">
                            <div class="mb-3">
                                <label class="form-label">Título para Mercado Libre</label>
                                <input type="text" name="mercadoLibreTitulo" class="form-control"
                                       value="${producto.mercadoLibreTitulo}"
                                       placeholder="Si se deja vacío, se usa el título general">
                            </div>
                            <div class="mb-3">
                                <label class="form-label">Categoría de Mercado Libre</label>
                                <input type="text" name="mercadoLibreCategoriaId" class="form-control"
                                       value="${producto.mercadoLibreCategoriaId}" placeholder="Dejar vacío para detectar automáticamente">
                                <small class="form-text text-muted">Si se deja vacía o no permite publicar, Mercado Libre sugerirá una categoría final según la descripción.</small>
                            </div>
                            <div class="row">
                                <div class="col-md-6 mb-3">
                                    <label class="form-label">Guía de talles de Mercado Libre</label>
                                    <input type="text" name="mercadoLibreGuiaTallesId" class="form-control" readonly
                                           value="${producto.mercadoLibreGuiaTallesId}" placeholder="Sin guía asignada">
                                    <input type="hidden" name="mercadoLibreGuiaTallesFilaId" value="${producto.mercadoLibreGuiaTallesFilaId}">
                                    <small class="form-text text-muted">La guía se construye y asigna desde la sección Guías de talles.</small>
                                    <c:if test="${not empty producto.id}"><a class="btn btn-sm btn-outline-primary mt-2" href="${pageContext.request.contextPath}/guias-talles?productoId=${producto.id}">Administrar guía</a></c:if>
                                </div>
                                <div class="col-md-6 mb-3">
                                    <label class="form-label">Género</label>
                                    <select name="mercadoLibreGenero" class="form-control">
                                        <option value="">Seleccionar cuando corresponda</option>
                                        <option value="Hombre" <c:if test="${producto.mercadoLibreGenero == 'Hombre'}">selected</c:if>>Hombre</option>
                                        <option value="Mujer" <c:if test="${producto.mercadoLibreGenero == 'Mujer'}">selected</c:if>>Mujer</option>
                                        <option value="Niños" <c:if test="${producto.mercadoLibreGenero == 'Niños'}">selected</c:if>>Niños</option>
                                        <option value="Niñas" <c:if test="${producto.mercadoLibreGenero == 'Niñas'}">selected</c:if>>Niñas</option>
                                        <option value="Bebés" <c:if test="${producto.mercadoLibreGenero == 'Bebés'}">selected</c:if>>Bebés</option>
                                        <option value="Sin género" <c:if test="${producto.mercadoLibreGenero == 'Sin género'}">selected</c:if>>Sin género</option>
                                    </select>
                                </div>
                                <div class="col-md-4 mb-3">
                                    <label class="form-label">ID de Tienda Oficial</label>
                                    <input type="number" min="1" name="mercadoLibreOfficialStoreId" class="form-control"
                                           value="${producto.mercadoLibreOfficialStoreId}" placeholder="Ej.: 1">
                                    <small class="form-text text-muted">Dejar vacío si no corresponde.</small>
                                </div>
                                <div class="col-12 mb-3">
                                    <small class="form-text text-muted">Marca, modelo, datos propios de la categoría, GTIN y motivo de GTIN vacío se completan en Presentaciones y variantes.</small>
                                </div>
                                <div class="col-md-4 mb-3">
                                    <label class="form-label">Tipo de garantía</label>
                                    <input type="text" name="mercadoLibreGarantiaTipo" class="form-control"
                                           value="${producto.mercadoLibreGarantiaTipo}" placeholder="Garantía del vendedor">
                                </div>
                                <div class="col-md-4 mb-3">
                                    <label class="form-label">Tiempo de garantía</label>
                                    <input type="text" name="mercadoLibreGarantiaTiempo" class="form-control"
                                           value="${producto.mercadoLibreGarantiaTiempo}" placeholder="90 días">
                                </div>
                                <div class="col-md-4 mb-3">
                                    <label class="form-label">Condición</label>
                                    <select name="mercadoLibreCondicion" class="form-control">
                                        <option value="">Usar Nuevo</option>
                                        <option value="new" <c:if test="${producto.mercadoLibreCondicion == 'new'}">selected</c:if>>Nuevo</option>
                                        <option value="used" <c:if test="${producto.mercadoLibreCondicion == 'used'}">selected</c:if>>Usado</option>
                                        <option value="refurbished" <c:if test="${producto.mercadoLibreCondicion == 'refurbished'}">selected</c:if>>Reacondicionado</option>
                                    </select>
                                </div>
                                <div class="col-md-4 mb-3">
                                    <label class="form-label">Estado de la publicación</label>
                                    <select name="mercadoLibreEstado" class="form-control">
                                        <option value="">No modificar</option>
                                        <option value="active" <c:if test="${producto.mercadoLibreEstado == 'active'}">selected</c:if>>Activa</option>
                                        <option value="paused" <c:if test="${producto.mercadoLibreEstado == 'paused'}">selected</c:if>>Pausada</option>
                                        <option value="closed" <c:if test="${producto.mercadoLibreEstado == 'closed'}">selected</c:if>>Cerrada (sólo informativo)</option>
                                    </select>
                                </div>
                                <div class="col-md-4 mb-3">
                                    <label class="form-label">Tiempo de disponibilidad</label>
                                    <input type="number" min="0" name="mercadoLibreTiempoDisponibilidad" class="form-control"
                                           value="${producto.mercadoLibreTiempoDisponibilidad}" placeholder="Días">
                                </div>
                                <div class="col-md-4 mb-3">
                                    <label class="form-label">Modo de envío</label>
                                    <select name="mercadoLibreModoEnvio" class="form-control">
                                        <option value="">Automático</option>
                                        <option value="me2" <c:if test="${producto.mercadoLibreModoEnvio == 'me2'}">selected</c:if>>Mercado Envíos</option>
                                        <option value="not_specified" <c:if test="${producto.mercadoLibreModoEnvio == 'not_specified'}">selected</c:if>>Acordar con el vendedor</option>
                                    </select>
                                </div>
                                <div class="col-md-4 mb-3">
                                    <label class="form-label">Tipo de publicación</label>
                                    <select name="mercadoLibreListingTypeId" class="form-control">
                                        <option value="">Usar configuración general</option>
                                        <option value="gold_special" <c:if test="${producto.mercadoLibreListingTypeId == 'gold_special'}">selected</c:if>>Clásica</option>
                                        <option value="gold_pro" <c:if test="${producto.mercadoLibreListingTypeId == 'gold_pro'}">selected</c:if>>Premium</option>
                                        <option value="free" <c:if test="${producto.mercadoLibreListingTypeId == 'free'}">selected</c:if>>Gratuita</option>
                                    </select>
                                </div>
                                <div class="col-md-6 mb-3">
                                    <label class="form-label">ID de video</label>
                                    <input type="text" name="mercadoLibreVideoId" class="form-control"
                                           value="${producto.mercadoLibreVideoId}">
                                    <small class="form-text text-muted">Dejar vacío si no hay video.</small>
                                </div>
                                <div class="col-md-6 mb-3 d-flex align-items-center">
                                    <input type="hidden" name="_mercadoLibreEnvioGratis" value="on">
                                    <div class="form-check mt-3">
                                        <input type="checkbox" name="mercadoLibreEnvioGratis" value="true"
                                               id="mercadoLibreEnvioGratis" class="form-check-input"
                                               <c:if test="${producto.mercadoLibreEnvioGratis}">checked</c:if>>
                                        <label for="mercadoLibreEnvioGratis" class="form-check-label">Ofrecer envío gratis</label>
                                    </div>
                                </div>
                                <div class="col-md-6 mb-3 d-flex align-items-center">
                                    <input type="hidden" name="_mercadoLibreRetiroPersonal" value="on">
                                    <div class="form-check mt-3">
                                        <input type="checkbox" name="mercadoLibreRetiroPersonal" value="true"
                                               id="mercadoLibreRetiroPersonal" class="form-check-input"
                                               <c:if test="${producto.mercadoLibreRetiroPersonal}">checked</c:if>>
                                        <label for="mercadoLibreRetiroPersonal" class="form-check-label">Permitir retiro en persona</label>
                                    </div>
                                </div>
                                <c:if test="${not empty producto.mercadoLibreConfiguracionCuotas || not empty producto.mercadoLibreCargoVenta || not empty producto.mercadoLibreCostoFinanciacion}">
                                    <div class="col-12 mb-3">
                                        <div class="alert alert-light border mb-0">
                                            <strong>Información comercial del archivo:</strong>
                                            <c:if test="${not empty producto.mercadoLibreConfiguracionCuotas}"> Cuotas: <c:out value="${producto.mercadoLibreConfiguracionCuotas}"/>.</c:if>
                                            <c:if test="${not empty producto.mercadoLibreCargoVenta}"> Cargo por venta: <c:out value="${producto.mercadoLibreCargoVenta}"/>.</c:if>
                                            <c:if test="${not empty producto.mercadoLibreCostoFinanciacion}"> Financiación: <c:out value="${producto.mercadoLibreCostoFinanciacion}"/>.</c:if>
                                        </div>
                                    </div>
                                </c:if>
                            </div>
                            <div class="mb-3">
                                <label class="form-label">Descripción de la publicación</label>
                                <textarea name="mercadoLibreDescripcion" class="form-control" rows="5"
                                          placeholder="Descripción en texto plano">${producto.mercadoLibreDescripcion}</textarea>
                            </div>
                            <div class="mb-3">
                                <label class="form-label">Fotos externas adicionales</label>
                                <textarea name="fotosUrlsExternas" class="form-control" rows="4"
                                          placeholder="https://ejemplo.com/foto-1.jpg&#10;https://ejemplo.com/foto-2.jpg">${producto.fotosUrlsExternas}</textarea>
                                <small class="form-text text-muted">Una URL pública por línea. Se admiten hasta 12 junto con la foto principal.</small>
                            </div>
                                </div>
                            </details>
                            <!-- Botones -->
                            <div class="mt-4">
                                <button type="submit" class="btn btn-success"
                                        onclick="this.disabled=true; this.form.submit();">
                                    Guardar
                                </button>

                                <a href="<c:url value='/productos'/>"
                                   class="btn btn-secondary">
                                    Cancelar
                                </a>
                            </div>

                        </form>

                    </div>
                </div>

            </div>
        </div>

        <!-- Footer -->
        <footer class="sticky-footer bg-white">
            <div class="container my-auto">
                <div class="copyright text-center my-auto">
                    <span>© Sistema Stock</span>
                </div>
            </div>
        </footer>

    </div>
</div>

</body>
</html>

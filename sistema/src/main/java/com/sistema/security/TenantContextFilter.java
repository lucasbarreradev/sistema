package com.sistema.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sistema.service.MercadoLibreTokenService;
import com.sistema.service.TenantPublicResourceService;
import com.sistema.service.TiendanubeCredencialesService;
import com.sistema.service.WooCommerceCredencialesService;
import com.sistema.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TenantContextFilter extends OncePerRequestFilter {
    private static final Pattern FOTO_PUBLICA_PRODUCTO = Pattern.compile(
            "^/productos/(\\d+)/(?:foto(?:/[^/]+)?|fotos/\\d+/woocommerce\\.jpg"
                    + "|variantes/\\d+/foto(?:/[^/]+)?)$");

    private final ObjectMapper objectMapper;
    private final MercadoLibreTokenService mercadoLibreTokenService;
    private final WooCommerceCredencialesService wooCredenciales;
    private final TiendanubeCredencialesService tiendaNubeCredenciales;
    private final TenantPublicResourceService tenantPublicResourceService;

    public TenantContextFilter(ObjectMapper objectMapper,
                               MercadoLibreTokenService mercadoLibreTokenService,
                               WooCommerceCredencialesService wooCredenciales,
                               TiendanubeCredencialesService tiendaNubeCredenciales,
                               TenantPublicResourceService tenantPublicResourceService) {
        this.objectMapper = objectMapper;
        this.mercadoLibreTokenService = mercadoLibreTokenService;
        this.wooCredenciales = wooCredenciales;
        this.tiendaNubeCredenciales = tiendaNubeCredenciales;
        this.tenantPublicResourceService = tenantPublicResourceService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        HttpServletRequest requestProcesable = request;
        Long tenantId = tenantSesion(request);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof TenantUserDetails usuario) {
            if (tenantId != null && !tenantId.equals(usuario.getTenantId())) {
                request.getSession(false).invalidate();
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
            tenantId = usuario.getTenantId();
        }

        if (tenantId == null && ("GET".equalsIgnoreCase(request.getMethod())
                || "HEAD".equalsIgnoreCase(request.getMethod()))) {
            tenantId = resolverTenantFotoPublica(request.getServletPath());
        }

        boolean callbackMercadoLibreAnterior = "/canales/mercadolibre/callback"
                .equals(request.getServletPath());
        if (tenantId == null && "POST".equalsIgnoreCase(request.getMethod())
                && (request.getServletPath().startsWith("/webhooks/")
                || callbackMercadoLibreAnterior)) {
            if (request.getServletPath().endsWith("/woocommerce")) {
                tenantId = wooCredenciales.resolverTenantPorUrl(request.getHeader("X-WC-Webhook-Source"));
            } else {
                CachedBodyRequest bodyRequest = new CachedBodyRequest(request);
                requestProcesable = bodyRequest;
                tenantId = resolverTenantWebhook(request.getServletPath(), bodyRequest.getCachedBody());
            }
        }

        if (tenantId == null) {
            filterChain.doFilter(requestProcesable, response);
            return;
        }
        try (TenantContext.Scope ignored = TenantContext.use(tenantId)) {
            filterChain.doFilter(requestProcesable, response);
        }
    }

    private Long tenantSesion(HttpServletRequest request) {
        if (request.getSession(false) == null) return null;
        Object valor = request.getSession(false).getAttribute(TenantContext.SESSION_ATTRIBUTE);
        if (valor instanceof Long id) return id;
        if (valor instanceof Number numero) return numero.longValue();
        return null;
    }

    private Long resolverTenantWebhook(String path, byte[] cuerpo) {
        try {
            JsonNode aviso = objectMapper.readTree(cuerpo);
            if (path.endsWith("/mercadolibre")
                    || "/canales/mercadolibre/callback".equals(path)) {
                return mercadoLibreTokenService.resolverTenantPorUsuario(
                        aviso.path("user_id").canConvertToLong() ? aviso.path("user_id").asLong() : null);
            }
            if (path.endsWith("/tiendanube")) {
                return tiendaNubeCredenciales.resolverTenantPorStoreId(aviso.path("store_id").asText());
            }
        } catch (Exception ignored) {
            // El controlador informa el error si el cuerpo recibido no es JSON válido.
        }
        return null;
    }

    private Long resolverTenantFotoPublica(String path) {
        Matcher coincidencia = FOTO_PUBLICA_PRODUCTO.matcher(path == null ? "" : path);
        if (!coincidencia.matches()) return null;
        try {
            return tenantPublicResourceService.buscarTenantProducto(
                    Long.parseLong(coincidencia.group(1))).orElse(null);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static final class CachedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] cachedBody;

        private CachedBodyRequest(HttpServletRequest request) throws IOException {
            super(request);
            cachedBody = request.getInputStream().readAllBytes();
        }

        private byte[] getCachedBody() {
            return cachedBody;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(cachedBody);
            return new ServletInputStream() {
                @Override public boolean isFinished() { return input.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener readListener) {}
                @Override public int read() { return input.read(); }
                @Override public int read(byte[] b, int off, int len) { return input.read(b, off, len); }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }

        @Override public int getContentLength() { return cachedBody.length; }
        @Override public long getContentLengthLong() { return cachedBody.length; }
    }
}

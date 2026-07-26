package com.sistema.security;

import com.sistema.tenant.TenantContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class TenantAuthenticationSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {
    public TenantAuthenticationSuccessHandler() {
        setDefaultTargetUrl("/");
        setAlwaysUseDefaultTargetUrl(true);
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        if (!(authentication.getPrincipal() instanceof TenantUserDetails usuario)) {
            throw new ServletException("El usuario autenticado no tiene tenant");
        }
        request.getSession(true).setAttribute(TenantContext.SESSION_ATTRIBUTE, usuario.getTenantId());
        request.getSession().setAttribute("TENANT_NOMBRE", usuario.getTenantNombre());
        super.onAuthenticationSuccess(request, response, authentication);
    }
}

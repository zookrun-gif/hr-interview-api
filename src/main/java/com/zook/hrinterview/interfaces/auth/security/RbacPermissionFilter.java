package com.zook.hrinterview.interfaces.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zook.hrinterview.common.ApiResponse;
import com.zook.hrinterview.common.ErrorCode;
import com.zook.hrinterview.interfaces.auth.service.RbacPermissionService;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.annotation.Resource;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 60)
public class RbacPermissionFilter extends OncePerRequestFilter {

    private static final Map<String, String> API_PERMISSION_MAP = new LinkedHashMap<>();

    static {
        API_PERMISSION_MAP.put("/api/jobs/create", "job:create");
        API_PERMISSION_MAP.put("/api/jobs/update", "job:update");
        API_PERMISSION_MAP.put("/api/jobs/delete", "job:delete");
        API_PERMISSION_MAP.put("/api/jobs/detail", "menu:jobs");
        API_PERMISSION_MAP.put("/api/jobs/list", "menu:jobs");

        API_PERMISSION_MAP.put("/api/candidates/create", "candidate:create");
        API_PERMISSION_MAP.put("/api/candidates/update", "candidate:update");
        API_PERMISSION_MAP.put("/api/candidates/delete", "candidate:delete");
        API_PERMISSION_MAP.put("/api/candidates/detail", "menu:candidates");
        API_PERMISSION_MAP.put("/api/candidates/list", "menu:candidates");
        API_PERMISSION_MAP.put("/api/candidates/resume/parse-pdf", "menu:candidates");

        API_PERMISSION_MAP.put("/api/interviews/create", "interview:create");
        API_PERMISSION_MAP.put("/api/interviews/detail", "menu:interviews");
        API_PERMISSION_MAP.put("/api/interviews/list", "menu:interviews");
        API_PERMISSION_MAP.put("/api/interviews/access-code/reset", "menu:interviews");
        API_PERMISSION_MAP.put("/api/interviews/messages/list", "menu:interviews");
        API_PERMISSION_MAP.put("/api/interviews/reports/detail", "menu:interviews");
        API_PERMISSION_MAP.put("/api/interviews/reports/list", "menu:interviews");

        API_PERMISSION_MAP.put("/api/rbac/roles/list", "menu:rbac:roles");
        API_PERMISSION_MAP.put("/api/rbac/roles/create", "rbac:role:create");
        API_PERMISSION_MAP.put("/api/rbac/roles/update", "rbac:role:update");
        API_PERMISSION_MAP.put("/api/rbac/roles/delete", "rbac:role:delete");
        API_PERMISSION_MAP.put("/api/rbac/permissions/list", "menu:rbac");
        API_PERMISSION_MAP.put("/api/rbac/permissions/create", "rbac:permission:create");
        API_PERMISSION_MAP.put("/api/rbac/permissions/update", "rbac:permission:update");
        API_PERMISSION_MAP.put("/api/rbac/permissions/delete", "rbac:permission:delete");
        API_PERMISSION_MAP.put("/api/rbac/roles/permissions/detail", "menu:rbac:roles");
        API_PERMISSION_MAP.put("/api/rbac/roles/permissions/save", "rbac:role-permission:save");
        API_PERMISSION_MAP.put("/api/rbac/users/list", "menu:rbac:users");
        API_PERMISSION_MAP.put("/api/rbac/users/create", "rbac:user:create");
        API_PERMISSION_MAP.put("/api/rbac/users/password/reset", "rbac:user-password:reset");
        API_PERMISSION_MAP.put("/api/rbac/users/roles/save", "rbac:user-role:save");
    }

    @Resource
    private RbacPermissionService rbacPermissionService;

    @Resource
    private ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri == null
                || !uri.startsWith("/api/")
                || uri.startsWith("/api/public/")
                || "/api/auth/login".equals(uri)
                || "/api/auth/wecom/config".equals(uri)
                || "/api/auth/wecom/login".equals(uri);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String permissionCode = API_PERMISSION_MAP.get(request.getRequestURI());
        if (permissionCode != null && !rbacPermissionService.hasPermission(LoginUserContext.mustGet(), permissionCode)) {
            writeForbiddenResponse(response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void writeForbiddenResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(
                ApiResponse.failure(ErrorCode.FORBIDDEN.getCode(), ErrorCode.FORBIDDEN.getMessage())
        ));
    }
}

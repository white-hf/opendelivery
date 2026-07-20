package com.hf.easydelivery.config;

import com.hf.easydelivery.common.exception.ForbiddenException;
import com.hf.easydelivery.common.exception.UnauthorizedException;
import com.hf.easydelivery.operations.auth.OperatorSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

@Component
@Profile("!memory")
public class OperationsAuthInterceptor implements HandlerInterceptor {
    private final OperatorSessionService sessions;
    private final ApiKeyVerifier apiKeys;
    private final JdbcTemplate jdbc;
    private final boolean legacyApiKeyEnabled;

    public OperationsAuthInterceptor(OperatorSessionService sessions, ApiKeyVerifier apiKeys, JdbcTemplate jdbc,
                                     @Value("${opendelivery.security.legacy-ops-api-key-enabled:true}") boolean legacyApiKeyEnabled) {
        this.sessions = sessions;
        this.apiKeys = apiKeys;
        this.jdbc = jdbc;
        this.legacyApiKeyEnabled = legacyApiKeyEnabled;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (legacyApiKeyEnabled && request.getHeader("X-Ops-Api-Key") != null) {
            apiKeys.requireOperations(request.getHeader("X-Ops-Api-Key"));
            request.setAttribute("legacyOpsApiKey", true);
            return true;
        }
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) throw new UnauthorizedException("Operator bearer token is required");
        OperatorSessionService.Principal principal = sessions.authenticate(header.substring(7));
        Long stationId;
        try {
            requirePermission(principal, request.getMethod(), request.getRequestURI());
            stationId = resolveStation(principal, request.getHeader("X-Station-Code"));
        } catch (ForbiddenException ex) {
            auditDenied(request, principal, ex.getMessage());
            throw ex;
        }
        request.setAttribute("operatorPrincipal", principal);
        request.setAttribute("operatorUserId", principal.userId());
        request.setAttribute("operatorStationId", stationId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        if ("GET".equals(request.getMethod()) || Boolean.TRUE.equals(request.getAttribute("legacyOpsApiKey"))) return;
        Object userId = request.getAttribute("operatorUserId");
        if (!(userId instanceof Long)) return;
        jdbc.update("""
                INSERT INTO operation_audit_log
                  (operator_user_id,station_id,action_code,resource_type,resource_id,outcome,request_id,detail_json)
                VALUES (?,?,?,?,?,?,?,JSON_OBJECT('method',?,'path',?))
                """, userId, request.getAttribute("operatorStationId"), "HTTP_" + request.getMethod(),
                resourceType(request.getRequestURI()), resourceId(request.getRequestURI()),
                ex == null && response.getStatus() < 400 ? "SUCCESS" : "FAILED",
                request.getHeader("X-Request-Id"), request.getMethod(), request.getRequestURI());
    }

    private String resourceType(String path) {
        String[] parts = path.split("/");
        return parts.length > 3 ? parts[3].toUpperCase() : "OPERATIONS";
    }

    private String resourceId(String path) {
        String[] parts = path.split("/");
        return parts.length > 4 ? parts[4] : null;
    }

    private void requirePermission(OperatorSessionService.Principal principal, String method, String path) {
        if (path.contains("/users") && !principal.hasRole("ADMIN")) {
            throw new ForbiddenException("Only administrators can manage operator users");
        }
        if (principal.hasRole("ADMIN") || principal.hasRole("SUPERVISOR")) return;
        boolean read = "GET".equals(method);
        boolean commonRead = read && (path.endsWith("/stations") || path.contains("/cases") || path.endsWith("/readiness"));
        boolean allowed = principal.hasRole("INBOUND") && (path.contains("/manifests") || commonRead)
                || principal.hasRole("DISPATCHER") && (path.contains("/waves") || commonRead);
        if (!allowed) throw new ForbiddenException("Role is not permitted to perform this operation");
    }

    private void auditDenied(HttpServletRequest request, OperatorSessionService.Principal principal, String reason) {
        jdbc.update("""
                INSERT INTO operation_audit_log
                  (operator_user_id,station_id,action_code,resource_type,resource_id,outcome,request_id,detail_json)
                VALUES (?,?,?,?,?,'DENIED',?,JSON_OBJECT('reason',?,'method',?,'path',?))
                """, principal.userId(), principal.stationId(), "HTTP_" + request.getMethod(),
                resourceType(request.getRequestURI()), resourceId(request.getRequestURI()),
                request.getHeader("X-Request-Id"), reason, request.getMethod(), request.getRequestURI());
    }

    private Long resolveStation(OperatorSessionService.Principal principal, String requestedCode) {
        if (requestedCode == null || requestedCode.isBlank()) return principal.stationId();
        List<Long> stationIds = jdbc.query("SELECT id FROM station WHERE station_code=? AND status='ACTIVE'",
                (rs,n)->rs.getLong(1), requestedCode.toUpperCase());
        if (stationIds.isEmpty()) throw new ForbiddenException("Requested station is not active");
        if (!principal.hasRole("ADMIN") && !stationIds.get(0).equals(principal.stationId())) {
            throw new ForbiddenException("Operator cannot switch to another station");
        }
        return stationIds.get(0);
    }
}

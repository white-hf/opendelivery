package com.hf.easydelivery.config;

import java.util.List;

final class OperationsAuthorizationPolicy {
    private OperationsAuthorizationPolicy() {}

    static boolean isAllowed(List<String> roles, String method, String path) {
        if (path.contains("/users")) return roles.contains("ADMIN");
        if (roles.contains("ADMIN") || roles.contains("SUPERVISOR")) return true;
        boolean read = "GET".equals(method);
        boolean commonRead = read && (path.endsWith("/stations") || path.contains("/cases")
                || path.endsWith("/readiness"));
        return roles.contains("INBOUND") && (path.contains("/manifests") || commonRead)
                || roles.contains("DISPATCHER") && (path.contains("/waves")
                || path.contains("/dispatch/") || commonRead);
    }
}

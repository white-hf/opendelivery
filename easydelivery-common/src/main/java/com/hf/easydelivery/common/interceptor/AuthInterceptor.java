package com.hf.easydelivery.common.interceptor;

import com.hf.easydelivery.common.exception.UnauthorizedException;
import com.hf.easydelivery.common.store.TokenStore;
import com.hf.easydelivery.common.util.JwtHelper;
import com.hf.easydelivery.common.repository.DriverRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.context.i18n.LocaleContextHolder;
import com.hf.easydelivery.common.i18n.SupportedLocale;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final TokenStore tokenStore;
    private final DriverRepository driverRepository;

    public AuthInterceptor(TokenStore tokenStore, DriverRepository driverRepository) {
        this.tokenStore = tokenStore;
        this.driverRepository = driverRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = request.getRequestURI();
        // Skip authentication routes and error pages
        if (path.contains("/auth/login") || path.contains("/auth/register") || path.contains("/auth/refresh")
                || path.startsWith("/integration/v1/") || path.startsWith("/ops/v1/")
                || path.startsWith("/ops/auth/") || path.contains("/error")) {
            return true;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            // Defensively support lowercase header used by Volley client requests
            authHeader = request.getHeader("authorization");
        }

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new UnauthorizedException("Missing or invalid Authorization header");
        }

        String token = authHeader.substring(7);

        // Step 1: Stateless Cryptographic Validation (check expiry, signature)
        String driverId = JwtHelper.verifyAccessToken(token);
        if (driverId == null) {
            throw new UnauthorizedException("Session has expired or token is invalid");
        }

        // Step 2: Stateful Blacklist check (check if user logged out)
        if (!tokenStore.validateAccessToken(token)) {
            throw new UnauthorizedException("Session has been terminated");
        }

        // Set verified context attributes
        com.hf.easydelivery.common.model.Driver driver = driverRepository.findByCredentialId(driverId)
                .orElseThrow(() -> new UnauthorizedException("Driver account no longer exists"));
        Integer numericDriverId=driver.getId();
        if(request.getHeader("Accept-Language")==null) LocaleContextHolder.setLocale(SupportedLocale.locale(driver.getPreferredLocale()));
        request.setAttribute("driverCredentialId", driverId);
        request.setAttribute("driverId", numericDriverId);

        return true;
    }

    @Override public void afterCompletion(HttpServletRequest request,HttpServletResponse response,Object handler,Exception ex) {
        LocaleContextHolder.resetLocaleContext();
    }
}

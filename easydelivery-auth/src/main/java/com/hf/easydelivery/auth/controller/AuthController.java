package com.hf.easydelivery.auth.controller;

import com.hf.easydelivery.auth.model.AppLoginReq;
import com.hf.easydelivery.auth.model.AppRefreshReq;
import com.hf.easydelivery.auth.model.AppRegisterReq;
import com.hf.easydelivery.common.exception.UnauthorizedException;
import com.hf.easydelivery.common.response.AppResponse;
import com.hf.easydelivery.common.store.TokenStore;
import com.hf.easydelivery.common.repository.DriverRepository;
import com.hf.easydelivery.common.model.Driver;
import com.hf.easydelivery.common.util.JwtHelper;
import org.springframework.security.crypto.bcrypt.BCrypt;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import com.hf.easydelivery.common.i18n.SupportedLocale;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final TokenStore tokenStore;
    private final DriverRepository driverRepository;

    public AuthController(TokenStore tokenStore, DriverRepository driverRepository) {
        this.tokenStore = tokenStore;
        this.driverRepository = driverRepository;
    }

    @PostMapping("/register")
    public AppResponse<Void> register(@jakarta.validation.Valid @RequestBody AppRegisterReq req) {
        if (driverRepository.existsByCredentialId(req.getCredential_id())) {
            return AppResponse.fail("AUTH.USER.EXISTS", "Driver credential already exists");
        }

        // Salt and Hash password using BCrypt
        String hashedPassword = BCrypt.hashpw(req.getPassword(), BCrypt.gensalt());

        Driver newDriver = new Driver(
                0, // ID generator inside repository will allocate a new numeric ID
                req.getCredential_id(),
                hashedPassword,
                req.getName(),
                "ACTIVE"
        );

        driverRepository.save(newDriver);
        return AppResponse.success("Driver registered successfully", null);
    }

    @PostMapping("/login")
    public AppResponse<Map<String, String>> login(@jakarta.validation.Valid @RequestBody AppLoginReq req) {
        Driver driver = driverRepository.findByCredentialId(req.getCredential_id()).orElse(null);
        if (driver != null && driver.isActive() && BCrypt.checkpw(req.getPassword(), driver.getPasswordHash())) {
            // Generate a secure JWT Access Token (lifespan 2 hours)
            String accessToken = JwtHelper.generateAccessToken(req.getCredential_id(), 7200 * 1000L);
            String refreshToken = "rt-" + UUID.randomUUID().toString().replace("-", "");

            tokenStore.storeTokens(req.getCredential_id(), accessToken, refreshToken);

            Map<String, String> data = new HashMap<>();
            data.put("token_type", "Bearer");
            data.put("access_token", accessToken);
            data.put("refresh_token", refreshToken);
            data.put("expires_in", "7200");

            return AppResponse.success("Login Success", data);
        } else {
            return AppResponse.fail("AUTH.INVALID.CREDENTIALS", "Invalid username or password");
        }
    }

    @PostMapping("/refresh")
    public AppResponse<Map<String, String>> refresh(@jakarta.validation.Valid @RequestBody AppRefreshReq req) {
        String driverId = tokenStore.validateRefreshToken(req.getRefresh_token());
        if (driverId == null) {
            throw new UnauthorizedException("Invalid or expired refresh token");
        }

        // Generate a new secure JWT Access Token (lifespan 2 hours)
        String newAccessToken = JwtHelper.generateAccessToken(driverId, 7200 * 1000L);
        String newRefreshToken = "rt-" + UUID.randomUUID().toString().replace("-", "");

        tokenStore.rotateRefreshToken(req.getRefresh_token(), newAccessToken, newRefreshToken);

        Map<String, String> data = new HashMap<>();
        data.put("token_type", "Bearer");
        data.put("access_token", newAccessToken);
        data.put("refresh_token", newRefreshToken);
        data.put("expires_in", "7200");

        return AppResponse.success("Token refreshed", data);
    }

    @PostMapping("/logout")
    public AppResponse<Void> logout(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null) {
            authHeader = request.getHeader("authorization");
        }

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String accessToken = authHeader.substring(7);
            tokenStore.revokeTokens(accessToken);
        }

        return AppResponse.success("Logout successful", null);
    }

    @PutMapping("/locale")
    public AppResponse<?> updateLocale(HttpServletRequest request,@RequestBody LocaleRequest body) {
        Object driverId=request.getAttribute("driverId");
        if(!(driverId instanceof Integer id)) throw new UnauthorizedException("Driver authentication is required");
        String locale=SupportedLocale.canonicalTag(body.locale());
        driverRepository.updatePreferredLocale(id,locale);
        return AppResponse.success(Map.of("preferred_locale",locale));
    }

    public record LocaleRequest(String locale) {}
}

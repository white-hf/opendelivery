package com.hf.easydelivery.common.store;

import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
@Profile("memory")
public class InMemoryTokenStore implements TokenStore {

    private final ConcurrentMap<String, String> accessTokenMap = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> refreshTokenMap = new ConcurrentHashMap<>();
    
    // Reverse mappings to safely revoke old sessions when a driver re-authenticates
    private final ConcurrentMap<String, String> driverAccessTokenMap = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> driverRefreshTokenMap = new ConcurrentHashMap<>();

    @Override
    public synchronized void storeTokens(String driverId, String accessToken, String refreshToken) {
        // Enforce single-session by clearing previous active tokens
        String oldAccess = driverAccessTokenMap.remove(driverId);
        if (oldAccess != null) {
            accessTokenMap.remove(oldAccess);
        }
        String oldRefresh = driverRefreshTokenMap.remove(driverId);
        if (oldRefresh != null) {
            refreshTokenMap.remove(oldRefresh);
        }

        accessTokenMap.put(accessToken, driverId);
        refreshTokenMap.put(refreshToken, driverId);
        driverAccessTokenMap.put(driverId, accessToken);
        driverRefreshTokenMap.put(driverId, refreshToken);
    }

    @Override
    public boolean validateAccessToken(String accessToken) {
        return accessTokenMap.containsKey(accessToken);
    }

    @Override
    public String validateRefreshToken(String refreshToken) {
        return refreshTokenMap.get(refreshToken);
    }

    @Override
    public synchronized void rotateRefreshToken(String oldRefreshToken, String newAccessToken, String newRefreshToken) {
        String driverId = refreshTokenMap.remove(oldRefreshToken);
        if (driverId != null) {
            String oldAccess = driverAccessTokenMap.remove(driverId);
            if (oldAccess != null) {
                accessTokenMap.remove(oldAccess);
            }
            
            accessTokenMap.put(newAccessToken, driverId);
            refreshTokenMap.put(newRefreshToken, driverId);
            driverAccessTokenMap.put(driverId, newAccessToken);
            driverRefreshTokenMap.put(driverId, newRefreshToken);
        }
    }

    @Override
    public synchronized void revokeTokens(String accessToken) {
        String driverId = accessTokenMap.remove(accessToken);
        if (driverId != null) {
            driverAccessTokenMap.remove(driverId);
            String refreshToken = driverRefreshTokenMap.remove(driverId);
            if (refreshToken != null) {
                refreshTokenMap.remove(refreshToken);
            }
        }
    }
}

package com.hf.easydelivery.common.store;

public interface TokenStore {
    /**
     * Store Access Token and Refresh Token mapped to a driver ID.
     */
    void storeTokens(String driverId, String accessToken, String refreshToken);

    /**
     * Validate whether the access token is valid and active.
     */
    boolean validateAccessToken(String accessToken);

    /**
     * Validate whether the refresh token is valid and return the associated driver ID.
     * Returns null if invalid or expired.
     */
    String validateRefreshToken(String refreshToken);

    /**
     * Revoke the old refresh token and store the new access/refresh token pair.
     */
    void rotateRefreshToken(String oldRefreshToken, String newAccessToken, String newRefreshToken);

    /**
     * Revoke the access token (and its associated refresh token) upon logout.
     */
    void revokeTokens(String accessToken);
}

package com.hf.easydelivery.common.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;

import java.util.Date;

public class JwtHelper {

    private static final String SECRET_KEY = loadSecret();
    private static final Algorithm ALGORITHM = Algorithm.HMAC256(SECRET_KEY);
    private static final String ISSUER = "easydelivery-auth-service";

    private static String loadSecret() {
        String value = System.getenv("JWT_SECRET");
        if (value == null || value.isBlank()) value = System.getProperty("JWT_SECRET");
        if (value == null || value.length() < 32) {
            throw new IllegalStateException("JWT_SECRET must be explicitly configured with at least 32 characters");
        }
        return value;
    }

    /**
     * Generates a signed JWT Access Token for the given driverId.
     * @param driverId the driver identifier (subject)
     * @param ttlMs time-to-live in milliseconds
     * @return signed JWT string
     */
    public static String generateAccessToken(String driverId, long ttlMs) {
        return JWT.create()
                .withIssuer(ISSUER)
                .withSubject(driverId)
                .withIssuedAt(new Date())
                .withExpiresAt(new Date(System.currentTimeMillis() + ttlMs))
                .sign(ALGORITHM);
    }

    /**
     * Decrypts and cryptographically verifies the token's signature, issuer, and expiration.
     * @param token the access token string
     * @return the driverId (subject) if valid, null otherwise
     */
    public static String verifyAccessToken(String token) {
        try {
            JWTVerifier verifier = JWT.require(ALGORITHM)
                    .withIssuer(ISSUER)
                    .build();
            DecodedJWT jwt = verifier.verify(token);
            return jwt.getSubject();
        } catch (JWTVerificationException exception) {
            return null; // Invalid signature or expired token
        }
    }
}

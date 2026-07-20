package com.hf.easydelivery.common.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class JwtHelperTest {

    @Test
    public void testJwtGenerationAndVerification() {
        String driverId = "driver123";
        long ttlMs = 60000; // 60 seconds
        
        // Generate Token
        String token = JwtHelper.generateAccessToken(driverId, ttlMs);
        assertNotNull(token);
        assertEquals(3, token.split("\\.").length); // JWT format Header.Payload.Signature

        // Verify Token
        String verifiedDriverId = JwtHelper.verifyAccessToken(token);
        assertEquals(driverId, verifiedDriverId);
    }

    @Test
    public void testJwtExpiration() throws InterruptedException {
        String driverId = "driverExp";
        long ttlMs = 100; // 0.1 seconds
        
        String token = JwtHelper.generateAccessToken(driverId, ttlMs);
        assertNotNull(token);

        // Sleep to let it expire
        Thread.sleep(300);

        // Verification should yield null
        String verifiedDriverId = JwtHelper.verifyAccessToken(token);
        assertNull(verifiedDriverId);
    }

    @Test
    public void testJwtTampering() {
        String driverId = "driverTamp";
        String token = JwtHelper.generateAccessToken(driverId, 60000);
        
        // Append characters to invalidate signature check
        String tamperedToken = token + "x";
        String verifiedDriverId = JwtHelper.verifyAccessToken(tamperedToken);
        assertNull(verifiedDriverId);
    }
}

package com.hf.easydelivery.common.store;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class TokenStoreTest {

    @Test
    public void testTokenStoreLifecycle() {
        TokenStore tokenStore = new InMemoryTokenStore();
        String driverId = "driver101";
        String at = "access-token-1";
        String rt = "refresh-token-1";

        // 1. Store tokens
        tokenStore.storeTokens(driverId, at, rt);

        // 2. Validate active tokens
        assertTrue(tokenStore.validateAccessToken(at));
        assertFalse(tokenStore.validateAccessToken("invalid-at"));
        assertEquals(driverId, tokenStore.validateRefreshToken(rt));
        assertNull(tokenStore.validateRefreshToken("invalid-rt"));

        // 3. Rotate refresh token
        String newAt = "access-token-2";
        String newRt = "refresh-token-2";
        tokenStore.rotateRefreshToken(rt, newAt, newRt);

        // 4. Validate old tokens are revoked
        assertFalse(tokenStore.validateAccessToken(at));
        assertNull(tokenStore.validateRefreshToken(rt));

        // 5. Validate new tokens are active
        assertTrue(tokenStore.validateAccessToken(newAt));
        assertEquals(driverId, tokenStore.validateRefreshToken(newRt));

        // 6. Logout and revoke
        tokenStore.revokeTokens(newAt);
        assertFalse(tokenStore.validateAccessToken(newAt));
        assertNull(tokenStore.validateRefreshToken(newRt));
    }
}

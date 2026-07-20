package com.hf.easydelivery.common.store;

import com.auth0.jwt.JWT;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;

@Component
@Profile("!memory")
public class JdbcTokenStore implements TokenStore {
    private final JdbcTemplate jdbc;

    public JdbcTokenStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void storeTokens(String driverCredential, String accessToken, String refreshToken) {
        Long driverId = resolveDriverId(driverCredential);
        jdbc.update("UPDATE auth_session SET revoked_at=CURRENT_TIMESTAMP(3) WHERE driver_id=? AND revoked_at IS NULL", driverId);
        Instant accessExpiry = JWT.decode(accessToken).getExpiresAtAsInstant();
        jdbc.update("""
                INSERT INTO auth_session
                    (driver_id, access_token_hash, refresh_token_hash, access_expires_at, refresh_expires_at)
                VALUES (?, ?, ?, ?, ?)
                """, driverId, hash(accessToken), hash(refreshToken), Timestamp.from(accessExpiry),
                Timestamp.from(Instant.now().plus(30, ChronoUnit.DAYS)));
    }

    @Override
    public boolean validateAccessToken(String accessToken) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM auth_session
                WHERE access_token_hash=? AND revoked_at IS NULL AND access_expires_at > CURRENT_TIMESTAMP(3)
                """, Integer.class, hash(accessToken));
        return count != null && count > 0;
    }

    @Override
    public String validateRefreshToken(String refreshToken) {
        List<String> values = jdbc.query("""
                SELECT d.credential_id FROM auth_session s JOIN driver d ON d.id=s.driver_id
                WHERE s.refresh_token_hash=? AND s.revoked_at IS NULL
                  AND s.refresh_expires_at > CURRENT_TIMESTAMP(3)
                """, (rs, rowNum) -> rs.getString(1), hash(refreshToken));
        return values.stream().findFirst().orElse(null);
    }

    @Override
    @Transactional
    public void rotateRefreshToken(String oldRefreshToken, String newAccessToken, String newRefreshToken) {
        String credential = validateRefreshToken(oldRefreshToken);
        if (credential == null) return;
        jdbc.update("UPDATE auth_session SET revoked_at=CURRENT_TIMESTAMP(3) WHERE refresh_token_hash=?", hash(oldRefreshToken));
        storeTokens(credential, newAccessToken, newRefreshToken);
    }

    @Override
    public void revokeTokens(String accessToken) {
        jdbc.update("UPDATE auth_session SET revoked_at=CURRENT_TIMESTAMP(3) WHERE access_token_hash=? AND revoked_at IS NULL", hash(accessToken));
    }

    private Long resolveDriverId(String credential) {
        List<Long> ids = jdbc.query("SELECT id FROM driver WHERE credential_id=?", (rs, rowNum) -> rs.getLong(1), credential);
        if (ids.isEmpty()) throw new IllegalArgumentException("Unknown driver credential");
        return ids.get(0);
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}

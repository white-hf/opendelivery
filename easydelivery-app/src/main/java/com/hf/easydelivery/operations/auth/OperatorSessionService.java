package com.hf.easydelivery.operations.auth;

import com.hf.easydelivery.common.exception.UnauthorizedException;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
@Profile("!memory")
public class OperatorSessionService {
    private final JdbcTemplate jdbc;

    public OperatorSessionService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public Tokens login(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new UnauthorizedException("Invalid operator username or password");
        }
        List<UserRow> users = jdbc.query("""
                SELECT u.id,u.username,u.password_hash,u.display_name,u.default_station_id,s.station_code,u.preferred_locale,s.default_locale
                FROM operator_user u LEFT JOIN station s ON s.id=u.default_station_id
                WHERE u.username=? AND u.status='ACTIVE'
                """, (rs,n) -> new UserRow(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getString(4),
                rs.getObject(5,Long.class),rs.getString(6),rs.getString(7),rs.getString(8)), username);
        if (users.isEmpty() || !BCrypt.checkpw(password, users.get(0).passwordHash())) {
            throw new UnauthorizedException("Invalid operator username or password");
        }
        return issue(users.get(0).id());
    }

    @Transactional
    public Tokens refresh(String refreshToken) {
        List<Long> users = jdbc.query("""
                SELECT user_id FROM operator_session WHERE refresh_token_hash=? AND revoked_at IS NULL
                  AND refresh_expires_at>CURRENT_TIMESTAMP(3) FOR UPDATE
                """, (rs,n)->rs.getLong(1), sha256(refreshToken));
        if (users.isEmpty()) throw new UnauthorizedException("Refresh token is invalid or expired");
        jdbc.update("UPDATE operator_session SET revoked_at=CURRENT_TIMESTAMP(3) WHERE refresh_token_hash=?", sha256(refreshToken));
        return issue(users.get(0));
    }

    public Principal authenticate(String accessToken) {
        List<Principal> rows = jdbc.query("""
                SELECT u.id,u.username,u.display_name,u.default_station_id,s.station_code,
                       GROUP_CONCAT(r.role_code ORDER BY r.role_code),u.preferred_locale,s.default_locale
                FROM operator_session os JOIN operator_user u ON u.id=os.user_id
                LEFT JOIN station s ON s.id=u.default_station_id
                JOIN operator_user_role ur ON ur.user_id=u.id JOIN operator_role r ON r.id=ur.role_id
                WHERE os.access_token_hash=? AND os.revoked_at IS NULL AND os.access_expires_at>CURRENT_TIMESTAMP(3)
                  AND u.status='ACTIVE'
                GROUP BY u.id,u.username,u.display_name,u.default_station_id,s.station_code,u.preferred_locale,s.default_locale
                """, (rs,n)->new Principal(rs.getLong(1),rs.getString(2),rs.getString(3),
                rs.getObject(4,Long.class),rs.getString(5),List.of(rs.getString(6).split(",")),rs.getString(7),rs.getString(8)), sha256(accessToken));
        if (rows.isEmpty()) throw new UnauthorizedException("Operator session is invalid or expired");
        return rows.get(0);
    }

    public void logout(String accessToken) {
        jdbc.update("UPDATE operator_session SET revoked_at=CURRENT_TIMESTAMP(3) WHERE access_token_hash=? AND revoked_at IS NULL",
                sha256(accessToken));
    }

    public void updateLocale(String accessToken,String locale) {
        Principal principal=authenticate(accessToken);
        jdbc.update("UPDATE operator_user SET preferred_locale=?,version=version+1 WHERE id=?",locale,principal.userId());
    }

    private Tokens issue(long userId) {
        String access = "oa-" + UUID.randomUUID().toString().replace("-", "");
        String refresh = "or-" + UUID.randomUUID().toString().replace("-", "");
        LocalDateTime now = LocalDateTime.now();
        jdbc.update("""
                INSERT INTO operator_session
                  (user_id,access_token_hash,refresh_token_hash,access_expires_at,refresh_expires_at)
                VALUES (?,?,?,?,?)
                """, userId, sha256(access), sha256(refresh), now.plusHours(2), now.plusDays(14));
        return new Tokens("Bearer", access, refresh, 7200);
    }

    private String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }

    private record UserRow(long id,String username,String passwordHash,String displayName,Long stationId,String stationCode,
                           String preferredLocale,String stationDefaultLocale) {}
    public record Tokens(String tokenType,String accessToken,String refreshToken,long expiresIn) {}
    public record Principal(long userId,String username,String displayName,Long stationId,String stationCode,List<String> roles,
                            String preferredLocale,String stationDefaultLocale) {
        public boolean hasRole(String role) { return roles.contains(role); }
    }
}

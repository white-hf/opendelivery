package com.hf.easydelivery.common.repository;

import com.hf.easydelivery.common.model.Driver;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

@Repository
@Profile("!memory")
public class JdbcDriverRepository implements DriverRepository {
    private final JdbcTemplate jdbc;

    public JdbcDriverRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Driver> findByCredentialId(String credentialId) {
        if (credentialId == null) return Optional.empty();
        return first(jdbc.query("""
                SELECT id, credential_id, password_hash, driver_name, status, preferred_locale
                FROM driver WHERE credential_id = ?
                """, (rs, rowNum) -> map(rs.getLong("id"), rs.getString("credential_id"),
                rs.getString("password_hash"), rs.getString("driver_name"), rs.getString("status"),rs.getString("preferred_locale")), credentialId));
    }

    @Override
    public Optional<Driver> findById(int id) {
        return first(jdbc.query("""
                SELECT id, credential_id, password_hash, driver_name, status, preferred_locale
                FROM driver WHERE id = ?
                """, (rs, rowNum) -> map(rs.getLong("id"), rs.getString("credential_id"),
                rs.getString("password_hash"), rs.getString("driver_name"), rs.getString("status"),rs.getString("preferred_locale")), id));
    }

    @Override
    public void save(Driver driver) {
        if (driver.getId() > 0 && findById(driver.getId()).isPresent()) {
            jdbc.update("""
                    UPDATE driver SET credential_id=?, password_hash=?, driver_name=?, status=?, version=version+1
                    WHERE id=?
                    """, driver.getCredentialId(), driver.getPasswordHash(), driver.getName(), driver.getStatus(), driver.getId());
            return;
        }
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO driver (credential_id, password_hash, driver_name, status)
                    VALUES (?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, driver.getCredentialId());
            ps.setString(2, driver.getPasswordHash());
            ps.setString(3, driver.getName());
            ps.setString(4, driver.getStatus());
            return ps;
        }, keys);
        if (keys.getKey() != null) driver.setId(keys.getKey().intValue());
    }

    @Override
    public boolean existsByCredentialId(String credentialId) {
        if (credentialId == null) return false;
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM driver WHERE credential_id=?", Integer.class, credentialId);
        return count != null && count > 0;
    }

    @Override public void updatePreferredLocale(int driverId,String locale) {
        jdbc.update("UPDATE driver SET preferred_locale=?,version=version+1 WHERE id=?",locale,driverId);
    }

    private Optional<Driver> first(List<Driver> drivers) {
        return drivers.stream().findFirst();
    }

    private Driver map(long id, String credentialId, String hash, String name, String status,String preferredLocale) {
        return new Driver(Math.toIntExact(id), credentialId, hash, name, status,preferredLocale);
    }
}

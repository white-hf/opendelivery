package com.hf.easydelivery.operations;

import com.hf.easydelivery.config.OperationsAccess;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@Profile("!memory")
public class SystemConfigOperationsService {
    private final JdbcTemplate jdbc;
    private final OperationsAccess access;

    public SystemConfigOperationsService(JdbcTemplate jdbc, OperationsAccess access) {
        this.jdbc = jdbc;
        this.access = access;
    }

    private long station() {
        Long id = access.selectedStationId();
        if (id == null) throw new com.hf.easydelivery.common.exception.BizException("STATION.CONTEXT.REQUIRED", "Station context is required");
        return id;
    }

    public record DriverCreateRequest(String credentialId, String driverName, String phone, String password) {}
    public record DriverStatusRequest(String status) {}
    public record ServiceAreaCreateRequest(String countryCode, String provinceCode, String cityName, String postalPrefix, Integer priority) {}

    public List<Map<String, Object>> listDrivers(String status) {
        Long stationId = access.selectedStationId();
        if (status != null && !status.isBlank()) {
            return jdbc.queryForList("""
                    SELECT id, id driver_id, credential_id, driver_name, phone, status, created_at
                    FROM driver WHERE (? IS NULL OR home_station_id=?) AND status=? ORDER BY id DESC
                    """, stationId, stationId, status.toUpperCase());
        }
        return jdbc.queryForList("""
                SELECT id, id driver_id, credential_id, driver_name, phone, status, created_at
                FROM driver WHERE (? IS NULL OR home_station_id=?) ORDER BY id DESC
                """, stationId, stationId);
    }

    @Transactional
    public Map<String, Object> createDriver(DriverCreateRequest req) {
        long stationId = station();
        SystemConfigPolicy.validateDriverInput(req.credentialId(), req.driverName());
        
        String rawPassword = (req.password() != null && !req.password().isBlank()) ? req.password() : "password123";
        String passwordHash = BCrypt.hashpw(rawPassword, BCrypt.gensalt());

        jdbc.update("""
                INSERT INTO driver (home_station_id, credential_id, password_hash, driver_name, phone, status)
                VALUES (?, ?, ?, ?, ?, 'ACTIVE')
                """, stationId, req.credentialId().trim(), passwordHash, req.driverName().trim(), req.phone());

        Long newId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return jdbc.queryForMap("SELECT id, credential_id, driver_name, phone, status FROM driver WHERE id=?", newId);
    }

    @Transactional
    public void updateDriverStatus(long driverId, DriverStatusRequest req) {
        long stationId = station();
        String newStatus = (req.status() != null && req.status().equalsIgnoreCase("INACTIVE")) ? "INACTIVE" : "ACTIVE";
        jdbc.update("UPDATE driver SET status=? WHERE id=? AND home_station_id=?", newStatus, driverId, stationId);
    }

    public List<Map<String, Object>> listServiceAreas() {
        Long stationId = access.selectedStationId();
        return jdbc.queryForList("""
                SELECT id, country_code, province_code, city_name, postal_prefix, priority, status, created_at
                FROM station_service_area WHERE (? IS NULL OR station_id=?) ORDER BY priority DESC, id DESC
                """, stationId, stationId);
    }

    @Transactional
    public Map<String, Object> createServiceArea(ServiceAreaCreateRequest req) {
        long stationId = station();
        SystemConfigPolicy.validateServiceAreaInput(req.countryCode(), req.provinceCode(), req.cityName());

        int priority = req.priority() != null ? req.priority() : 100;
        String postalPrefix = (req.postalPrefix() != null && !req.postalPrefix().isBlank()) ? req.postalPrefix().trim().toUpperCase() : null;

        jdbc.update("""
                INSERT INTO station_service_area (station_id, country_code, province_code, city_name, postal_prefix, priority, status)
                VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE')
                """, stationId, req.countryCode().trim().toUpperCase(), req.provinceCode().trim().toUpperCase(), req.cityName().trim().toUpperCase(), postalPrefix, priority);

        Long newId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return jdbc.queryForMap("SELECT id, country_code, province_code, city_name, postal_prefix, priority, status FROM station_service_area WHERE id=?", newId);
    }

    public record StationCreateRequest(String stationCode, String stationName, String city, String provinceCode, String countryCode, String timezone, String addressLine) {}

    @Transactional
    public Map<String, Object> createStation(StationCreateRequest req) {
        String countryCode = (req.countryCode() != null && !req.countryCode().isBlank()) ? req.countryCode().trim().toUpperCase() : "CA";
        String timezone = (req.timezone() != null && !req.timezone().isBlank()) ? req.timezone().trim() : "America/Toronto";
        SystemConfigPolicy.validateStationInput(req.stationCode(), req.stationName(), req.city(), req.provinceCode());

        jdbc.update("""
                INSERT INTO station (station_code, station_name, city, province_code, country_code, timezone, address_line, status)
                VALUES (UPPER(?), ?, UPPER(?), UPPER(?), ?, ?, ?, 'ACTIVE')
                """, req.stationCode().trim(), req.stationName().trim(), req.city().trim(), req.provinceCode().trim(), countryCode, timezone, req.addressLine());

        Long newId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return jdbc.queryForMap("SELECT id, station_code, station_name, city, province_code, country_code, timezone, status FROM station WHERE id=?", newId);
    }
}

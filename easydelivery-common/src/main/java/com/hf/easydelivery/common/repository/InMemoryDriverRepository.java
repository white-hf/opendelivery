package com.hf.easydelivery.common.repository;

import com.hf.easydelivery.common.model.Driver;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Repository;
import org.springframework.context.annotation.Profile;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Repository
@Profile("memory")
public class InMemoryDriverRepository implements DriverRepository {

    private final Map<String, Driver> driversByCred = new ConcurrentHashMap<>();
    private final Map<Integer, Driver> driversById = new ConcurrentHashMap<>();
    private final AtomicInteger idGenerator = new AtomicInteger(100);

    public InMemoryDriverRepository() {
        // Pre-populate with standard drivers (hashed with BCrypt)
        save(new Driver(101, "driver123", BCrypt.hashpw("password123", BCrypt.gensalt()), "Alex Driver", "ACTIVE"));
        save(new Driver(102, "test", BCrypt.hashpw("test", BCrypt.gensalt()), "Test Driver", "ACTIVE"));
    }

    @Override
    public Optional<Driver> findByCredentialId(String credentialId) {
        if (credentialId == null) return Optional.empty();
        return Optional.ofNullable(driversByCred.get(credentialId));
    }

    @Override
    public Optional<Driver> findById(int id) {
        return Optional.ofNullable(driversById.get(id));
    }

    @Override
    public synchronized void save(Driver driver) {
        if (driver == null) return;
        if (driver.getId() <= 0) {
            driver.setId(idGenerator.incrementAndGet());
        } else {
            // Synchronize generator to always exceed manually supplied IDs
            idGenerator.accumulateAndGet(driver.getId(), Math::max);
        }
        driversByCred.put(driver.getCredentialId(), driver);
        driversById.put(driver.getId(), driver);
    }

    @Override
    public boolean existsByCredentialId(String credentialId) {
        if (credentialId == null) return false;
        return driversByCred.containsKey(credentialId);
    }
}

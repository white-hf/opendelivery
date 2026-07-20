package com.hf.easydelivery.common.repository;

import com.hf.easydelivery.common.model.Driver;
import java.util.Optional;

public interface DriverRepository {
    
    /**
     * Resolves a driver profile by their login credential id.
     */
    Optional<Driver> findByCredentialId(String credentialId);

    /**
     * Resolves a driver profile by their internal numeric driver ID.
     */
    Optional<Driver> findById(int id);

    /**
     * Saves or updates a driver profile in the repository.
     */
    void save(Driver driver);

    /**
     * Checks if a credential id already exists in the store.
     */
    boolean existsByCredentialId(String credentialId);

    void updatePreferredLocale(int driverId,String locale);
}

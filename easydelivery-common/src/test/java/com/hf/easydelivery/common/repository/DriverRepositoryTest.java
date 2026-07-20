package com.hf.easydelivery.common.repository;

import com.hf.easydelivery.common.model.Driver;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCrypt;
import static org.junit.jupiter.api.Assertions.*;

public class DriverRepositoryTest {

    @Test
    public void testDriverRegistrationAndValidation() {
        DriverRepository repo = new InMemoryDriverRepository();
        
        // 1. Verify preset driver
        assertTrue(repo.existsByCredentialId("driver123"));
        Driver alex = repo.findByCredentialId("driver123").orElse(null);
        assertNotNull(alex);
        assertEquals("Alex Driver", alex.getName());
        assertTrue(alex.isActive());
        assertTrue(BCrypt.checkpw("password123", alex.getPasswordHash()));

        // 2. Register new driver
        String cred = "new_driver";
        String pw = "secure_password_123";
        String hashed = BCrypt.hashpw(pw, BCrypt.gensalt());
        Driver newDriver = new Driver(0, cred, hashed, "New Driver", "ACTIVE");
        
        assertFalse(repo.existsByCredentialId(cred));
        repo.save(newDriver);
        
        // 3. Verify new driver is saved and searchable
        assertTrue(repo.existsByCredentialId(cred));
        Driver saved = repo.findByCredentialId(cred).orElse(null);
        assertNotNull(saved);
        assertTrue(saved.getId() > 102); // Autoincrement check
        assertEquals("New Driver", saved.getName());
        assertTrue(BCrypt.checkpw(pw, saved.getPasswordHash()));
    }
}

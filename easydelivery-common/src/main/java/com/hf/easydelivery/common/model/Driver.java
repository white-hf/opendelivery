package com.hf.easydelivery.common.model;

public class Driver {
    private int id;
    private String credentialId;
    private String passwordHash; // Stores BCrypt salted hash
    private String name;
    private String status; // e.g. "ACTIVE", "SUSPENDED"
    private String preferredLocale;

    public Driver() {}

    public Driver(int id, String credentialId, String passwordHash, String name, String status) {
        this(id,credentialId,passwordHash,name,status,"en-CA");
    }

    public Driver(int id, String credentialId, String passwordHash, String name, String status, String preferredLocale) {
        this.id = id;
        this.credentialId = credentialId;
        this.passwordHash = passwordHash;
        this.name = name;
        this.status = status;
        this.preferredLocale = preferredLocale;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getCredentialId() {
        return credentialId;
    }

    public void setCredentialId(String credentialId) {
        this.credentialId = credentialId;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isActive() {
        return "ACTIVE".equalsIgnoreCase(this.status);
    }

    public String getPreferredLocale() { return preferredLocale; }
    public void setPreferredLocale(String preferredLocale) { this.preferredLocale=preferredLocale; }
}

package com.hf.easydelivery.auth.model;

import jakarta.validation.constraints.NotBlank;

public class AppLoginReq {
    @NotBlank(message = "Username cannot be empty")
    private String credential_id;

    @NotBlank(message = "Password cannot be empty")
    private String password;

    public String getCredential_id() { return credential_id; }
    public void setCredential_id(String credential_id) { this.credential_id = credential_id; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}

package com.hf.easydelivery.auth.model;

import jakarta.validation.constraints.NotBlank;

public class AppRefreshReq {
    @NotBlank(message = "Refresh token cannot be empty")
    private String refresh_token;

    public String getRefresh_token() { return refresh_token; }
    public void setRefresh_token(String refresh_token) { this.refresh_token = refresh_token; }
}

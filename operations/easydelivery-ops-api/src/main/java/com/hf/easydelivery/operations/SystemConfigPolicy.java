package com.hf.easydelivery.operations;

import com.hf.easydelivery.common.exception.BizException;

public class SystemConfigPolicy {
    public static void validateDriverInput(String credentialId, String driverName) {
        if (credentialId == null || credentialId.isBlank()) {
            throw new BizException("SYSTEM.DRIVER.CREDENTIAL_REQUIRED", "Driver credential ID is required");
        }
        if (driverName == null || driverName.isBlank()) {
            throw new BizException("SYSTEM.DRIVER.NAME_REQUIRED", "Driver name is required");
        }
    }

    public static void validateServiceAreaInput(String countryCode, String provinceCode, String cityName) {
        if (countryCode == null || countryCode.isBlank()) {
            throw new BizException("SYSTEM.SERVICE_AREA.COUNTRY_REQUIRED", "Country code is required");
        }
        if (provinceCode == null || provinceCode.isBlank()) {
            throw new BizException("SYSTEM.SERVICE_AREA.PROVINCE_REQUIRED", "Province code is required");
        }
        if (cityName == null || cityName.isBlank()) {
            throw new BizException("SYSTEM.SERVICE_AREA.CITY_REQUIRED", "City name is required");
        }
    }

    public static void validateStationInput(String stationCode, String stationName, String city, String provinceCode) {
        if (stationCode == null || stationCode.isBlank()) {
            throw new BizException("SYSTEM.STATION.CODE_REQUIRED", "Station code is required");
        }
        if (stationName == null || stationName.isBlank()) {
            throw new BizException("SYSTEM.STATION.NAME_REQUIRED", "Station name is required");
        }
        if (city == null || city.isBlank()) {
            throw new BizException("SYSTEM.STATION.CITY_REQUIRED", "City name is required");
        }
        if (provinceCode == null || provinceCode.isBlank()) {
            throw new BizException("SYSTEM.STATION.PROVINCE_REQUIRED", "Province code is required");
        }
    }
}

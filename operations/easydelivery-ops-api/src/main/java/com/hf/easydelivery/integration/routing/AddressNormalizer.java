package com.hf.easydelivery.integration.routing;

import com.hf.easydelivery.common.exception.BizException;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class AddressNormalizer {
    public NormalizedAddress normalize(String city, String province, String postalCode, String countryCode) {
        String country = normalizeText(countryCode == null || countryCode.isBlank() ? "CA" : countryCode);
        String normalizedCity = normalizeText(city);
        String normalizedProvince = normalizeText(province);
        String postal = postalCode == null ? "" : postalCode.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        if (normalizedCity.isBlank() || normalizedProvince.isBlank() || postal.isBlank()) {
            throw new BizException("ROUTING.ADDRESS.INCOMPLETE", "City, province and postal code are required for routing");
        }
        if ("CA".equals(country) && !postal.matches("[A-Z]\\d[A-Z]\\d[A-Z]\\d")) {
            throw new BizException("ROUTING.POSTAL.INVALID", "Canadian postal code format is invalid");
        }
        return new NormalizedAddress(normalizedCity, normalizedProvince, postal, country);
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    public record NormalizedAddress(String city, String province, String postalCode, String countryCode) {}
}


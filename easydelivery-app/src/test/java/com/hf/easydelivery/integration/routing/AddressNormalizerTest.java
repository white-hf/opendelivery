package com.hf.easydelivery.integration.routing;

import com.hf.easydelivery.common.exception.BizException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AddressNormalizerTest {
    private final AddressNormalizer normalizer = new AddressNormalizer();

    @Test
    void normalizesCanadianRoutingFields() {
        var result = normalizer.normalize("  Halifax ", "ns", "b3j 1z2", null);
        assertEquals("HALIFAX", result.city());
        assertEquals("NS", result.province());
        assertEquals("B3J1Z2", result.postalCode());
        assertEquals("CA", result.countryCode());
    }

    @Test
    void rejectsInvalidCanadianPostalCode() {
        BizException error = assertThrows(BizException.class,
                () -> normalizer.normalize("Halifax", "NS", "123", "CA"));
        assertEquals("ROUTING.POSTAL.INVALID", error.getBizCode());
    }
}

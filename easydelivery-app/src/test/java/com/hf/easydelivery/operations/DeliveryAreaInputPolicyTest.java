package com.hf.easydelivery.operations;

import com.hf.easydelivery.common.exception.BizException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DeliveryAreaInputPolicyTest {
    @Test void acceptsWgs84BoundaryAndConfidenceRange() {
        assertDoesNotThrow(() -> DeliveryAreaInputPolicy.coordinates(-180,90,BigDecimal.ONE));
    }

    @Test void rejectsNonFiniteOrOutOfRangeLocations() {
        assertThrows(BizException.class,() -> DeliveryAreaInputPolicy.coordinates(Double.NaN,44,null));
        assertThrows(BizException.class,() -> DeliveryAreaInputPolicy.coordinates(181,44,null));
        assertThrows(BizException.class,() -> DeliveryAreaInputPolicy.coordinates(-63,91,null));
        assertThrows(BizException.class,() -> DeliveryAreaInputPolicy.coordinates(-63,44,new BigDecimal("1.01")));
    }

    @Test void validatesPreferencePriorityAndDates() {
        LocalDate today=LocalDate.of(2026,7,20);
        assertDoesNotThrow(() -> DeliveryAreaInputPolicy.preference(10,today,today.plusDays(1)));
        assertThrows(BizException.class,() -> DeliveryAreaInputPolicy.preference(0,null,null));
        assertThrows(BizException.class,() -> DeliveryAreaInputPolicy.preference(10,today,today.minusDays(1)));
    }
}

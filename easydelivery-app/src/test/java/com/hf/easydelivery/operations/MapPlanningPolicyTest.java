package com.hf.easydelivery.operations;

import com.hf.easydelivery.common.exception.BizException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MapPlanningPolicyTest {
    @Test void acceptsOrdersBeforeAndAfterPhysicalArrival() {
        assertTrue(MapPlanningPolicy.plannable("RECEIVED"));
        assertTrue(MapPlanningPolicy.plannable("AT_STATION"));
        assertFalse(MapPlanningPolicy.plannable("ASSIGNED"));
        assertFalse(MapPlanningPolicy.plannable("DELIVERED"));
    }

    @Test void rejectsCapacityOverflowWithoutSoftWarning() {
        BizException error=assertThrows(BizException.class,()->MapPlanningPolicy.capacity(20,6,25));
        assertEquals("DRIVER.CAPACITY.EXCEEDED",error.getBizCode());
        assertDoesNotThrow(()->MapPlanningPolicy.capacity(20,5,25));
    }

    @Test void onlyDraftIsEditableAndShiftMustBeAvailable() {
        assertDoesNotThrow(()->MapPlanningPolicy.editable("DRAFT"));
        assertThrows(BizException.class,()->MapPlanningPolicy.editable("FROZEN"));
        assertThrows(BizException.class,()->MapPlanningPolicy.available("UNAVAILABLE"));
    }
}

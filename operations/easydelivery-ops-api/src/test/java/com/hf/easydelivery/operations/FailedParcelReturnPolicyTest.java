package com.hf.easydelivery.operations;

import com.hf.easydelivery.common.exception.BizException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FailedParcelReturnPolicyTest {
    @Test
    void acceptsFailedParcelHeldByItsTaskDriver() {
        assertDoesNotThrow(() -> FailedParcelReturnPolicy.requireReceivable(
                "DELIVERY_FAILED", "DRIVER", 11L, 21L, 31L, 31L));
    }

    @Test
    void recognizesIdempotentStationReceipt() {
        assertTrue(FailedParcelReturnPolicy.isAlreadyReceived("RETURNED_TO_STATION", "STATION"));
        assertFalse(FailedParcelReturnPolicy.isAlreadyReceived("DELIVERY_FAILED", "DRIVER"));
    }

    @Test
    void rejectsWrongStateAndMissingFailedTaskItem() {
        BizException wrongState = assertThrows(BizException.class, () -> FailedParcelReturnPolicy.requireReceivable(
                "OUT_FOR_DELIVERY", "DRIVER", 11L, 21L, 31L, 31L));
        assertEquals("RETURN.PARCEL.NOT_RECEIVABLE", wrongState.getBizCode());
        assertThrows(BizException.class, () -> FailedParcelReturnPolicy.requireReceivable(
                "DELIVERY_FAILED", "DRIVER", null, 21L, 31L, 31L));
    }

    @Test
    void rejectsCustodyThatDoesNotMatchTaskDriver() {
        BizException mismatch = assertThrows(BizException.class, () -> FailedParcelReturnPolicy.requireReceivable(
                "DELIVERY_FAILED", "DRIVER", 11L, 21L, 31L, 32L));
        assertEquals("RETURN.CUSTODY.MISMATCH", mismatch.getBizCode());
    }
}

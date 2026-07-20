package com.hf.easydelivery.operations;

import com.hf.easydelivery.common.exception.BizException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InboundDiscrepancyPolicyTest {
    @Test
    void acceptsOnlyTheSafeResolutionForEachReceiptStatus() {
        assertEquals("CONFIRM_MISSING", InboundDiscrepancyPolicy.validate("MISSING", "confirm_missing", "Upstream confirmed short"));
        assertEquals("QUARANTINE", InboundDiscrepancyPolicy.validate("EXTRA", "QUARANTINE", "Awaiting upstream data"));
        assertEquals("QUARANTINE", InboundDiscrepancyPolicy.validate("WRONG_STATION", "QUARANTINE", "Held in exception cage"));
        assertEquals("ACCEPT_DAMAGED", InboundDiscrepancyPolicy.validate("DAMAGED", "ACCEPT_DAMAGED", "Damage photographed"));
    }

    @Test
    void rejectsAResolutionThatWouldMisstateInventory() {
        BizException error = assertThrows(BizException.class,
                () -> InboundDiscrepancyPolicy.validate("EXTRA", "ACCEPT_DAMAGED", "wrong action"));
        assertEquals("MANIFEST.DECISION.INVALID", error.getBizCode());
    }

    @Test
    void requiresAnOperationalReason() {
        BizException error = assertThrows(BizException.class,
                () -> InboundDiscrepancyPolicy.validate("MISSING", "CONFIRM_MISSING", " "));
        assertEquals("MANIFEST.DECISION.REASON.REQUIRED", error.getBizCode());
    }
}

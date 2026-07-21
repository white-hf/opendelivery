package com.hf.easydelivery.operations.reconciliation;

import com.hf.easydelivery.operations.reconciliation.service.ScanSupervisionPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScanSupervisionPolicyTest {

    @Test
    void calculatesMissingCorrectly() {
        assertEquals(5, ScanSupervisionPolicy.calculateMissing(10, 5));
        assertEquals(0, ScanSupervisionPolicy.calculateMissing(10, 10));
        assertEquals(0, ScanSupervisionPolicy.calculateMissing(5, 10)); // overflow scan
    }
}

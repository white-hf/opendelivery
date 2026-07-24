package com.hf.easydelivery.operations.reconciliation.service;

public final class ScanSupervisionPolicy {

    private ScanSupervisionPolicy() {}

    /**
     * Calculates missing parcel count: expected (ASSIGNED) minus valid (EXPECTED去重).
     * Missing count cannot be negative.
     */
    public static int calculateMissing(int expectedCount, int validCount) {
        return Math.max(0, expectedCount - validCount);
    }
}

package com.hf.easydelivery.operations;

import com.hf.easydelivery.common.exception.BizException;

import java.util.List;

final class MapPlanningPolicy {
    private MapPlanningPolicy() {}

    static void editable(String status) {
        if (!"DRAFT".equals(status)) throw new BizException("WAVE.STATE.INVALID", "Only a draft wave can be edited");
    }

    static void available(String status) {
        if (!"AVAILABLE".equals(status)) throw new BizException("DRIVER.SHIFT.UNAVAILABLE", "Driver has no available shift for the service date");
    }

    static void capacity(int assigned, int requested, int capacity) {
        if (requested < 1) throw new BizException("ASSIGNMENT.EMPTY", "No eligible parcels were selected");
        if (assigned + requested > capacity) throw new BizException("DRIVER.CAPACITY.EXCEEDED", "Assignment exceeds driver capacity by " + (assigned + requested - capacity) + " parcels");
    }

    static boolean plannable(String parcelStatus) {
        return List.of("RECEIVED", "AT_STATION", "SORTED", "READY_FOR_DISPATCH").contains(parcelStatus);
    }
}

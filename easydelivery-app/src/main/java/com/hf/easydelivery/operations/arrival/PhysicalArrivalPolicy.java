package com.hf.easydelivery.operations.arrival;

import java.util.Map;
import java.util.Set;

final class PhysicalArrivalPolicy {
    private static final Map<String, Set<String>> TRIP = Map.of(
            "EXPECTED", Set.of("ARRIVED", "CANCELLED"),
            "ARRIVED", Set.of("UNLOADING"),
            "UNLOADING", Set.of("READY_FOR_SCAN"),
            "READY_FOR_SCAN", Set.of("CLOSED"));
    private static final Map<String, Set<String>> UNIT = Map.of(
            "EXPECTED", Set.of("ARRIVED"), "ARRIVED", Set.of("OPENED"), "OPENED", Set.of("CLEARED"));

    private PhysicalArrivalPolicy() {}

    static boolean canMoveTrip(String from, String to) { return TRIP.getOrDefault(from, Set.of()).contains(to); }
    static boolean canMoveUnit(String from, String to) { return UNIT.getOrDefault(from, Set.of()).contains(to); }
}

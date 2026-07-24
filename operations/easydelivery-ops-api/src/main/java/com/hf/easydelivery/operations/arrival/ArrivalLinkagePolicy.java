package com.hf.easydelivery.operations.arrival;

import com.hf.easydelivery.common.exception.BizException;
import com.hf.easydelivery.integration.ingestion.CanonicalShipmentRequest;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure rules for arrival-batch numbering and upstream unit-label mapping.
 * Kept free of JDBC so the behavior stays unit-testable; callers enforce station scope.
 */
public final class ArrivalLinkagePolicy {
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final List<String> UNIT_TYPES = List.of("PALLET", "CAGE", "BAG", "LOOSE");
    public static final int DEFAULT_UNIT_COUNT = 10;

    private ArrivalLinkagePolicy() {
    }

    public static String batchNo(String stationCode, LocalDate serviceDate, long sequence) {
        if (sequence < 1 || sequence > 99) {
            throw new BizException("ARRIVAL.TRIP.EXISTS", "Cannot allocate an arrival batch number for the day");
        }
        return stationCode + "-" + DAY.format(serviceDate) + "-" + String.format("%02d", sequence);
    }

    public static List<String> defaultUnitNumbers(String batchNo, int count) {
        List<String> numbers = new ArrayList<>();
        for (int index = 1; index <= count; index++) {
            numbers.add(batchNo + "-U" + String.format("%02d", index));
        }
        return numbers;
    }

    /**
     * Maps tracking numbers to their upstream-declared unit label. The main
     * {@code trackingNumbers} list stays the parcel truth: labels are applied only to
     * trackings present there, extras declared inside units are ignored.
     */
    public static Map<String, String> unitLabelByTracking(CanonicalShipmentRequest request) {
        Map<String, String> labels = new LinkedHashMap<>();
        if (request.handlingUnits() == null) {
            return labels;
        }
        for (CanonicalShipmentRequest.UnitDeclaration unit : request.handlingUnits()) {
            String unitNo = unit.externalUnitNo() == null ? "" : unit.externalUnitNo().trim();
            if (unitNo.isEmpty()) {
                throw new BizException("PARAM.INVALID", "handlingUnits.externalUnitNo is required");
            }
            if (unit.unitType() != null && !UNIT_TYPES.contains(unit.unitType().toUpperCase())) {
                throw new BizException("PARAM.INVALID", "Unsupported handlingUnits.unitType: " + unit.unitType());
            }
            if (unit.trackingNumbers() == null) {
                continue;
            }
            for (String tracking : unit.trackingNumbers()) {
                if (tracking != null && request.trackingNumbers().contains(tracking)) {
                    labels.put(tracking, unitNo);
                }
            }
        }
        return labels;
    }
}

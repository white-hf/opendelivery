package com.hf.easydelivery.operations.arrival;

import com.hf.easydelivery.common.exception.BizException;
import com.hf.easydelivery.integration.ingestion.CanonicalShipmentRequest;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ArrivalLinkagePolicyTest {

    @Test
    void batchNoUsesStationDateAndTwoDigitSequence() {
        assertEquals("YHZ-01-20260721-01", ArrivalLinkagePolicy.batchNo("YHZ-01", LocalDate.of(2026, 7, 21), 1));
        assertEquals("YYZ-01-20260721-12", ArrivalLinkagePolicy.batchNo("YYZ-01", LocalDate.of(2026, 7, 21), 12));
    }

    @Test
    void batchNoRejectsSequenceBeyondDailyCapacity() {
        assertThrows(BizException.class, () -> ArrivalLinkagePolicy.batchNo("YHZ-01", LocalDate.now(), 100));
    }

    @Test
    void defaultUnitNumbersDeriveFromBatchNo() {
        List<String> numbers = ArrivalLinkagePolicy.defaultUnitNumbers("YHZ-01-20260721-01", 10);
        assertEquals(10, numbers.size());
        assertEquals("YHZ-01-20260721-01-U01", numbers.get(0));
        assertEquals("YHZ-01-20260721-01-U10", numbers.get(9));
    }

    @Test
    void unitLabelsApplyOnlyToDeclaredTruthTrackings() {
        CanonicalShipmentRequest request = request(List.of(
                new CanonicalShipmentRequest.UnitDeclaration("PLT-1", "PALLET", List.of("PKG-1", "PKG-2", "PKG-GHOST")),
                new CanonicalShipmentRequest.UnitDeclaration("PLT-2", null, List.of("PKG-3"))));
        Map<String, String> labels = ArrivalLinkagePolicy.unitLabelByTracking(request);
        assertEquals(Map.of("PKG-1", "PLT-1", "PKG-2", "PLT-1", "PKG-3", "PLT-2"), labels);
        assertFalse(labels.containsKey("PKG-GHOST"));
    }

    @Test
    void unitLabelsAreEmptyWhenNoHandlingUnitsDeclared() {
        assertTrue(ArrivalLinkagePolicy.unitLabelByTracking(request(null)).isEmpty());
    }

    @Test
    void unitDeclarationRejectsBlankUnitNoAndUnknownType() {
        assertThrows(BizException.class, () -> ArrivalLinkagePolicy.unitLabelByTracking(request(List.of(
                new CanonicalShipmentRequest.UnitDeclaration(" ", "PALLET", List.of("PKG-1"))))));
        assertThrows(BizException.class, () -> ArrivalLinkagePolicy.unitLabelByTracking(request(List.of(
                new CanonicalShipmentRequest.UnitDeclaration("PLT-1", "TRUCK", List.of("PKG-1"))))));
    }

    private CanonicalShipmentRequest request(List<CanonicalShipmentRequest.UnitDeclaration> units) {
        return new CanonicalShipmentRequest("evt-1", "wb-1", null, "Recipient", null, "1 Main St", null,
                "Halifax", "NS", "B3H1A1", "CA", "STANDARD", null, null, null, "YHZ-01", null,
                List.of("PKG-1", "PKG-2", "PKG-3"), null, null, units);
    }
}

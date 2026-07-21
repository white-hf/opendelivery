package com.hf.easydelivery.operations.arrival;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PhysicalArrivalPolicyTest {
    @Test void tripFollowsPhysicalArrivalSequence(){assertTrue(PhysicalArrivalPolicy.canMoveTrip("EXPECTED","ARRIVED"));assertTrue(PhysicalArrivalPolicy.canMoveTrip("READY_FOR_SCAN","CLOSED"));assertFalse(PhysicalArrivalPolicy.canMoveTrip("EXPECTED","CLOSED"));}
    @Test void handlingUnitCannotSkipOpening(){assertTrue(PhysicalArrivalPolicy.canMoveUnit("ARRIVED","OPENED"));assertFalse(PhysicalArrivalPolicy.canMoveUnit("ARRIVED","CLEARED"));}
}

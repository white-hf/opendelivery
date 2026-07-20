package com.hf.easydelivery.operations;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ControlTowerPolicyTest {
    @Test void blockerOverridesProgress(){assertEquals("BLOCKED",ControlTowerPolicy.stageStatus(72,66,6));}
    @Test void distinguishesNotStartedProgressAndComplete(){assertEquals("NOT_STARTED",ControlTowerPolicy.stageStatus(72,0,0));assertEquals("IN_PROGRESS",ControlTowerPolicy.stageStatus(72,20,0));assertEquals("COMPLETED",ControlTowerPolicy.stageStatus(72,72,0));}
    @Test void percentIsSafeAndCapped(){assertEquals(0,ControlTowerPolicy.percent(0,0));assertEquals(92,ControlTowerPolicy.percent(72,66));assertEquals(100,ControlTowerPolicy.percent(2,3));}
}

package com.hf.easydelivery.operations;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ControlTowerPolicyTest {
    @Test void blockerOverridesProgress(){assertEquals("BLOCKED",ControlTowerPolicy.stageStatus(72,66,6));}
    @Test void distinguishesNotStartedProgressAndComplete(){assertEquals("NOT_STARTED",ControlTowerPolicy.stageStatus(72,0,0));assertEquals("IN_PROGRESS",ControlTowerPolicy.stageStatus(72,20,0));assertEquals("COMPLETED",ControlTowerPolicy.stageStatus(72,72,0));}
    @Test void percentIsSafeAndCapped(){assertEquals(0,ControlTowerPolicy.percent(0,0));assertEquals(92,ControlTowerPolicy.percent(72,66));assertEquals(100,ControlTowerPolicy.percent(2,3));}
    @Test void calculatesActualSphCorrectly(){assertEquals(20.0,ControlTowerPolicy.calculateActualSph(80,4.0));assertEquals(7.5,ControlTowerPolicy.calculateActualSph(30,4.0));assertEquals(0.0,ControlTowerPolicy.calculateActualSph(0,0.0));}
    @Test void calculatesEfficiencyVarianceAndStatus(){assertEquals(2.5,ControlTowerPolicy.calculateEfficiencyVariance(20.5,20.0));assertEquals(-37.5,ControlTowerPolicy.calculateEfficiencyVariance(7.5,12.0));assertEquals("NORMAL",ControlTowerPolicy.evaluateSupervisionStatus(4.0,82,2.5));assertEquals("LAGGING",ControlTowerPolicy.evaluateSupervisionStatus(4.0,31,-37.5));assertEquals("STALLED",ControlTowerPolicy.evaluateSupervisionStatus(2.5,0,-100.0));}
}

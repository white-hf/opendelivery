package com.hf.easydelivery.operations;

final class ControlTowerPolicy {
    private ControlTowerPolicy() {}
    static String stageStatus(int total,int completed,int blockers){
        if(blockers>0)return "BLOCKED";
        if(completed==0)return "NOT_STARTED";
        return completed>=total?"COMPLETED":"IN_PROGRESS";
    }
    static int percent(int total,int completed){return total==0?0:Math.min(100,(int)Math.round(completed*100.0/total));}

    static double calculateActualSph(int totalAttempts, double activeHours) {
        if (activeHours <= 0.0) return 0.0;
        return Math.round((totalAttempts / activeHours) * 10.0) / 10.0;
    }

    static double calculateEfficiencyVariance(double actualSph, double baselineSph) {
        if (baselineSph <= 0.0) return 0.0;
        return Math.round(((actualSph - baselineSph) / baselineSph * 100.0) * 10.0) / 10.0;
    }

    static String evaluateSupervisionStatus(double activeHours, int totalAttempts, double variancePercent) {
        if (activeHours >= 2.0 && totalAttempts == 0) return "STALLED";
        if (variancePercent < -25.0) return "LAGGING";
        return "NORMAL";
    }
}


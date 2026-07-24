package com.hf.easydelivery.operations;

final class ControlTowerPolicy {
    private ControlTowerPolicy() {}
    static String stageStatus(int total,int completed,int blockers){
        if(blockers>0)return "BLOCKED";
        if(completed==0)return "NOT_STARTED";
        return completed>=total?"COMPLETED":"IN_PROGRESS";
    }
    static int percent(int total,int completed){return total==0?0:Math.min(100,(int)Math.round(completed*100.0/total));}
}

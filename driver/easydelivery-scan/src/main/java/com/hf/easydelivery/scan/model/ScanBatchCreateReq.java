package com.hf.easydelivery.scan.model;

public class ScanBatchCreateReq {
    private int driver_id;
    private int operator_role;
    private int scan_as;

    public int getDriver_id() { return driver_id; }
    public void setDriver_id(int driver_id) { this.driver_id = driver_id; }

    public int getOperator_role() { return operator_role; }
    public void setOperator_role(int operator_role) { this.operator_role = operator_role; }

    public int getScan_as() { return scan_as; }
    public void setScan_as(int scan_as) { this.scan_as = scan_as; }
}

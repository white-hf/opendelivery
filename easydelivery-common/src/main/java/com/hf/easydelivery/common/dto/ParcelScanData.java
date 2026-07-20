package com.hf.easydelivery.common.dto;

public class ParcelScanData {
    private long orderId;
    private String trackingNo;
    private int routeNo;

    public long getOrderId() { return orderId; }
    public void setOrderId(long orderId) { this.orderId = orderId; }

    public String getTrackingNo() { return trackingNo; }
    public void setTrackingNo(String trackingNo) { this.trackingNo = trackingNo; }

    public int getRouteNo() { return routeNo; }
    public void setRouteNo(int routeNo) { this.routeNo = routeNo; }
}

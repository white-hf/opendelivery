package com.hf.easydelivery.scan.model;

public class ParcelScanReq {
    private String tracking_no;
    private Long scan_batch_id;
    private String device_event_id;

    public String getTracking_no() { return tracking_no; }
    public void setTracking_no(String tracking_no) { this.tracking_no = tracking_no; }

    public Long getScan_batch_id() { return scan_batch_id; }
    public void setScan_batch_id(Long scan_batch_id) { this.scan_batch_id = scan_batch_id; }

    public String getDevice_event_id() { return device_event_id; }
    public void setDevice_event_id(String device_event_id) { this.device_event_id = device_event_id; }
}

package com.hf.easydelivery.common.dto;

public class ScanBatchReviewData {
    private String status;

    public ScanBatchReviewData() {}

    public ScanBatchReviewData(String status) {
        this.status = status;
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}

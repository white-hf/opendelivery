package com.hf.easydelivery.common.store;

import com.hf.easydelivery.common.dto.DeliveringListData;

import java.util.List;

public interface DeliveryOperations {
    List<DeliveringListData> getUnscannedParcels(int driverId);
    List<DeliveringListData> getDeliveringParcels(int driverId);
    DeliveringListData getParcelByTrackingNo(String trackingNo);
    DeliveringListData getParcelByOrderId(long orderId);
    void updateParcelState(long orderId, int state);
    long createBatch(int driverId, int operatorRole, int scanAs);
    ScanBatch getBatch(long batchId);
    List<ScanBatch> getAllBatchesByDriver(int driverId);
    ScanBatch reviewBatch(long batchId, String status);
    ParcelScanResult scanParcel(String trackingNo, Long batchId, String deviceEventId);
    long recordDelivery(long orderId, int authenticatedDriverId, int deliveryResult, Integer failedReason,
                        String recipientName, double latitude, double longitude, String idempotencyKey);
    void recordPod(long attemptId, String podType, String objectUri, String sha256,
                   String contentType, long contentSize);
    void retryDelivery(long orderId, int authenticatedDriverId);

    class ScanBatch {
        private final long id;
        private final int driverId;
        private final int operatorRole;
        private final int scanAs;
        private int status;
        private final List<String> scannedTrackingNos;
        private final String scanTime;

        public ScanBatch(long id, int driverId, int operatorRole, int scanAs, int status,
                         List<String> scannedTrackingNos, String scanTime) {
            this.id = id;
            this.driverId = driverId;
            this.operatorRole = operatorRole;
            this.scanAs = scanAs;
            this.status = status;
            this.scannedTrackingNos = scannedTrackingNos;
            this.scanTime = scanTime;
        }

        public long getId() { return id; }
        public int getDriverId() { return driverId; }
        public int getOperatorRole() { return operatorRole; }
        public int getScanAs() { return scanAs; }
        public int getStatus() { return status; }
        public void setStatus(int status) { this.status = status; }
        public List<String> getScannedTrackingNos() { return scannedTrackingNos; }
        public String getScanTime() { return scanTime; }
    }

    record ParcelScanResult(DeliveringListData parcel, String errorCode, String errorMessage) {
        public boolean success() { return errorCode == null; }
    }
}

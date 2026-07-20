package com.hf.easydelivery.common.store;

import com.hf.easydelivery.common.dto.DeliveringListData;
import com.hf.easydelivery.common.dto.Dispatch_type;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;
import com.hf.easydelivery.common.repository.DriverRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

@Component
@Profile("memory")
public class MemoryDataStore implements DeliveryOperations {

    private final DriverRepository driverRepository;

    // Parcels
    private final List<DeliveringListData> parcels = new CopyOnWriteArrayList<>();

    // Scan Batches
    private final Map<Long, DeliveryOperations.ScanBatch> batches = new ConcurrentHashMap<>();
    private final AtomicLong batchIdGenerator = new AtomicLong(99991);

    public MemoryDataStore(DriverRepository driverRepository) {
        this.driverRepository = driverRepository;

        // Init Mock Parcels (Vancouver area coordinates)
        parcels.add(createMockParcel(10001, "SN10001", "BAUNI000300014438615", 12, 0, 0, "123 Main St, Vancouver, BC", "V6B 1A1", "49.2827", "-123.1207", "John Doe"));
        parcels.add(createMockParcel(10002, "SN10002", "BAUNI000300014438616", 12, 0, 0, "125 Main St, Vancouver, BC", "V6B 1A1", "49.2828", "-123.1208", "Alice Smith"));
        parcels.add(createMockParcel(10003, "SN10003", "BAUNI000300014438617", 12, 2, 1, "888 Kingsway, Vancouver, BC", "V5V 3C3", "49.2505", "-123.0760", "Bob Chen"));
        parcels.add(createMockParcel(10004, "SN10004", "BAUNI000300014438618", 12, 2, 1, "890 Kingsway, Vancouver, BC", "V5V 3C3", "49.2506", "-123.0761", "David Wong"));
        parcels.add(createMockParcel(10005, "SN10005", "BAUNI000300014438619", 12, 2, 1, "1055 W Georgia St, Vancouver, BC", "V6E 3P3", "49.2858", "-123.1244", "Emma Wilson"));
        parcels.add(createMockParcel(10006, "SN10006", "BAUNI000300014438620", 12, 0, 0, "2000 Simcoe St, Vancouver, BC", "V6E 3P4", "49.2860", "-123.1246", "George Martin"));
    }

    private DeliveringListData createMockParcel(long orderId, String orderSn, String trackingNo, int routeNo,
                                                int state, int scanStatus, String address, String zipcode,
                                                String lat, String lng, String name) {
        DeliveringListData data = new DeliveringListData();
        data.setOrder_id(orderId);
        data.setOrder_sn(orderSn);
        data.setTracking_no(trackingNo);
        data.setGoods_type(1);
        data.setExpress_type(1);
        data.setRoute_no(routeNo);
        data.setState(state); // 0 = Pending/Unscanned, 2 = Delivering, 3 = Delivered
        data.setScan_status(scanStatus); // 0 = Unscanned, 1 = Scanned
        data.setAddress(address);
        data.setZipcode(zipcode);
        data.setLat(lat);
        data.setLng(lng);
        data.setName(name);
        data.setMobile("604-555-0199");
        data.setWarehouse_id(1);
        data.setAssign_time(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        data.setDelivery_by("101");
        
        Dispatch_type dt = new Dispatch_type();
        dt.setSZ(1);
        dt.setSG(2);
        dt.setDT("Regular");
        dt.setSP(0);
        data.setDispatch_type(dt);
        
        return data;
    }

    // Auth helpers using BCrypt dynamic password hashes and status checks
    public boolean validateDriver(String credentialId, String password) {
        return driverRepository.findByCredentialId(credentialId)
                .map(driver -> driver.isActive() && org.springframework.security.crypto.bcrypt.BCrypt.checkpw(password, driver.getPasswordHash()))
                .orElse(false);
    }

    public Integer getDriverId(String credentialId) {
        return driverRepository.findByCredentialId(credentialId)
                .map(com.hf.easydelivery.common.model.Driver::getId)
                .orElse(null);
    }

    public String getDriverName(int driverId) {
        return driverRepository.findById(driverId)
                .map(com.hf.easydelivery.common.model.Driver::getName)
                .orElse("Driver");
    }

    // Parcel helpers
    public List<DeliveringListData> getUnscannedParcels(int driverId) {
        List<DeliveringListData> result = new ArrayList<>();
        for (DeliveringListData parcel : parcels) {
            if (parcel.getScan_status() == 0 && String.valueOf(driverId).equals(parcel.getDelivery_by())) {
                result.add(parcel);
            }
        }
        return result;
    }

    public List<DeliveringListData> getDeliveringParcels(int driverId) {
        List<DeliveringListData> result = new ArrayList<>();
        for (DeliveringListData parcel : parcels) {
            // state 2 is delivering, 3 is delivered (so we filter out delivered)
            if (parcel.getScan_status() == 1 && parcel.getState() == 2 && String.valueOf(driverId).equals(parcel.getDelivery_by())) {
                result.add(parcel);
            }
        }
        return result;
    }

    public DeliveringListData getParcelByTrackingNo(String trackingNo) {
        for (DeliveringListData parcel : parcels) {
            if (parcel.getTracking_no().equals(trackingNo)) {
                return parcel;
            }
        }
        return null;
    }

    public DeliveringListData getParcelByOrderId(long orderId) {
        for (DeliveringListData parcel : parcels) {
            if (parcel.getOrder_id() == orderId) {
                return parcel;
            }
        }
        return null;
    }

    public void updateParcelState(long orderId, int state) {
        DeliveringListData p = getParcelByOrderId(orderId);
        if (p != null) {
            p.setState(state);
        }
    }

    // Batch helpers
    public long createBatch(int driverId, int operatorRole, int scanAs) {
        long batchId = batchIdGenerator.getAndIncrement();
        ScanBatch batch = new ScanBatch(batchId, driverId, operatorRole, scanAs);
        batches.put(batchId, batch);
        return batchId;
    }

    public DeliveryOperations.ScanBatch getBatch(long batchId) {
        return batches.get(batchId);
    }

    public List<DeliveryOperations.ScanBatch> getAllBatchesByDriver(int driverId) {
        List<DeliveryOperations.ScanBatch> result = new ArrayList<>();
        for (DeliveryOperations.ScanBatch b : batches.values()) {
            if (b.getDriverId() == driverId) {
                result.add(b);
            }
        }
        return result;
    }

    public static class ScanBatch extends DeliveryOperations.ScanBatch {
        public ScanBatch(long id, int driverId, int operatorRole, int scanAs) {
            super(id, driverId, operatorRole, scanAs, 1, new CopyOnWriteArrayList<>(),
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }
    }

    @Override
    public ParcelScanResult scanParcel(String trackingNo, Long batchId, String deviceEventId) {
        DeliveringListData parcel = getParcelByTrackingNo(trackingNo);
        if (parcel == null) return new ParcelScanResult(null, "SCAN.NOT.FOUND", "Parcel not found: " + trackingNo);
        if (parcel.getScan_status() == 1) return new ParcelScanResult(null, "SCAN.ALREADY.SCANNED", "Parcel already scanned in another batch");
        parcel.setScan_status(1);
        parcel.setState(2);
        if (batchId != null) {
            DeliveryOperations.ScanBatch batch = batches.get(batchId);
            if (batch != null) batch.getScannedTrackingNos().add(trackingNo);
        }
        return new ParcelScanResult(parcel, null, null);
    }

    @Override
    public long recordDelivery(long orderId, int authenticatedDriverId, int deliveryResult, Integer failedReason,
                               String recipientName, double latitude, double longitude, String idempotencyKey) {
        updateParcelState(orderId, deliveryResult == 0 ? 3 : 0);
        return 0;
    }

    @Override
    public void recordPod(long attemptId, String podType, String objectUri, String sha256,
                          String contentType, long contentSize) {}

    @Override
    public void retryDelivery(long orderId, int authenticatedDriverId) {
        DeliveringListData parcel = getParcelByOrderId(orderId);
        if (parcel != null) {
            parcel.setState(2);
            parcel.setScan_status(1);
        }
    }

    @Override
    public DeliveryOperations.ScanBatch reviewBatch(long batchId, String status) {
        DeliveryOperations.ScanBatch batch = batches.get(batchId);
        if (batch != null && "APPROVED".equalsIgnoreCase(status)) batch.setStatus(2);
        return batch;
    }
}

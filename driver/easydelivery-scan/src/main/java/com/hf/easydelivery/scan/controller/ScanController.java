package com.hf.easydelivery.scan.controller;

import com.hf.easydelivery.common.dto.*;
import com.hf.easydelivery.common.response.AppResponse;
import com.hf.easydelivery.common.store.DeliveryOperations;
import com.hf.easydelivery.common.store.DeliveryOperations.ScanBatch;
import com.hf.easydelivery.scan.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import com.hf.easydelivery.common.exception.UnauthorizedException;

@RestController
@RequestMapping("/delivery")
public class ScanController {

    private static final Logger log = LoggerFactory.getLogger(ScanController.class);
    private final DeliveryOperations dataStore;

    public ScanController(DeliveryOperations dataStore) {
        this.dataStore = dataStore;
    }

    @PostMapping("/ext/scan")
    public AppResponse<ParcelScanData> scanParcel(@RequestBody ParcelScanReq req, HttpServletRequest request) {
        log.info("Scanning parcel: tracking_no={}, scan_batch_id={}", req.getTracking_no(), req.getScan_batch_id());
        DeliveryOperations.ScanBatch batch = dataStore.getBatch(req.getScan_batch_id() == null ? -1 : req.getScan_batch_id());
        if (batch == null) return AppResponse.fail("SCAN.BATCH.NOT.FOUND", "Scan batch not found");
        requireSelf(batch.getDriverId(), request);
        
        DeliveryOperations.ParcelScanResult result = dataStore.scanParcel(req.getTracking_no(), req.getScan_batch_id(), req.getDevice_event_id());
        if (!result.success()) return AppResponse.fail(result.errorCode(), result.errorMessage());
        DeliveringListData parcel = result.parcel();

        ParcelScanData data = new ParcelScanData();
        data.setOrderId(parcel.getOrder_id());
        data.setTrackingNo(parcel.getTracking_no());
        data.setRouteNo(parcel.getRoute_no());

        return AppResponse.success("Scan successful", data);
    }

    @PostMapping("/scan/batch")
    public AppResponse<ScanBatchCreateData> createScanBatch(@RequestBody ScanBatchCreateReq req, HttpServletRequest request) {
        requireSelf(req.getDriver_id(), request);
        log.info("Creating scan batch for driverId={}, role={}, scanAs={}",
                req.getDriver_id(), req.getOperator_role(), req.getScan_as());

        long batchId = dataStore.createBatch(req.getDriver_id(), req.getOperator_role(), req.getScan_as());
        
        ScanBatchCreateData data = new ScanBatchCreateData();
        data.setScan_batch_id(batchId);

        return AppResponse.success("Batch created", data);
    }

    @PostMapping("/scan/batch/report")
    public AppResponse<ScanBatchGenerateReportData> generateScanBatchReport(@RequestBody ScanBatchGenerateReportReq req,
                                                                            HttpServletRequest request) {
        log.info("Generating report for batchId={}", req.getScan_batch_id());

        ScanBatch batch = dataStore.getBatch(req.getScan_batch_id());
        if (batch == null) {
            return AppResponse.fail("REPORT.BATCH.NOT.FOUND", "Batch not found: " + req.getScan_batch_id());
        }
        requireSelf(batch.getDriverId(), request);

        // Retrieve driver parcels
        List<DeliveringListData> driverUnscanned = dataStore.getUnscannedParcels(batch.getDriverId());
        
        int scannedCount = batch.getScannedTrackingNos().size();
        int unscannedCount = driverUnscanned.size();
        int assignedCount = scannedCount + unscannedCount;

        ScanBatchGenerateReportData data = new ScanBatchGenerateReportData();
        data.setScan_time(batch.getScanTime());
        data.setAssigned_parcels_count(assignedCount);
        data.setScanned_parcels_count(scannedCount);
        data.setUnscanned_parcels_count(unscannedCount);
        
        List<ScanBatchGenerateReportData.ParcelInfo> unscannedInfoList = new ArrayList<>();
        for (DeliveringListData parcel : driverUnscanned) {
            unscannedInfoList.add(new ScanBatchGenerateReportData.ParcelInfo(parcel.getTracking_no(), parcel.getRoute_no()));
        }
        data.setUnscanned_parcels(unscannedInfoList);
        data.setReturned_parcels_count(0);
        data.setReturned_parcels(new ArrayList<>());

        return AppResponse.success(data);
    }

    @GetMapping("/ext/scan/batch/reports")
    public AppResponse<List<ScanBatchReportData>> fetchDriverReports(
            @RequestParam("warehouse") Integer warehouse,
            @RequestParam("driver_id") Integer driverId,
            @RequestParam("start_date") String startDate,
            HttpServletRequest request) {
        requireSelf(driverId, request);
        
        log.info("Fetching reports for warehouse={}, driverId={}, startDate={}", warehouse, driverId, startDate);
        List<ScanBatch> driverBatches = dataStore.getAllBatchesByDriver(driverId);

        List<ScanBatchReportData> list = new ArrayList<>();
        for (ScanBatch batch : driverBatches) {
            // Check if batch date matches the prefix of scanTime
            if (batch.getScanTime().startsWith(startDate)) {
                ScanBatchReportData data = new ScanBatchReportData();
                data.setScan_batch_id(batch.getId());
                data.setName("Batch_" + batch.getId());
                data.setDispatch_nos("DISP-" + batch.getId());
                data.setDriver_id(batch.getDriverId());
                
                List<DeliveringListData> unscanned = dataStore.getUnscannedParcels(batch.getDriverId());
                data.setUnscanned_parcels(unscanned.size());
                data.setScanned_parcels(batch.getScannedTrackingNos().size());
                data.setReturned_parcels(0);
                data.setTotal_return_parcels(0);
                data.setScan_time(batch.getScanTime());
                data.setScan_batch_status(batch.getStatus());
                
                list.add(data);
            }
        }

        return AppResponse.success(list);
    }

    @PutMapping("/ext/scan/batch/{scanBatchId}")
    public AppResponse<ScanBatchReviewData> submitScanBatchReview(
            @PathVariable("scanBatchId") Long scanBatchId,
            @RequestBody ScanBatchReviewReq req,
            HttpServletRequest request) {
        log.info("Reviewing batchId={}, newStatus={}", scanBatchId, req.getStatus());

        ScanBatch batch = dataStore.getBatch(scanBatchId);
        if (batch == null) {
            return AppResponse.fail("REVIEW.BATCH.NOT.FOUND", "Batch not found: " + scanBatchId);
        }

        requireSelf(batch.getDriverId(), request);
        if (!"SUBMITTED".equalsIgnoreCase(req.getStatus())) {
            throw new UnauthorizedException("Driver may only submit a load session for operations review");
        }

        batch = dataStore.reviewBatch(scanBatchId, req.getStatus());

        return AppResponse.success(new ScanBatchReviewData(req.getStatus()));
    }

    @GetMapping("/to-be-picked-up/brief/{driverId}")
    public AppResponse<ToBePickedUpBriefData> fetchToBePickedUpBrief(
            @PathVariable("driverId") Integer driverId,
            HttpServletRequest request) {
        requireSelf(driverId, request);
        log.info("Fetching sorting brief for driverId={}", driverId);

        List<DeliveringListData> unscanned = dataStore.getUnscannedParcels(driverId);
        List<ScanBatch> driverBatches = dataStore.getAllBatchesByDriver(driverId);

        // Find active open batch if any
        long activeBatchId = 0;
        int activeBatchStatus = 0;
        int scannedCount = 0;
        if (!driverBatches.isEmpty()) {
            ScanBatch lastBatch = driverBatches.get(driverBatches.size() - 1);
            activeBatchId = lastBatch.getId();
            activeBatchStatus = lastBatch.getStatus();
            scannedCount = lastBatch.getScannedTrackingNos().size();
        }

        ToBePickedUpBriefData data = new ToBePickedUpBriefData();
        data.setTotal_number(unscanned.size() + scannedCount);
        data.setAddress("Sector 12B - Warehouse " + 1);
        data.setScan_batch_id(activeBatchId);
        data.setScan_batch_status(activeBatchStatus);
        data.setScanned_item_quantity(scannedCount);

        return AppResponse.success(data);
    }

    private void requireSelf(Integer requestedDriverId, HttpServletRequest request) {
        Object authenticated = request.getAttribute("driverId");
        if (!(authenticated instanceof Integer id) || !id.equals(requestedDriverId)) {
            throw new UnauthorizedException("Driver cannot access another driver's data");
        }
    }
}
